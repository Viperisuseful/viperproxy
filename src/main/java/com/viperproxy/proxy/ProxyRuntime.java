package com.viperproxy.proxy;

import com.viperproxy.ProxyRuntimeHolder;
import com.viperproxy.config.ProxyConfig;
import com.viperproxy.config.ProxyConfigStore;
import com.viperproxy.config.ProxyProfile;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.ProxyHandler;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.NetworkSide;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProxyRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("ViperProxy");

    private static final long CONNECTIVITY_TEST_TIMEOUT_SECONDS = 20L;

    private static final String NETTY_PROXY_HANDLER = "viperproxy_proxy";
    private static final String NETTY_KILLSWITCH_HANDLER = "viperproxy_killswitch";

    private static final Set<String> AUTH_BYPASS_HOSTS = Set.of(
        "authserver.mojang.com",
        "sessionserver.mojang.com",
        "api.mojang.com",
        "api.minecraftservices.com",
        "multiplayer.minecraft.net",
        "realms.minecraft.net",
        "irisshaders.net",
        "modrinth.com",
        "textures.minecraft.net",
        "xsts.auth.xboxlive.com",
        "user.auth.xboxlive.com",
        "login.live.com"
    );

    private final ExecutorService testerExecutor = Executors.newSingleThreadExecutor(new ProxyThreadFactory());
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
        new ProxyHeartbeatThreadFactory()
    );

    private volatile List<ProxyProfile> profiles = List.of(new ProxyProfile("Default", new ProxyConfig()));
    private volatile int activeProfileIndex;

    private volatile ProxyConfig activeConfig = new ProxyConfig();
    private volatile ProxyStatus status = ProxyStatus.DISABLED;
    private volatile StatusDetail statusDetail = StatusDetail.of(FailureReason.DISABLED, "Proxy not configured");
    private volatile String externalIp = "Unknown";
    private volatile long latencyMs = -1L;

    private volatile ProxyConfigStore configStore;
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile long configGeneration;
    private volatile boolean initialized;
    private volatile FutureTask<ProxyTester.TestResult> activeConnectivityTask;
    private volatile Thread activeConnectivityWorker;
    private volatile boolean shutdownComplete;

    public ProxyRuntime() {
    }

    @Deprecated
    public static ProxyRuntime getInstance() {
        return ProxyRuntimeHolder.getRequiredRuntime();
    }

    public synchronized void initialize() {
        if (this.initialized) {
            return;
        }

        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("viperproxy.json");
        this.configStore = new ProxyConfigStore(configPath);

        ProxyConfigStore.LoadedProfiles loadedProfiles = this.configStore.loadProfiles();
        List<ProxyProfile> loaded = copyProfiles(loadedProfiles.profiles());
        if (loaded.isEmpty()) {
            loaded.add(new ProxyProfile("Default", new ProxyConfig()));
        }

        this.profiles = loaded;
        this.activeProfileIndex = sanitizeProfileIndex(loadedProfiles.activeProfileIndex(), this.profiles.size());
        this.activeConfig = this.profiles.get(this.activeProfileIndex).getConfigCopy().normalized();

        applyCurrentConfigState(this.activeConfig);
        this.initialized = true;
    }

    public synchronized void shutdown() {
        if (this.shutdownComplete) {
            return;
        }

        this.shutdownComplete = true;
        this.configGeneration++;

        this.heartbeatExecutor.shutdownNow();
        this.testerExecutor.shutdownNow();

        FutureTask<ProxyTester.TestResult> connectivityTask = this.activeConnectivityTask;
        if (connectivityTask != null) {
            connectivityTask.cancel(true);
            this.activeConnectivityTask = null;
        }

        Thread connectivityWorker = this.activeConnectivityWorker;
        if (connectivityWorker != null) {
            connectivityWorker.interrupt();
            this.activeConnectivityWorker = null;
        }

        this.heartbeatTask = null;

        ProxySelector.setDefault(ProxySelector.of(null));
        clearProxyProperties();

        Authenticator.setDefault(null);

        this.status = ProxyStatus.DISABLED;
        this.statusDetail = StatusDetail.of(FailureReason.DISABLED, "Proxy shut down");
        this.externalIp = "Unknown";
        this.latencyMs = -1L;

        LOGGER.info("ViperProxy shut down cleanly");
    }

    public synchronized void applyFromUi(ProxyConfig config) {
        applyFromUi(config, getActiveProfileName());
    }

    public synchronized void applyFromUi(ProxyConfig config, String profileName) {
        ProxyConfig normalized = config == null ? new ProxyConfig() : config.normalized();
        String resolvedProfileName = normalizeProfileName(profileName, this.activeProfileIndex + 1);

        LOGGER.info(
            "Applying proxy configuration from UI: profile='{}', type={}, host='{}', port={}",
            resolvedProfileName,
            normalized.type,
            normalized.host,
            normalized.port
        );

        ensureProfilesExist();

        ProxyProfile active = this.profiles.get(this.activeProfileIndex).copy();
        active.setName(resolvedProfileName);
        active.setConfig(normalized);

        List<ProxyProfile> updatedProfiles = copyProfiles(this.profiles);
        updatedProfiles.set(this.activeProfileIndex, active);
        this.profiles = updatedProfiles;
        this.activeConfig = normalized;

        persistProfiles();
        applyCurrentConfigState(normalized);
    }

    public synchronized void createProfileFromUi(String profileName, ProxyConfig config) {
        ProxyConfig normalized = config == null ? new ProxyConfig() : config.normalized();

        List<ProxyProfile> updatedProfiles = copyProfiles(this.profiles);
        updatedProfiles.add(new ProxyProfile(normalizeProfileName(profileName, updatedProfiles.size() + 1), normalized));

        this.profiles = updatedProfiles;
        this.activeProfileIndex = this.profiles.size() - 1;
        this.activeConfig = normalized;

        persistProfiles();
        applyCurrentConfigState(normalized);
    }

    public synchronized void cycleActiveProfile() {
        ensureProfilesExist();
        int nextIndex = (this.activeProfileIndex + 1) % this.profiles.size();
        selectActiveProfile(nextIndex);
    }

    public synchronized void selectActiveProfile(int profileIndex) {
        ensureProfilesExist();
        this.activeProfileIndex = sanitizeProfileIndex(profileIndex, this.profiles.size());
        this.activeConfig = this.profiles.get(this.activeProfileIndex).getConfigCopy().normalized();

        persistProfiles();
        applyCurrentConfigState(this.activeConfig);
    }

    public synchronized void deleteProfile(int profileIndex) {
        ensureProfilesExist();
        if (this.profiles.size() <= 1) {
            return;
        }

        if (profileIndex < 0 || profileIndex >= this.profiles.size()) {
            return;
        }

        List<ProxyProfile> updatedProfiles = copyProfiles(this.profiles);
        updatedProfiles.remove(profileIndex);
        this.profiles = updatedProfiles;

        if (this.activeProfileIndex >= this.profiles.size()) {
            this.activeProfileIndex = this.profiles.size() - 1;
        } else if (profileIndex < this.activeProfileIndex) {
            this.activeProfileIndex--;
        } else if (profileIndex == this.activeProfileIndex) {
            this.activeProfileIndex = sanitizeProfileIndex(this.activeProfileIndex, this.profiles.size());
        }

        this.activeConfig = this.profiles.get(this.activeProfileIndex).getConfigCopy().normalized();
        persistProfiles();
        applyCurrentConfigState(this.activeConfig);
    }

    public synchronized List<String> getProfileNames() {
        ensureProfilesExist();
        List<String> names = new ArrayList<>();
        for (ProxyProfile profile : this.profiles) {
            names.add(profile.getName());
        }
        return List.copyOf(names);
    }

    public synchronized String getActiveProfileName() {
        ensureProfilesExist();
        return this.profiles.get(this.activeProfileIndex).getName();
    }

    public synchronized int getActiveProfileIndex() {
        return this.activeProfileIndex;
    }

    public synchronized int getProfileCount() {
        return this.profiles.size();
    }

    public synchronized ProxyConfig getActiveConfigCopy() {
        return this.activeConfig.copy();
    }

    public ProxyStatus getStatus() {
        return this.status;
    }

    public StatusDetail getStatusDetailInfo() {
        return this.statusDetail;
    }

    public String getStatusDetail() {
        return this.statusDetail.summarize();
    }

    public String getExternalIp() {
        return this.externalIp;
    }

    public long getLatencyMs() {
        return this.latencyMs;
    }

    public Text getMultiplayerButtonText() {
        return switch (this.status) {
            case CONNECTED -> Text.literal("Viper Proxy: Connected").formatted(Formatting.GREEN);
            case CONNECTING -> Text.literal("Viper Proxy: Connecting...").formatted(Formatting.YELLOW);
            case DISABLED -> Text.literal("Viper Proxy: Disabled").formatted(Formatting.GRAY);
            case ERROR -> Text.literal("Viper Proxy: ERROR").formatted(Formatting.RED);
        };
    }

    public boolean isReadyForMultiplayerConnection() {
        return this.status == ProxyStatus.CONNECTED;
    }

    public boolean isProxyRequired() {
        return true;
    }

    public String getKillSwitchReason() {
        StatusDetail detail = this.statusDetail;
        if (detail.reason() == FailureReason.NONE) {
            return "Viper Proxy kill-switch blocked connection: " + detail.summarize();
        }

        return "Viper Proxy kill-switch blocked connection ["
            + detail.reason().code()
            + "]: "
            + detail.summarize();
    }

    public void injectProxyHandlers(ChannelPipeline pipeline, NetworkSide side, boolean local) {
        if (local || side != NetworkSide.CLIENTBOUND) {
            return;
        }

        ProxyConfig cfg = this.activeConfig.normalized();

        if (!cfg.isUsable()) {
            throw new IllegalStateException("Proxy configuration is missing or invalid.");
        }

        if (this.status != ProxyStatus.CONNECTED) {
            throw new IllegalStateException("Proxy is not connected.");
        }

        synchronized (pipeline) {
            if (pipeline.context(NETTY_PROXY_HANDLER) != null) {
                return;
            }

            try {
                if (pipeline.context(NETTY_KILLSWITCH_HANDLER) == null) {
                    pipeline.addFirst(NETTY_KILLSWITCH_HANDLER, new ProxyKillSwitchHandler(this));
                }

                InetSocketAddress resolvedProxyAddress = resolveProxyAddress(cfg.host, cfg.port);
                ProxyHandler handler = cfg.type.toNettyProxyHandler(resolvedProxyAddress, cfg.username, cfg.password);
                pipeline.addFirst(NETTY_PROXY_HANDLER, handler);
            } catch (RuntimeException insertionError) {
                if (pipeline.context(NETTY_PROXY_HANDLER) == null) {
                    String detail = "Proxy handler injection failed: " + simplifyError(insertionError);
                    markRuntimeError(mapFailure(insertionError), detail);
                    throw new IllegalStateException(detail, insertionError);
                }
            }
        }

        if (this.status != ProxyStatus.CONNECTED) {
            removeHandlerIfPresent(pipeline, NETTY_PROXY_HANDLER);
            removeHandlerIfPresent(pipeline, NETTY_KILLSWITCH_HANDLER);
            throw new IllegalStateException("Proxy state changed before connection pipeline became active.");
        }
    }

    public synchronized void markProxyTunnelEstablished() {
        if (this.status == ProxyStatus.CONNECTING) {
            this.status = ProxyStatus.CONNECTED;
            this.statusDetail = StatusDetail.of(FailureReason.NONE, "Proxy tunnel established");
        }
    }

    public void markRuntimeError(String reason) {
        markRuntimeError(FailureReason.UNKNOWN, reason);
    }

    public synchronized void markRuntimeError(FailureReason reason, String reasonText) {
        this.configGeneration++;
        setErrorLocked(reason, reasonText);
    }

    private void applyCurrentConfigState(ProxyConfig config) {
        ProxyConfig normalized = config == null ? new ProxyConfig() : config.normalized();
        this.activeConfig = normalized;
        this.configGeneration++;

        installGlobalRouting();

        if (!normalized.enabled) {
            cancelHeartbeatLocked();
            this.status = ProxyStatus.DISABLED;
            this.statusDetail = StatusDetail.of(FailureReason.DISABLED, "Proxy is disabled");
            this.externalIp = "Unknown";
            this.latencyMs = -1L;
            return;
        }

        if (!normalized.isUsable()) {
            setErrorLocked(FailureReason.INVALID_CONFIG, "Host/port is invalid");
            return;
        }

        startConnectivityTest(normalized.copy(), this.configGeneration);
    }

    private void startConnectivityTest(ProxyConfig expectedConfig, long generation) {
        this.status = ProxyStatus.CONNECTING;
        this.statusDetail = StatusDetail.of(FailureReason.NONE, "Testing proxy connectivity...");
        this.externalIp = "Unknown";
        this.latencyMs = -1L;
        cancelHeartbeatLocked();

        this.testerExecutor.submit(() -> {
            try {
                FutureTask<ProxyTester.TestResult> testTask = new FutureTask<>(() -> ProxyTester.testConnectivity(expectedConfig));
                this.activeConnectivityTask = testTask;

                Thread worker = new Thread(testTask, "ViperProxy-Connectivity-Test-Worker");
                worker.setDaemon(true);
                this.activeConnectivityWorker = worker;
                worker.start();

                try {
                    ProxyTester.TestResult result = testTask.get(CONNECTIVITY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    LOGGER.info(
                        "Proxy connectivity test succeeded: externalIp='{}', latencyMs={}",
                        result.externalIp(),
                        result.latencyMs()
                    );

                    synchronized (this) {
                        if (generation != this.configGeneration) {
                            return;
                        }

                        if (!result.minecraftReachable()) {
                            setErrorLocked(FailureReason.MINECRAFT_UNREACHABLE, "Proxy connected, but Minecraft services are unreachable");
                            return;
                        }

                        this.status = ProxyStatus.CONNECTED;
                        this.externalIp = result.externalIp();
                        this.latencyMs = result.latencyMs();
                        this.statusDetail = StatusDetail.of(
                            FailureReason.NONE,
                            "Connected via " + expectedConfig.type.label() + " (" + result.latencyMs() + " ms)"
                        );

                        startHeartbeatLocked(expectedConfig.copy(), generation);
                    }
                } catch (TimeoutException timeoutError) {
                    testTask.cancel(true);

                    LOGGER.warn(
                        "Connectivity test timed out after 20s for host='{}' port={}",
                        expectedConfig.host,
                        expectedConfig.port
                    );
                    LOGGER.warn("Proxy connectivity test timed out after 20s", timeoutError);

                    synchronized (this) {
                        if (generation != this.configGeneration) {
                            return;
                        }

                        setErrorLocked(FailureReason.TIMEOUT, "Connectivity test timed out after 20s");
                    }
                } catch (InterruptedException interruptedError) {
                    testTask.cancel(true);
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Connectivity test interrupted: {}", simplifyError(interruptedError), interruptedError);

                    synchronized (this) {
                        if (generation != this.configGeneration) {
                            return;
                        }

                        setErrorLocked(FailureReason.TIMEOUT, "Connectivity test interrupted");
                    }
                } catch (ExecutionException executionError) {
                    Throwable cause = executionError.getCause() == null ? executionError : executionError.getCause();
                    LOGGER.warn("Connectivity test threw: {}", simplifyError(cause), cause);

                    synchronized (this) {
                        if (generation != this.configGeneration) {
                            return;
                        }

                        setErrorLocked(mapFailure(cause), simplifyError(cause));
                    }
                } finally {
                    testTask.cancel(true);
                    if (this.activeConnectivityTask == testTask) {
                        this.activeConnectivityTask = null;
                    }
                    if (this.activeConnectivityWorker == worker) {
                        this.activeConnectivityWorker = null;
                    }
                }
            } catch (Throwable throwable) {
                LOGGER.warn("Connectivity test threw: {}", simplifyError(throwable), throwable);

                synchronized (this) {
                    if (generation != this.configGeneration) {
                        return;
                    }

                    setErrorLocked(FailureReason.UNKNOWN, simplifyError(throwable));
                }
            }
        });
    }

    private void startHeartbeatLocked(ProxyConfig expectedConfig, long generation) {
        cancelHeartbeatLocked();
        this.heartbeatTask = this.heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                ProxyTester.heartbeat(expectedConfig);
            } catch (Exception heartbeatError) {
                synchronized (this) {
                    if (generation != this.configGeneration || this.status != ProxyStatus.CONNECTED) {
                        return;
                    }

                    setErrorLocked(
                        FailureReason.PROXY_DROPPED,
                        "Proxy heartbeat failed: " + simplifyError(heartbeatError)
                    );
                }
            }
        }, 15L, 15L, TimeUnit.SECONDS);
    }

    private void cancelHeartbeatLocked() {
        ScheduledFuture<?> task = this.heartbeatTask;
        if (task != null) {
            task.cancel(true);
            this.heartbeatTask = null;
        }
    }

    private void setErrorLocked(FailureReason reason, String message) {
        this.status = ProxyStatus.ERROR;
        this.statusDetail = StatusDetail.of(reason == null ? FailureReason.UNKNOWN : reason, message);
        this.externalIp = "Unknown";
        this.latencyMs = -1L;
        cancelHeartbeatLocked();
    }

    private synchronized void installGlobalRouting() {
        if (!(ProxySelector.getDefault() instanceof EnforcedProxySelector)) {
            ProxySelector.setDefault(new EnforcedProxySelector());
        }

        if (!(Authenticator.getDefault() instanceof EnforcedProxyAuthenticator)) {
            Authenticator.setDefault(new EnforcedProxyAuthenticator());
        }

        clearProxyProperties();

        ProxyConfig cfg = this.activeConfig.normalized();
        if (!cfg.isUsable()) {
            return;
        }

        switch (cfg.type) {
            case SOCKS5 -> {
                System.setProperty("socksProxyHost", cfg.host);
                System.setProperty("socksProxyPort", Integer.toString(cfg.port));
                System.setProperty("socksProxyVersion", "5");
                if (cfg.hasCredentials()) {
                    System.setProperty("java.net.socks.username", cfg.username);
                    System.setProperty("java.net.socks.password", cfg.password);
                }
            }
            case HTTP, HTTPS -> {
                System.setProperty("http.proxyHost", cfg.host);
                System.setProperty("http.proxyPort", Integer.toString(cfg.port));
                System.setProperty("https.proxyHost", cfg.host);
                System.setProperty("https.proxyPort", Integer.toString(cfg.port));
            }
        }
    }

    private void persistProfiles() {
        if (this.configStore == null) {
            return;
        }

        this.configStore.saveProfiles(copyProfiles(this.profiles), this.activeProfileIndex);
    }

    private void ensureProfilesExist() {
        if (this.profiles == null || this.profiles.isEmpty()) {
            this.profiles = List.of(new ProxyProfile("Default", new ProxyConfig()));
            this.activeProfileIndex = 0;
            this.activeConfig = this.profiles.get(0).getConfigCopy().normalized();
        }
    }

    private static int sanitizeProfileIndex(int profileIndex, int size) {
        if (size <= 0) {
            return 0;
        }

        if (profileIndex < 0 || profileIndex >= size) {
            return 0;
        }

        return profileIndex;
    }

    private static List<ProxyProfile> copyProfiles(List<ProxyProfile> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }

        List<ProxyProfile> copied = new ArrayList<>();
        for (ProxyProfile profile : source) {
            if (profile != null) {
                copied.add(profile.copy());
            }
        }
        return copied;
    }

    private static String normalizeProfileName(String requestedName, int fallbackIndex) {
        if (requestedName == null || requestedName.isBlank()) {
            return "Profile " + fallbackIndex;
        }

        return requestedName.trim();
    }

    private static String simplifyError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }

        return message;
    }

    private static FailureReason mapFailure(Throwable error) {
        String text = simplifyError(error).toLowerCase(Locale.ROOT);
        if (text.contains("timed out") || text.contains("timeout")) {
            return FailureReason.TIMEOUT;
        }

        if (text.contains("407") || text.contains("auth")) {
            return FailureReason.AUTH_FAILED;
        }

        if (text.contains("refused")) {
            return FailureReason.CONNECTION_REFUSED;
        }

        if (text.contains("unknownhost") || text.contains("unresolved") || text.contains("dns")) {
            return FailureReason.DNS_FAILURE;
        }

        if (text.contains("minecraft")) {
            return FailureReason.MINECRAFT_UNREACHABLE;
        }

        return FailureReason.UNKNOWN;
    }

    private static boolean shouldBypassHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }

        String normalized = host.toLowerCase(Locale.ROOT);
        if (isLocalHost(normalized) || isPrivateIpLiteral(normalized)) {
            return true;
        }

        for (String bypassHost : AUTH_BYPASS_HOSTS) {
            if (normalized.equals(bypassHost) || normalized.endsWith("." + bypassHost)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isLocalHost(String host) {
        return "localhost".equals(host)
            || host.endsWith(".local")
            || "127.0.0.1".equals(host)
            || "::1".equals(host)
            || "[::1]".equals(host);
    }

    private static boolean isPrivateIpLiteral(String host) {
        String[] pieces = host.split("\\.");
        if (pieces.length != 4) {
            return false;
        }

        try {
            int p0 = Integer.parseInt(pieces[0]);
            int p1 = Integer.parseInt(pieces[1]);
            int p2 = Integer.parseInt(pieces[2]);
            int p3 = Integer.parseInt(pieces[3]);

            if (p0 < 0 || p0 > 255 || p1 < 0 || p1 > 255 || p2 < 0 || p2 > 255 || p3 < 0 || p3 > 255) {
                return false;
            }

            if (p0 == 10 || p0 == 127 || p0 == 0) {
                return true;
            }

            if (p0 == 172 && p1 >= 16 && p1 <= 31) {
                return true;
            }

            return p0 == 192 && p1 == 168;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void clearProxyProperties() {
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        System.clearProperty("socksProxyVersion");
        System.clearProperty("java.net.socks.username");
        System.clearProperty("java.net.socks.password");

        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
    }

    private static InetSocketAddress resolveProxyAddress(String host, int port) {
        try {
            return new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (Exception resolveError) {
            throw new IllegalStateException(
                "Unable to resolve proxy host '" + host + "': " + simplifyError(resolveError),
                resolveError
            );
        }
    }

    private static void removeHandlerIfPresent(ChannelPipeline pipeline, String name) {
        if (pipeline.context(name) != null) {
            pipeline.remove(name);
        }
    }

    private final class EnforcedProxySelector extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            ProxyConfig cfg = activeConfig.normalized();
            if (!cfg.isUsable() || uri == null) {
                return List.of(Proxy.NO_PROXY);
            }

            String host = uri.getHost();
            if (shouldBypassHost(host)) {
                return List.of(Proxy.NO_PROXY);
            }

            return List.of(cfg.type.toJavaProxy(cfg.socketAddress()));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
            markRuntimeError(FailureReason.CONNECTION_REFUSED, "Proxy selector connect failed: " + simplifyError(ioe));
        }
    }

    private final class EnforcedProxyAuthenticator extends Authenticator {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            ProxyConfig cfg = activeConfig.normalized();
            if (!cfg.isUsable() || !cfg.hasCredentials()) {
                return null;
            }

            if (getRequestorType() != RequestorType.PROXY) {
                return null;
            }

            return new PasswordAuthentication(cfg.username, cfg.password.toCharArray());
        }
    }

    private static final class ProxyThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ViperProxy-Connectivity-Tester");
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class ProxyHeartbeatThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ViperProxy-Heartbeat");
            thread.setDaemon(true);
            return thread;
        }
    }
}
