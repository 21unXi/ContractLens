package com.contractlens.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_docs")
public class KnowledgeDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false, unique = true, length = 50)
    private String docId;

    @Column(nullable = false)
    private String title;

    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "law_article", length = 100)
    private String lawArticle;

    @Column(name = "risk_type", length = 50)
    private String riskType;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
