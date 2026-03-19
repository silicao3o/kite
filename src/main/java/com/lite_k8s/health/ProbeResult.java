package com.lite_k8s.health;

import lombok.Getter;

@Getter
public class ProbeResult {

    private final boolean success;
    private final String message;
    private final long responseTimeMs;

    private ProbeResult(boolean success, String message, long responseTimeMs) {
        this.success = success;
        this.message = message;
        this.responseTimeMs = responseTimeMs;
    }

    public static ProbeResult success(long responseTimeMs) {
        return new ProbeResult(true, "OK", responseTimeMs);
    }

    public static ProbeResult failure(String message) {
        return new ProbeResult(false, message, -1);
    }
}
