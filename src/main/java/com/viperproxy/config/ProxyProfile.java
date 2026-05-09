package com.viperproxy.config;

public final class ProxyProfile {
    private String name;
    private ProxyConfig config;

    public ProxyProfile(String name, ProxyConfig config) {
        this.name = normalizeName(name);
        this.config = config == null ? new ProxyConfig() : config.normalized();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = normalizeName(name);
    }

    public ProxyConfig getConfigCopy() {
        return this.config.copy();
    }

    public void setConfig(ProxyConfig config) {
        this.config = config == null ? new ProxyConfig() : config.normalized();
    }

    public ProxyProfile copy() {
        return new ProxyProfile(this.name, this.config.copy());
    }

    private static String normalizeName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "Default";
        }

        return candidate.trim();
    }
}
