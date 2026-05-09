package com.viperproxy.proxy;

public record StatusDetail(FailureReason reason, String extra) {
    public StatusDetail {
        if (reason == null) {
            reason = FailureReason.UNKNOWN;
        }

        if (extra == null) {
            extra = "";
        }
    }

    public static StatusDetail none() {
        return new StatusDetail(FailureReason.NONE, "");
    }

    public static StatusDetail of(FailureReason reason, String extra) {
        return new StatusDetail(reason, extra == null ? "" : extra);
    }

    public String summarize() {
        return this.extra.isBlank() ? this.reason.userMessage() : this.extra;
    }
}
