package com.viperproxy.proxy;

import com.viperproxy.config.ProxyConfig;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.function.Supplier;

/** Supplies credentials only when Java is authenticating with the active proxy. */
final class ViperProxyAuthenticator extends Authenticator {
    private final Supplier<ProxyConfig> configSupplier;

    ViperProxyAuthenticator(Supplier<ProxyConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        ProxyConfig supplied = this.configSupplier.get();
        ProxyConfig config = supplied == null ? new ProxyConfig() : supplied.normalized();
        if (!config.isUsable() || !config.hasCredentials() || !isActiveProxy(config)) {
            return null;
        }

        boolean explicitProxyRequest = getRequestorType() == RequestorType.PROXY;
        boolean javaSocksRequest = config.type == ProxyType.SOCKS5 && isSocksProtocol(getRequestingProtocol());
        if (!explicitProxyRequest && !javaSocksRequest) {
            return null;
        }

        return new PasswordAuthentication(config.username, config.password.toCharArray());
    }

    private boolean isActiveProxy(ProxyConfig config) {
        if (getRequestingPort() != config.port) {
            return false;
        }

        String requestingHost = normalizeHost(getRequestingHost());
        String configuredHost = normalizeHost(config.host);
        if (!requestingHost.isEmpty() && requestingHost.equals(configuredHost)) {
            return true;
        }

        InetAddress requestingAddress = getRequestingSite();
        if (requestingAddress == null) {
            return false;
        }

        try {
            for (InetAddress configuredAddress : InetAddress.getAllByName(config.host)) {
                if (requestingAddress.equals(configuredAddress)) {
                    return true;
                }
            }
        } catch (UnknownHostException ignored) {
            return false;
        }

        return false;
    }

    private static boolean isSocksProtocol(String protocol) {
        if (protocol == null) {
            return false;
        }

        return protocol.toUpperCase(Locale.ROOT).startsWith("SOCKS");
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }

        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
