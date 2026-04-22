package com.contractlens.service.graph;

import dev.langchain4j.data.segment.TextSegment;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GraphContextService {

    private static final Logger log = LoggerFactory.getLogger(GraphContextService.class);

    private static final String GRAPH_RETRIEVAL_CYPHER = """
            WITH $docIds AS docIds, $lawArticles AS lawArticles, $riskTypes AS riskTypes
            MATCH (d:KnowledgeDoc)
            WHERE d.docId IN docIds
               OR EXISTS { MATCH (d)-[:REFERS_TO]->(la:LawArticle) WHERE la.name IN lawArticles }
               OR EXISTS { MATCH (d)-[:HAS_RISK_TYPE]->(rt:RiskType) WHERE rt.name IN riskTypes }
            OPTIONAL MATCH (d)-[:REFERS_TO]->(la:LawArticle)
            OPTIONAL MATCH (d)-[:HAS_RISK_TYPE]->(rt:RiskType)
            RETURN d.docId AS docId,
                   d.title AS title,
                   d.docType AS docType,
                   d.contentPreview AS contentPreview,
                   collect(distinct la.name) AS lawArticles,
                   collect(distinct rt.name) AS riskTypes
            LIMIT $limit
            """;

    private final Driver neo4jDriver;
    private final int topN;

    public GraphContextService(Driver neo4jDriver, @Value("${contractlens.rag.graph.top-n:10}") int topN) {
        this.neo4jDriver = neo4jDriver;
        this.topN = Math.min(Math.max(topN, 1), 50);
    }

    public GraphContextResult buildGraphContext(List<TextSegment> relevantSegments) {
        Seeds seeds = extractSeeds(relevantSegments);
        try (Session session = neo4jDriver.session()) {
            List<GraphDoc> docs = session.executeRead(tx -> {
                Result result = tx.run(GRAPH_RETRIEVAL_CYPHER, Map.of(
                        "docIds", new ArrayList<>(seeds.docIds()),
                        "lawArticles", new ArrayList<>(seeds.lawArticles()),
                        "riskTypes", new ArrayList<>(seeds.riskTypes()),
                        "limit", topN
                ));
                List<GraphDoc> collected = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    collected.add(new GraphDoc(
                            blankToNull(record.get("docId").asString(null)),
                            blankToNull(record.get("title").asString(null)),
                            blankToNull(record.get("docType").asString(null)),
                            blankToNull(record.get("contentPreview").asString(null)),
                            record.get("lawArticles").asList(value -> blankToNull(value.asString(null))).stream().filter(StringUtils::hasText).collect(Collectors.toCollection(LinkedHashSet::new)),
                            record.get("riskTypes").asList(value -> blankToNull(value.asString(null))).stream().filter(StringUtils::hasText).collect(Collectors.toCollection(LinkedHashSet::new))
                    ));
                }
                return collected;
            });
            return GraphContextResult.success(formatSuccess(seeds, docs));
        } catch (Exception ex) {
            log.warn("Neo4j graph retrieval failed", ex);
            return GraphContextResult.failed(formatFailure(seeds, ex));
        }
    }

    private Seeds extractSeeds(List<TextSegment> segments) {
        Set<String> docIds = new LinkedHashSet<>();
        Set<String> lawArticles = new LinkedHashSet<>();
        Set<String> riskTypes = new LinkedHashSet<>();

        if (segments != null) {
            for (TextSegment segment : segments) {
                addIfPresent(docIds, segment.metadata("doc_id"));
                addIfPresent(lawArticles, segment.metadata("law_article"));
                addIfPresent(riskTypes, segment.metadata("risk_type"));
            }
        }
        return new Seeds(docIds, lawArticles, riskTypes);
    }

    private void addIfPresent(Set<String> target, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            target.add(normalized);
        }
    }

    private String formatSuccess(Seeds seeds, List<GraphDoc> docs) {
        StringBuilder builder = new StringBuilder();
        builder.append("图谱检索状态：SUCCESS\n");
        builder.append("种子doc_id：").append(formatList(seeds.docIds())).append("\n");
        builder.append("种子law_article：").append(formatList(seeds.lawArticles())).append("\n");
        builder.append("种子risk_type：").append(formatList(seeds.riskTypes())).append("\n");
        builder.append("命中知识条目：").append(docs != null ? docs.size() : 0).append("\n");

        if (docs != null) {
            int index = 1;
            for (GraphDoc doc : docs) {
                builder.append(index++).append(". ");
                builder.append(defaultText(doc.title(), "未命名条目"));
                if (StringUtils.hasText(doc.docType())) {
                    builder.append(" [").append(doc.docType()).append("]");
                }
                if (StringUtils.hasText(doc.docId())) {
                    builder.append(" (doc_id=").append(doc.docId()).append(")");
                }
                builder.append("\n");
                if (StringUtils.hasText(doc.contentPreview())) {
                    builder.append("摘要：").append(doc.contentPreview().trim()).append("\n");
                }
                builder.append("法条：").append(formatList(doc.lawArticles())).append("\n");
                builder.append("风险类型：").append(formatList(doc.riskTypes())).append("\n");
            }
        }

        return builder.toString().trim();
    }

    private String formatFailure(Seeds seeds, Exception ex) {
        StringBuilder builder = new StringBuilder();
        builder.append("图谱检索状态：FAILED\n");
        builder.append("失败原因：").append(defaultText(blankToNull(ex.getMessage()), ex.getClass().getSimpleName())).append("\n");
        builder.append("种子doc_id：").append(formatList(seeds.docIds())).append("\n");
        builder.append("种子law_article：").append(formatList(seeds.lawArticles())).append("\n");
        builder.append("种子risk_type：").append(formatList(seeds.riskTypes())).append("\n");
        builder.append("降级策略：仅使用向量检索结果\n");
        return builder.toString().trim();
    }

    private String formatList(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "无";
        }
        return values.stream().filter(StringUtils::hasText).collect(Collectors.joining("、"));
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record GraphContextResult(String graphContext, boolean success) {

        public static GraphContextResult success(String graphContext) {
            return new GraphContextResult(graphContext, true);
        }

        public static GraphContextResult failed(String graphContext) {
            return new GraphContextResult(graphContext, false);
        }
    }

    private record Seeds(Set<String> docIds, Set<String> lawArticles, Set<String> riskTypes) {
    }

    private record GraphDoc(String docId, String title, String docType, String contentPreview, Set<String> lawArticles, Set<String> riskTypes) {
    }
}

