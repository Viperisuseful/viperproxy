package com.viperproxy.proxy;

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetSocketAddress;
import java.net.Proxy;
import net.minecraft.text.Text;

public enum ProxyType {
    SOCKS5("SOCKS5"),
    HTTP("HTTP"),
    HTTPS("HTTPS");

    private final String label;

    ProxyType(String label) {
        this.label = label;
    }

    public Text asText() {
        return Text.literal(this.label);
    }

    public String label() {
        return this.label;
    }

    public Proxy toJavaProxy(InetSocketAddress socketAddress) {
        InetSocketAddress unresolved = toUnresolved(socketAddress);

        return switch (this) {
            case SOCKS5 -> new Proxy(Proxy.Type.SOCKS, unresolved);
            case HTTP, HTTPS -> new Proxy(Proxy.Type.HTTP, unresolved);
        };
    }

    public ProxyHandler toNettyProxyHandler(InetSocketAddress socketAddress, String username, String password) {
        boolean hasAuth = username != null && !username.isBlank() && password != null && !password.isBlank();

        return switch (this) {
            case SOCKS5 -> hasAuth
                ? new Socks5ProxyHandler(socketAddress, username, password)
                : new Socks5ProxyHandler(socketAddress);
            case HTTP, HTTPS -> hasAuth
                ? new HttpProxyHandler(socketAddress, username, password)
                : new HttpProxyHandler(socketAddress);
        };
    }

    private static InetSocketAddress toUnresolved(InetSocketAddress socketAddress) {
        return InetSocketAddress.createUnresolved(socketAddress.getHostString(), socketAddress.getPort());
    }
}
