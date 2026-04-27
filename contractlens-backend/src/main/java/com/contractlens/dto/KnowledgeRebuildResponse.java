package com.contractlens.dto;

import java.time.Instant;

public record KnowledgeRebuildResponse(
        String ragMode,
        boolean ok,
        Integer writtenDocs,
        Integer deletedDocs,
        Integer skippedDocs,
        Long durationMs,
        Instant finishedAt,
        String inputsDir,
        String error
) {
}

