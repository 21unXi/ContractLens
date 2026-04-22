package com.contractlens.dto;

import com.contractlens.entity.KnowledgeDoc;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeDocSummary {

    private String docId;
    private String title;
    private String docType;
    private LocalDateTime createdAt;

    public static KnowledgeDocSummary from(KnowledgeDoc doc) {
        if (doc == null) {
            return null;
        }
        return KnowledgeDocSummary.builder()
                .docId(doc.getDocId())
                .title(doc.getTitle())
                .docType(doc.getDocType())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}

