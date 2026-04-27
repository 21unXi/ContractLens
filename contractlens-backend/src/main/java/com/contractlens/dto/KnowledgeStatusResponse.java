package com.contractlens.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class KnowledgeStatusResponse {

    private String ragMode;
    private long knowledgeDocsCount;
    private String embeddingStoreUrl;
    private String embeddingStoreCollection;
    private Integer retrieverProbeHitCount;
    private Integer retrieverProbeReturnedSegments;
    private Integer retrieverTopK;
    private Double retrieverMinScore;
    private String retrieverProbeQuery;
    private String retrieverProbeError;
    private Boolean graphEnabled;
    private Long graphNodeCount;
    private Long graphEdgeCount;
    private String graphProbeQuery;
    private Integer graphProbeReturnedDocs;
    private String graphProbeError;

    private Boolean lightRagEnabled;
    private String lightRagBaseUrl;
    private String lightRagQueryMode;
    private Boolean lightRagOk;
    private Integer lightRagProbeReturnedChunks;
    private Integer lightRagProbeContextChars;
    private Long lightRagProbeLatencyMs;
    private String lightRagProbeError;

    private Instant lastRebuildAt;
    private Integer lastRebuildWrittenDocs;
    private Integer lastRebuildDeletedDocs;
    private Long lastRebuildDurationMs;
}
