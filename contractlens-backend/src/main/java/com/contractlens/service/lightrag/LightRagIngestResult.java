package com.contractlens.service.lightrag;

import java.time.Instant;

public record LightRagIngestResult(
        boolean ok,
        String inputsDir,
        int writtenDocs,
        int deletedDocs,
        int skippedDocs,
        long durationMs,
        Instant finishedAt,
        String error
) {
}
