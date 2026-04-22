package com.contractlens.controller;

import com.contractlens.dto.KnowledgeStatusResponse;
import com.contractlens.repository.KnowledgeDocRepository;
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
        String probeError = null;
        Long graphNodeCount = null;
        Long graphEdgeCount = null;
        Integer graphProbeReturnedDocs = null;
        String graphProbeError = null;

        try {
            int size = retriever.findRelevant(probeQuery).size();
            hitCount = size;
            returnedSegments = size;
        } catch (Exception ex) {
            probeError = ex.getMessage();
        }

        if (graphEnabled) {
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
                .knowledgeDocsCount(count)
                .embeddingStoreUrl(blankToNull(embeddingStoreUrl))
                .embeddingStoreCollection(blankToNull(embeddingStoreCollection))
                .retrieverProbeHitCount(hitCount)
                .retrieverProbeReturnedSegments(returnedSegments)
                .retrieverTopK(retrieverTopK)
                .retrieverMinScore(retrieverMinScore)
                .retrieverProbeQuery(blankToNull(probeQuery))
                .retrieverProbeError(blankToNull(probeError))
                .graphEnabled(graphEnabled)
                .graphNodeCount(graphNodeCount)
                .graphEdgeCount(graphEdgeCount)
                .graphProbeQuery(blankToNull(probeQuery))
                .graphProbeReturnedDocs(graphProbeReturnedDocs)
                .graphProbeError(blankToNull(graphProbeError))
                .build();

        return ResponseEntity.ok(response);
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
