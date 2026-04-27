package com.contractlens.service;

import com.contractlens.entity.KnowledgeDoc;
import com.contractlens.dto.KnowledgeRebuildResponse;
import com.contractlens.rag.RagMode;
import com.contractlens.rag.RagProperties;
import com.contractlens.repository.KnowledgeDocRepository;
import com.contractlens.service.lightrag.LightRagIngestResult;
import com.contractlens.service.lightrag.LightRagIngestService;
import com.contractlens.service.graph.GraphSchemaService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final String UPSERT_KNOWLEDGE_DOC_CYPHER = """
            MERGE (d:KnowledgeDoc {docId: $docId})
            ON CREATE SET d.createdAt = $createdAt
            SET d.title = $title,
                d.docType = $docType,
                d.chunkCount = $chunkCount,
                d.contentPreview = $contentPreview,
                d.updatedAt = $now
            WITH d
            OPTIONAL MATCH (d)-[r1:REFERS_TO]->(:LawArticle)
            DELETE r1
            WITH d
            OPTIONAL MATCH (d)-[r2:HAS_RISK_TYPE]->(:RiskType)
            DELETE r2
            WITH d
            FOREACH (_ IN CASE WHEN $lawArticle IS NULL THEN [] ELSE [1] END |
                MERGE (la:LawArticle {name: $lawArticle})
                MERGE (d)-[:REFERS_TO]->(la)
            )
            FOREACH (_ IN CASE WHEN $riskType IS NULL THEN [] ELSE [1] END |
                MERGE (rt:RiskType {name: $riskType})
                MERGE (d)-[:HAS_RISK_TYPE]->(rt)
            )
            """;

    private final KnowledgeDocRepository knowledgeDocRepository;
    private final EmbeddingStoreIngestor embeddingStoreIngestor;
    private final Driver neo4jDriver;
    private final GraphSchemaService graphSchemaService;
    private final int graphContentSnippetLen;
    private final RagProperties ragProperties;
    private final LightRagIngestService lightRagIngestService;

    public KnowledgeService(
            KnowledgeDocRepository knowledgeDocRepository,
            EmbeddingStoreIngestor embeddingStoreIngestor,
            Driver neo4jDriver,
            GraphSchemaService graphSchemaService,
            @Value("${contractlens.rag.graph.content-snippet-len:200}") int graphContentSnippetLen,
            RagProperties ragProperties,
            LightRagIngestService lightRagIngestService
    ) {
        this.knowledgeDocRepository = knowledgeDocRepository;
        this.embeddingStoreIngestor = embeddingStoreIngestor;
        this.neo4jDriver = neo4jDriver;
        this.graphSchemaService = graphSchemaService;
        this.graphContentSnippetLen = Math.max(graphContentSnippetLen, 0);
        this.ragProperties = ragProperties;
        this.lightRagIngestService = lightRagIngestService;
    }

    public Page<KnowledgeDoc> listKnowledgeDocs(Pageable pageable) {
        return knowledgeDocRepository.findAll(pageable);
    }

    public KnowledgeRebuildResponse rebuild() {
        List<KnowledgeDoc> knowledgeDocs = knowledgeDocRepository.findAll();
        if (ragProperties.getMode() == RagMode.LIGHTRAG) {
            try {
                LightRagIngestResult result = lightRagIngestService.rebuildInputs(knowledgeDocs);
                if (!result.ok()) {
                    throw new IllegalStateException(result.error());
                }
                return new KnowledgeRebuildResponse(
                        ragProperties.getMode().name().toLowerCase(),
                        true,
                        result.writtenDocs(),
                        result.deletedDocs(),
                        result.skippedDocs(),
                        result.durationMs(),
                        result.finishedAt(),
                        result.inputsDir(),
                        null
                );
            } catch (Exception ex) {
                return new KnowledgeRebuildResponse(
                        ragProperties.getMode().name().toLowerCase(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "LightRAG rebuild failed: " + ex.getMessage()
                );
            }
        }
        try {
            graphSchemaService.ensureConstraints();
            upsertKnowledgeGraph(knowledgeDocs);
        } catch (Exception ex) {
            log.warn("Knowledge graph rebuild failed, continue vector ingestion", ex);
        }
        ingestKnowledgeBase(knowledgeDocs);
        return new KnowledgeRebuildResponse(
                ragProperties.getMode().name().toLowerCase(),
                true,
                knowledgeDocs.size(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public void ingestKnowledgeBase() {
        List<KnowledgeDoc> knowledgeDocs = knowledgeDocRepository.findAll();
        ingestKnowledgeBase(knowledgeDocs);
    }

    private void ingestKnowledgeBase(List<KnowledgeDoc> knowledgeDocs) {
        List<Document> documents = knowledgeDocs.stream()
                .map(doc -> Document.from(doc.getContent(), createMetadata(doc)))
                .collect(Collectors.toList());
        embeddingStoreIngestor.ingest(documents);
    }

    private dev.langchain4j.data.document.Metadata createMetadata(KnowledgeDoc doc) {
        dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
        metadata.add("doc_id", doc.getDocId());
        metadata.add("title", doc.getTitle());
        metadata.add("doc_type", doc.getDocType());
        if (doc.getLawArticle() != null) {
            metadata.add("law_article", doc.getLawArticle());
        }
        if (doc.getRiskType() != null) {
            metadata.add("risk_type", doc.getRiskType());
        }
        return metadata;
    }

    private void upsertKnowledgeGraph(List<KnowledgeDoc> knowledgeDocs) {
        LocalDateTime now = LocalDateTime.now();
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                for (KnowledgeDoc doc : knowledgeDocs) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("docId", doc.getDocId());
                    params.put("title", doc.getTitle());
                    params.put("docType", doc.getDocType());
                    params.put("chunkCount", doc.getChunkCount());
                    params.put("contentPreview", buildPreview(doc.getContent(), graphContentSnippetLen));
                    params.put("createdAt", doc.getCreatedAt());
                    params.put("now", now);
                    params.put("lawArticle", blankToNull(doc.getLawArticle()));
                    params.put("riskType", blankToNull(doc.getRiskType()));
                    tx.run(UPSERT_KNOWLEDGE_DOC_CYPHER, params);
                }
                tx.run("MATCH (l:LawArticle) WHERE NOT EXISTS { MATCH (:KnowledgeDoc)-[:REFERS_TO]->(l) } DELETE l");
                tx.run("MATCH (r:RiskType) WHERE NOT EXISTS { MATCH (:KnowledgeDoc)-[:HAS_RISK_TYPE]->(r) } DELETE r");
                return null;
            });
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String buildPreview(String content, int maxLen) {
        if (content == null || maxLen <= 0) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }
}
