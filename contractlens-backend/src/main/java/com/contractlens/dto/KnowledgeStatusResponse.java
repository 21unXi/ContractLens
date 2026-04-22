package com.contractlens.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeStatusResponse {

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
}
