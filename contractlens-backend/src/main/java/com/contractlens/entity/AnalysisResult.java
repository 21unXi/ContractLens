package com.contractlens.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "analysis_results")
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "contract_id", referencedColumnName = "id", nullable = false)
    private Contract contract;

    @Column(name = "contract_content_hash", length = 64)
    private String contractContentHash;

    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "party_lessor_risks", columnDefinition = "json")
    private String partyLessorRisks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "party_tenant_risks", columnDefinition = "json")
    private String partyTenantRisks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String suggestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contract_tags", columnDefinition = "json")
    private String contractTags;

    @Lob
    @Column(name = "retrieved_context", columnDefinition = "TEXT")
    private String retrievedContext;

    @Lob
    @Column(name = "graph_context", columnDefinition = "TEXT")
    private String graphContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clause_conflicts", columnDefinition = "json")
    private String clauseConflicts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
