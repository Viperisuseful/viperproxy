package com.viperproxy.proxy;

import com.viperproxy.config.ProxyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProxyTester {
    private static final Logger LOGGER = LoggerFactory.getLogger("ViperProxy");

    private static final String IP_CHECK_URL = "https://api.ipify.org";
    private static final String IP_CHECK_HOST = "api.ipify.org";
    private static final int IP_CHECK_PORT = 80;
    private static final int SOCKS_STAGE_TIMEOUT_MS = 8000;
    private static final String MINECRAFT_REACHABILITY_HOST = "sessionserver.mojang.com";
    private static final int MINECRAFT_REACHABILITY_PORT = 443;

    private ProxyTester() {
    }

    public static TestResult testConnectivity(ProxyConfig config) throws IOException {
        ProxyConfig normalized = config == null ? new ProxyConfig() : config.normalized();

        LOGGER.info(
            "Starting proxy connectivity test: type={}, host='{}', port={}",
            normalized.type,
            normalized.host,
            normalized.port
        );

        try {
            long start = System.nanoTime();

            String ip = normalized.type == ProxyType.SOCKS5
                ? fetchExternalIpViaSocks5Netty(normalized)
                : fetchExternalIpViaHttpConnection(normalized, SOCKS_STAGE_TIMEOUT_MS);

            boolean minecraftReachable = testMinecraftReachability(normalized, SOCKS_STAGE_TIMEOUT_MS, true);
            long latency = (System.nanoTime() - start) / 1_000_000L;

            LOGGER.info(
                "Proxy connectivity test result: type={}, host='{}', port={}, externalIp='{}', latencyMs={}, minecraftReachable={}",
                normalized.type,
                normalized.host,
                normalized.port,
                ip.trim(),
                latency,
                minecraftReachable
            );

            LOGGER.info("Proxy connectivity test succeeded: externalIp='{}', latencyMs={}", ip.trim(), latency);

            return new TestResult(ip.trim(), latency, minecraftReachable);
        } catch (Throwable error) {
            LOGGER.warn(
                "Proxy connectivity test failed: type={}, host='{}', port={}, message='{}', cause='{}'",
                normalized.type,
                normalized.host,
                normalized.port,
                safeMessage(error),
                causeText(error),
                error
            );

            if (error instanceof IOException ioException) {
                throw ioException;
            }

            throw new IOException("Proxy connectivity test crashed: " + safeMessage(error), error);
        }
    }

    public static void heartbeat(ProxyConfig config) throws IOException {
        ProxyConfig normalized = config == null ? new ProxyConfig() : config.normalized();
        if (!testMinecraftReachability(normalized, 4500, false)) {
            throw new IOException("Heartbeat probe failed to reach Minecraft services.");
        }
    }

    private static String fetchExternalIpViaHttpConnection(ProxyConfig config, int timeoutMs) throws IOException {
        Proxy javaProxy = config.type.toJavaProxy(config.socketAddress());
        URL endpoint = URI.create(IP_CHECK_URL).toURL();

        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection(javaProxy);
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "ViperProxy/1.0");

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("Proxy test failed with HTTP " + statusCode);
            }

            String ip;
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
            )) {
                ip = reader.readLine();
            }

            if (ip == null || ip.isBlank()) {
                throw new IOException("Proxy test did not return an external IP.");
            }

            return ip.trim();
        } finally {
            connection.disconnect();
        }
    }

    @SuppressWarnings("deprecation")
    private static String fetchExternalIpViaSocks5Netty(ProxyConfig config) throws IOException {
        Socks5ProxyHandler socksHandler = createSocks5Handler(config);
        socksHandler.setConnectTimeoutMillis(SOCKS_STAGE_TIMEOUT_MS);

        NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);
        AtomicReference<String> ipResult = new AtomicReference<>();
        AtomicReference<Throwable> pipelineError = new AtomicReference<>();
        CountDownLatch responseLatch = new CountDownLatch(1);
        Channel channel = null;

        try {
            Bootstrap bootstrap = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, SOCKS_STAGE_TIMEOUT_MS)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast("viperproxy_socks5", socksHandler);
                        ch.pipeline().addLast("viperproxy_http_codec", new HttpClientCodec());
                        ch.pipeline().addLast("viperproxy_http_agg", new HttpObjectAggregator(16 * 1024));
                        ch.pipeline().addLast("viperproxy_ip_response", new SimpleChannelInboundHandler<FullHttpResponse>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext context, FullHttpResponse msg) {
                                try {
                                    int statusCode = msg.status().code();
                                    if (statusCode < 200 || statusCode >= 300) {
                                        pipelineError.compareAndSet(null, new IOException("SOCKS5 IP check failed with HTTP " + statusCode));
                                        return;
                                    }

                                    String body = msg.content().toString(StandardCharsets.UTF_8).trim();
                                    if (body.isBlank()) {
                                        pipelineError.compareAndSet(null, new IOException("SOCKS5 IP check returned an empty body."));
                                        return;
                                    }

                                    ipResult.set(body);
                                } finally {
                                    responseLatch.countDown();
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                                pipelineError.compareAndSet(null, cause);
                                responseLatch.countDown();
                                context.close();
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext context) {
                                if (ipResult.get() == null && pipelineError.get() == null) {
                                    pipelineError.compareAndSet(null, new IOException("SOCKS5 IP check connection closed before response."));
                                }
                                responseLatch.countDown();
                            }
                        });
                    }
                });

            LOGGER.info("SOCKS5 IP test connecting: destination={}:{}, timeoutMs={}", IP_CHECK_HOST, IP_CHECK_PORT, SOCKS_STAGE_TIMEOUT_MS);

            ChannelFuture connectFuture = bootstrap.connect(InetSocketAddress.createUnresolved(IP_CHECK_HOST, IP_CHECK_PORT));
            if (!connectFuture.await(SOCKS_STAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IOException("Timed out connecting to ipify through SOCKS5 proxy.");
            }

            if (!connectFuture.isSuccess()) {
                throw new IOException("Failed to connect to ipify through SOCKS5 proxy.", connectFuture.cause());
            }

            channel = connectFuture.channel();

            io.netty.util.concurrent.Future<Channel> tunnelFuture = socksHandler.connectFuture();
            boolean tunnelEstablished = tunnelFuture.await(SOCKS_STAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!tunnelEstablished || !tunnelFuture.isSuccess()) {
                throw new IOException(
                    "SOCKS5 tunnel did not establish within timeout: " + safeMessage(tunnelFuture.cause()),
                    tunnelFuture.cause()
                );
            }

            LOGGER.info("SOCKS5 tunnel established for IP test: destination={}:{}, timeoutMs={}", IP_CHECK_HOST, IP_CHECK_PORT, SOCKS_STAGE_TIMEOUT_MS);

            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            request.headers().set(HttpHeaderNames.HOST, IP_CHECK_HOST);
            request.headers().set(HttpHeaderNames.USER_AGENT, "ViperProxy/1.0");
            request.headers().set(HttpHeaderNames.ACCEPT, "text/plain");
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

            ChannelFuture writeFuture = channel.writeAndFlush(request);
            if (!writeFuture.await(SOCKS_STAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IOException("Timed out writing SOCKS5 IP check request.");
            }

            if (!writeFuture.isSuccess()) {
                throw new IOException("Failed to send SOCKS5 IP check request.", writeFuture.cause());
            }

            if (!responseLatch.await(SOCKS_STAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IOException("Timed out waiting for SOCKS5 IP check response.");
            }

            Throwable error = pipelineError.get();
            if (error != null) {
                throw new IOException("SOCKS5 IP check failed: " + safeMessage(error), error);
            }

            String ip = ipResult.get();
            if (ip == null || ip.isBlank()) {
                throw new IOException("SOCKS5 IP check did not return an external IP.");
            }

            return ip;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while performing SOCKS5 connectivity test.", interrupted);
        } finally {
            if (channel != null) {
                channel.close().awaitUninterruptibly();
            }
            eventLoop.shutdownGracefully(0L, 500L, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
    }

    private static boolean testMinecraftReachability(ProxyConfig config, int timeoutMs, boolean logSuccess) {
        LOGGER.info(
            "Starting Minecraft TCP reachability probe: target={}:{}, timeoutMs={}",
            MINECRAFT_REACHABILITY_HOST,
            MINECRAFT_REACHABILITY_PORT,
            timeoutMs
        );

        if (config.type == ProxyType.SOCKS5) {
            try {
                verifySocks5Tunnel(config, MINECRAFT_REACHABILITY_HOST, MINECRAFT_REACHABILITY_PORT, timeoutMs);
                if (logSuccess) {
                    LOGGER.info(
                        "Minecraft TCP reachability probe passed via SOCKS5: target={}:{}, timeoutMs={}",
                        MINECRAFT_REACHABILITY_HOST,
                        MINECRAFT_REACHABILITY_PORT,
                        timeoutMs
                    );
                }
                return true;
            } catch (IOException error) {
                LOGGER.warn(
                    "Minecraft TCP reachability probe failed via SOCKS5: target={}:{}, timeoutMs={}, message='{}', cause='{}'",
                    MINECRAFT_REACHABILITY_HOST,
                    MINECRAFT_REACHABILITY_PORT,
                    timeoutMs,
                    safeMessage(error),
                    causeText(error),
                    error
                );
                return false;
            }
        }

        Proxy proxy = config.type.toJavaProxy(config.socketAddress());

        try (Socket socket = new Socket(proxy)) {
            socket.connect(
                InetSocketAddress.createUnresolved(MINECRAFT_REACHABILITY_HOST, MINECRAFT_REACHABILITY_PORT),
                timeoutMs
            );

            if (logSuccess) {
                LOGGER.info(
                    "Minecraft TCP reachability probe passed: target={}:{}, timeoutMs={}",
                    MINECRAFT_REACHABILITY_HOST,
                    MINECRAFT_REACHABILITY_PORT,
                    timeoutMs
                );
            }

            return true;
        } catch (IOException error) {
            LOGGER.warn(
                "Minecraft TCP reachability probe failed: target={}:{}, timeoutMs={}, message='{}', cause='{}'",
                MINECRAFT_REACHABILITY_HOST,
                MINECRAFT_REACHABILITY_PORT,
                timeoutMs,
                safeMessage(error),
                causeText(error),
                error
            );
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static void verifySocks5Tunnel(ProxyConfig config, String targetHost, int targetPort, int timeoutMs) throws IOException {
        Socks5ProxyHandler socksHandler = createSocks5Handler(config);
        socksHandler.setConnectTimeoutMillis(SOCKS_STAGE_TIMEOUT_MS);

        NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);
        Channel channel = null;
        int effectiveTimeoutMs = Math.max(1, Math.min(timeoutMs, SOCKS_STAGE_TIMEOUT_MS));

        try {
            Bootstrap bootstrap = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, effectiveTimeoutMs)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast("viperproxy_socks5_probe", socksHandler);
                    }
                });

            LOGGER.info("SOCKS5 reachability probe connecting: destination={}:{}, timeoutMs={}", targetHost, targetPort, effectiveTimeoutMs);

            ChannelFuture connectFuture = bootstrap.connect(InetSocketAddress.createUnresolved(targetHost, targetPort));
            if (!connectFuture.await(effectiveTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("Timed out connecting through SOCKS5 proxy to " + targetHost + ":" + targetPort + '.');
            }

            if (!connectFuture.isSuccess()) {
                throw new IOException("Failed to open SOCKS5 probe connection to " + targetHost + ":" + targetPort + '.', connectFuture.cause());
            }

            channel = connectFuture.channel();

            io.netty.util.concurrent.Future<Channel> tunnelFuture = socksHandler.connectFuture();
            boolean tunnelEstablished = tunnelFuture.await(effectiveTimeoutMs, TimeUnit.MILLISECONDS);
            if (!tunnelEstablished || !tunnelFuture.isSuccess()) {
                throw new IOException(
                    "SOCKS5 tunnel did not establish within timeout: " + safeMessage(tunnelFuture.cause()),
                    tunnelFuture.cause()
                );
            }

            LOGGER.info("SOCKS5 reachability tunnel established: destination={}:{}, timeoutMs={}", targetHost, targetPort, effectiveTimeoutMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while probing Minecraft reachability through SOCKS5.", interrupted);
        } finally {
            if (channel != null) {
                channel.close().awaitUninterruptibly();
            }
            eventLoop.shutdownGracefully(0L, 500L, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
    }

    private static Socks5ProxyHandler createSocks5Handler(ProxyConfig config) {
        InetSocketAddress proxyAddress;
        try {
            proxyAddress = new InetSocketAddress(InetAddress.getByName(config.host), config.port);
        } catch (Exception resolveError) {
            throw new IllegalArgumentException(
                "Unable to resolve proxy host '" + config.host + "': " + safeMessage(resolveError),
                resolveError
            );
        }

        return config.hasCredentials()
            ? new Socks5ProxyHandler(proxyAddress, config.username, config.password)
            : new Socks5ProxyHandler(proxyAddress);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return (message == null || message.isBlank()) ? error.getClass().getSimpleName() : message;
    }

    private static String causeText(Throwable error) {
        Throwable cause = error.getCause();
        return cause == null ? "none" : safeMessage(cause);
    }

    public record TestResult(String externalIp, long latencyMs, boolean minecraftReachable) {
    }
}
