package com.contractlens.controller;

import com.contractlens.dto.KnowledgeStatusResponse;
import com.contractlens.rag.RagMode;
import com.contractlens.rag.RagProperties;
import com.contractlens.repository.KnowledgeDocRepository;
import com.contractlens.service.lightrag.LightRagClient;
import com.contractlens.service.lightrag.LightRagProperties;
import com.contractlens.service.lightrag.LightRagQueryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.retriever.Retriever;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeStatusController {

    @Autowired
    private KnowledgeDocRepository knowledgeDocRepository;

    @Autowired
    private Retriever<TextSegment> retriever;

    @Autowired
    private Driver neo4jDriver;

    @Autowired
    private RagProperties ragProperties;

    @Autowired
    private LightRagProperties lightRagProperties;

    @Autowired
    private LightRagClient lightRagClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${langchain4j.chroma.embedding-store.url:}")
    private String embeddingStoreUrl;

    @Value("${langchain4j.chroma.embedding-store.collection-name:}")
    private String embeddingStoreCollection;

    @Value("${contractlens.rag.probe-query:租房合同 押金 违约金}")
    private String probeQuery;

    @Value("${contractlens.rag.retriever.top-k:5}")
    private int retrieverTopK;

    @Value("${contractlens.rag.retriever.min-score:0.6}")
    private double retrieverMinScore;

    @Value("${contractlens.rag.graph.enabled:true}")
    private boolean graphEnabled;

    @GetMapping("/status")
    public ResponseEntity<KnowledgeStatusResponse> status() {
        long count = knowledgeDocRepository.count();
        Integer hitCount = null;
        Integer returnedSegments = null;
        String contextPreview = null;
        String probeError = null;
        Long graphNodeCount = null;
        Long graphEdgeCount = null;
        Integer graphProbeReturnedDocs = null;
        String graphProbeError = null;
        Boolean lightRagOk = null;
        Integer lightRagProbeReturnedChunks = null;
        Integer lightRagProbeContextChars = null;
        Long lightRagProbeLatencyMs = null;
        String lightRagProbeError = null;
        Instant lastRebuildAt = null;
        Integer lastRebuildWrittenDocs = null;
        Integer lastRebuildDeletedDocs = null;
        Long lastRebuildDurationMs = null;

        if (ragProperties.getMode() == RagMode.LIGHTRAG) {
            LightRagQueryResult result = lightRagClient.query(probeQuery);
            lightRagProbeLatencyMs = result.latencyMs();
            if (result.ok()) {
                lightRagOk = true;
                lightRagProbeReturnedChunks = result.retrievedChunkCount();
                String ctx = result.context();
                lightRagProbeContextChars = ctx != null ? ctx.length() : 0;
                returnedSegments = lightRagProbeReturnedChunks != null ? lightRagProbeReturnedChunks : 0;
                String raw = result.raw();
                contextPreview = preview(StringUtils.hasText(ctx) ? ctx : raw, 200);
            } else {
                lightRagOk = false;
                lightRagProbeError = result.error();
                probeError = result.error();
            }
        } else {
            try {
                List<TextSegment> segments = retriever.findRelevant(probeQuery);
                int size = segments != null ? segments.size() : 0;
                hitCount = size;
                returnedSegments = size;
                contextPreview = previewSegments(segments, 2, 200);
            } catch (Exception ex) {
                probeError = ex.getMessage();
            }
        }

        String inputsDir = lightRagProperties.getInputsDir();
        if (StringUtils.hasText(inputsDir)) {
            Path meta = Paths.get(inputsDir.trim()).resolve(".contractlens_rebuild.json");
            if (Files.exists(meta)) {
                try {
                    String json = Files.readString(meta);
                    JsonNode root = objectMapper.readTree(json);
                    String finishedAt = root.path("finishedAt").asText(null);
                    if (StringUtils.hasText(finishedAt)) {
                        lastRebuildAt = Instant.parse(finishedAt);
                    }
                    if (root.hasNonNull("writtenDocs")) {
                        lastRebuildWrittenDocs = root.get("writtenDocs").asInt();
                    }
                    if (root.hasNonNull("deletedDocs")) {
                        lastRebuildDeletedDocs = root.get("deletedDocs").asInt();
                    }
                    if (root.hasNonNull("durationMs")) {
                        lastRebuildDurationMs = root.get("durationMs").asLong();
                    }
                } catch (Exception ignore) {
                }
            }
        }

        if (ragProperties.getMode() != RagMode.LIGHTRAG && graphEnabled) {
            Set<String> probeRiskTypes = extractRiskTypesFromQuery(probeQuery);
            Set<String> probeLawArticles = extractLawArticlesFromQuery(probeQuery);
            try (Session session = neo4jDriver.session()) {
                graphNodeCount = session.executeRead(tx -> tx.run("MATCH (n) RETURN count(n) AS c").single().get("c").asLong());
                graphEdgeCount = session.executeRead(tx -> tx.run("MATCH ()-[r]->() RETURN count(r) AS c").single().get("c").asLong());

                Result result = session.executeRead(tx -> tx.run("""
                        WITH $lawArticles AS lawArticles, $riskTypes AS riskTypes
                        MATCH (d:KnowledgeDoc)
                        WHERE (size(lawArticles) > 0 AND EXISTS { MATCH (d)-[:REFERS_TO]->(la:LawArticle) WHERE la.name IN lawArticles })
                           OR (size(riskTypes) > 0 AND EXISTS { MATCH (d)-[:HAS_RISK_TYPE]->(rt:RiskType) WHERE rt.name IN riskTypes })
                        RETURN count(distinct d) AS c
                        """, java.util.Map.of(
                        "lawArticles", List.copyOf(probeLawArticles),
                        "riskTypes", List.copyOf(probeRiskTypes)
                )));
                Record record = result.single();
                graphProbeReturnedDocs = record.get("c").isNull() ? null : (int) record.get("c").asLong();
            } catch (Exception ex) {
                graphProbeError = ex.getMessage();
            }
        }

        KnowledgeStatusResponse response = KnowledgeStatusResponse.builder()
                .ragMode(ragProperties.getMode().name().toLowerCase())
                .knowledgeDocsCount(count)
                .embeddingStoreUrl(ragProperties.getMode() == RagMode.LIGHTRAG ? blankToNull(lightRagProperties.getBaseUrl()) : blankToNull(embeddingStoreUrl))
                .embeddingStoreCollection(ragProperties.getMode() == RagMode.LIGHTRAG ? null : blankToNull(embeddingStoreCollection))
                .retrieverProbeHitCount(hitCount)
                .retrieverProbeReturnedSegments(returnedSegments)
                .retrieverTopK(retrieverTopK)
                .retrieverMinScore(retrieverMinScore)
                .retrieverProbeQuery(blankToNull(probeQuery))
                .retrieverProbeContextPreview(blankToNull(contextPreview))
                .retrieverProbeError(blankToNull(probeError))
                .graphEnabled(ragProperties.getMode() == RagMode.LIGHTRAG ? null : graphEnabled)
                .graphNodeCount(graphNodeCount)
                .graphEdgeCount(graphEdgeCount)
                .graphProbeQuery(blankToNull(probeQuery))
                .graphProbeReturnedDocs(graphProbeReturnedDocs)
                .graphProbeError(blankToNull(graphProbeError))
                .lightRagEnabled(lightRagProperties.isEnabled())
                .lightRagBaseUrl(blankToNull(lightRagProperties.getBaseUrl()))
                .lightRagQueryMode(blankToNull(lightRagProperties.getQueryMode()))
                .lightRagOk(lightRagOk)
                .lightRagProbeReturnedChunks(lightRagProbeReturnedChunks)
                .lightRagProbeContextChars(lightRagProbeContextChars)
                .lightRagProbeLatencyMs(lightRagProbeLatencyMs)
                .lightRagProbeError(blankToNull(lightRagProbeError))
                .lastRebuildAt(lastRebuildAt)
                .lastRebuildWrittenDocs(lastRebuildWrittenDocs)
                .lastRebuildDeletedDocs(lastRebuildDeletedDocs)
                .lastRebuildDurationMs(lastRebuildDurationMs)
                .build();

        return ResponseEntity.ok(response);
    }

    private static String previewSegments(List<TextSegment> segments, int maxSegments, int maxChars) {
        if (segments == null || segments.isEmpty()) return null;
        int limit = Math.min(Math.max(maxSegments, 1), segments.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            TextSegment seg = segments.get(i);
            String t = seg == null ? null : seg.text();
            if (!StringUtils.hasText(t)) continue;
            if (sb.length() > 0) sb.append("\n---\n");
            sb.append(t);
            if (sb.length() >= maxChars) break;
        }
        return preview(sb.toString(), maxChars);
    }

    private static String preview(String text, int maxChars) {
        if (!StringUtils.hasText(text)) return null;
        String normalized = text.replace("\r", "").trim();
        if (normalized.length() <= maxChars) return normalized;
        return normalized.substring(0, Math.max(maxChars - 1, 0)) + "…";
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Set<String> extractRiskTypesFromQuery(String query) {
        String q = query == null ? "" : query;
        Set<String> riskTypes = new LinkedHashSet<>();
        if (q.contains("押金")) riskTypes.add("押金风险");
        if (q.contains("租金")) riskTypes.add("租金风险");
        if (q.contains("解约") || q.contains("违约金")) riskTypes.add("提前解约风险");
        if (q.contains("维修")) riskTypes.add("维修责任风险");
        if (q.contains("转租")) riskTypes.add("转租风险");
        if (q.contains("交付")) riskTypes.add("房屋交付风险");
        if (q.contains("水电") || q.contains("物业") || q.contains("费用")) riskTypes.add("费用风险");
        return riskTypes;
    }

    private static Set<String> extractLawArticlesFromQuery(String query) {
        String q = query == null ? "" : query;
        Set<String> laws = new LinkedHashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:民法典)?\\s*第?\\s*(\\d{3,4})\\s*条?").matcher(q);
        while (matcher.find()) {
            String digits = matcher.group(1);
            if (StringUtils.hasText(digits)) {
                laws.add("民法典" + digits);
            }
        }
        return laws;
    }
}
