package com.viperproxy;

import com.viperproxy.proxy.ProxyRuntime;
import com.viperproxy.ui.MultiplayerButtonInjector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class ViperProxyClient implements ClientModInitializer {
    public static final String MOD_ID = "viperproxy";

    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        ProxyRuntime runtime = new ProxyRuntime();
        ProxyRuntimeHolder.setRuntime(runtime);
        runtime.initialize();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ProxyRuntime.getInstance().shutdown());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ProxyRuntime.getInstance().shutdown();
        }, "ViperProxy-Shutdown"));

        MultiplayerButtonInjector.register();
    }
}
