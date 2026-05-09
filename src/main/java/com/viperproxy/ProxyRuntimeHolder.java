package com.viperproxy;

import com.viperproxy.proxy.ProxyRuntime;
import net.fabricmc.loader.api.FabricLoader;

public final class ProxyRuntimeHolder {
    private static final String RUNTIME_KEY = "viperproxy:runtime";

    private ProxyRuntimeHolder() {
    }

    public static void setRuntime(ProxyRuntime runtime) {
        FabricLoader.getInstance().getObjectShare().put(RUNTIME_KEY, runtime);
    }

    public static ProxyRuntime getRequiredRuntime() {
        Object runtime = FabricLoader.getInstance().getObjectShare().get(RUNTIME_KEY);
        if (runtime instanceof ProxyRuntime proxyRuntime) {
            return proxyRuntime;
        }

        throw new IllegalStateException("Viper Proxy runtime has not been initialized.");
    }
}
