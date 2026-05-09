package com.viperproxy.config;

import com.viperproxy.proxy.ProxyType;
import java.net.InetSocketAddress;

public final class ProxyConfig {
    public boolean enabled = false;
    public String host = "";
    public int port = 1080;
    public String username = "";
    public String password = "";
    public ProxyType type = ProxyType.SOCKS5;

    public ProxyConfig copy() {
        ProxyConfig copy = new ProxyConfig();
        copy.enabled = this.enabled;
        copy.host = this.host;
        copy.port = this.port;
        copy.username = this.username;
        copy.password = this.password;
        copy.type = this.type;
        return copy;
    }

    public ProxyConfig normalized() {
        ProxyConfig normalized = this.copy();
        normalized.host = normalized.host == null ? "" : normalized.host.trim();
        normalized.username = normalized.username == null ? "" : normalized.username.trim();
        normalized.password = normalized.password == null ? "" : normalized.password;

        if (normalized.type == null) {
            normalized.type = ProxyType.SOCKS5;
        }

        return normalized;
    }

    public boolean hasCredentials() {
        return this.username != null
            && !this.username.isBlank()
            && this.password != null
            && !this.password.isBlank();
    }

    public boolean isUsable() {
        ProxyConfig cfg = this.normalized();
        return cfg.enabled
            && !cfg.host.isBlank()
            && cfg.port > 0
            && cfg.port <= 65535;
    }

    public InetSocketAddress socketAddress() {
        ProxyConfig cfg = this.normalized();
        return InetSocketAddress.createUnresolved(cfg.host, cfg.port);
    }
}
