package com.viperproxy.proxy;

public enum FailureReason {
    NONE("", ""),
    DISABLED("DISABLED", "Proxy is disabled"),
    INVALID_CONFIG("INVALID", "Proxy configuration is invalid"),
    TIMEOUT("TIMEOUT", "Request timed out"),
    AUTH_FAILED("AUTH FAILED", "Proxy authentication failed"),
    CONNECTION_REFUSED("REFUSED", "Connection refused"),
    DNS_FAILURE("DNS", "DNS resolution failure"),
    MINECRAFT_UNREACHABLE("MINECRAFT", "Minecraft services are unreachable through the proxy"),
    PROXY_DROPPED("DROPPED", "Proxy tunnel dropped during session"),
    UNKNOWN("UNKNOWN", "Unknown proxy error");

    private final String code;
    private final String userMessage;

    FailureReason(String code, String userMessage) {
        this.code = code;
        this.userMessage = userMessage;
    }

    public String code() {
        return this.code;
    }

    public String userMessage() {
        return this.userMessage;
    }
}
