package com.viperproxy.proxy;

import com.viperproxy.config.ProxyConfig;

public final class ProfileRenameVerifier {
    private ProfileRenameVerifier() {
    }

    public static void main(String[] args) {
        ProxyRuntime runtime = new ProxyRuntime();
        try {
            ProxyConfig originalConfig = runtime.getActiveConfigCopy();

            runtime.renameActiveProfile("  Work Proxy  ");
            require("Work Proxy".equals(runtime.getActiveProfileName()), "Profile name was not trimmed and saved");
            require(
                originalConfig.host.equals(runtime.getActiveConfigCopy().host),
                "Renaming changed active proxy configuration"
            );

            runtime.renameActiveProfile("   ");
            require("Profile 1".equals(runtime.getActiveProfileName()), "Blank rename did not use safe fallback name");

            System.out.println("Verified profile rename persistence and configuration isolation.");
        } finally {
            runtime.shutdown();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
