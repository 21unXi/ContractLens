package com.contractlens.service.lightrag;

public record LightRagQueryResult(boolean enabled, boolean ok, String context, Integer retrievedChunkCount, Long latencyMs, String raw, String error) {

    public static LightRagQueryResult disabled() {
        return new LightRagQueryResult(false, false, "", null, null, null, null);
    }

    public static LightRagQueryResult ok(String context, Integer retrievedChunkCount, String raw) {
        return new LightRagQueryResult(true, true, context, retrievedChunkCount, null, raw, null);
    }

    public static LightRagQueryResult ok(String context, Integer retrievedChunkCount, Long latencyMs, String raw) {
        return new LightRagQueryResult(true, true, context, retrievedChunkCount, latencyMs, raw, null);
    }

    public static LightRagQueryResult error(String error) {
        return new LightRagQueryResult(true, false, "", null, null, null, error);
    }

    public static LightRagQueryResult error(String error, Long latencyMs) {
        return new LightRagQueryResult(true, false, "", null, latencyMs, null, error);
    }
}
