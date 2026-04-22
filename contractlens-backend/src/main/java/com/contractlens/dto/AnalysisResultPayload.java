package com.contractlens.dto;

import com.contractlens.entity.AnalysisResult;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnalysisResultPayload {

    private Long id;
    private Long contractId;
    private String summary;
    private String riskLevel;
    private Integer riskScore;
    private String partyLessorRisks;
    private String partyTenantRisks;
    private String suggestions;
    private String contractTags;
    private LocalDateTime createdAt;

    public static AnalysisResultPayload from(AnalysisResult result) {
        if (result == null) {
            return null;
        }

        return AnalysisResultPayload.builder()
                .id(result.getId())
                .contractId(result.getContract() != null ? result.getContract().getId() : null)
                .summary(result.getSummary())
                .riskLevel(result.getRiskLevel())
                .riskScore(result.getRiskScore())
                .partyLessorRisks(result.getPartyLessorRisks())
                .partyTenantRisks(result.getPartyTenantRisks())
                .suggestions(result.getSuggestions())
                .contractTags(result.getContractTags())
                .createdAt(result.getCreatedAt())
                .build();
    }
}
