package com.contractlens.service;

import com.contractlens.dto.AnalysisResultPayload;
import com.contractlens.entity.AnalysisResult;
import com.contractlens.entity.Contract;
import com.contractlens.repository.AnalysisResultRepository;
import com.contractlens.repository.ContractRepository;
import com.contractlens.service.ai.AiContractAnalyst;
import com.contractlens.service.graph.GraphContextService;
import com.contractlens.util.JsonSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final String DEFAULT_INITIAL_PROMPT = "请对这份租房合同做一次完整风险分析。";
    private static final int MAX_CHUNK_LENGTH = 180;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private AiContractAnalyst analyst;

    @Autowired
    private Retriever<TextSegment> retriever;

    @Autowired
    private AnalysisChatSessionService analysisChatSessionService;

    @Autowired
    private GraphContextService graphContextService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(4);

    public AnalysisResult analyzeContract(Long contractId, String username) throws IOException {
        Contract contract = getOwnedContract(contractId, username);
        RetrievalBundle retrievalBundle = retrieveContext(contract.getContent());
        return generateAndSaveStructuredResult(contract, retrievalBundle.retrievedContext(), retrievalBundle.graphContext());
    }

    public SseEmitter streamContractAnalysis(Long contractId, String username, String message) {
        SseEmitter emitter = new SseEmitter(0L);
        streamExecutor.execute(() -> handleStreamingAnalysis(emitter, contractId, username, message));
        return emitter;
    }

    @PreDestroy
    public void shutdownExecutor() {
        streamExecutor.shutdown();
    }

    private void handleStreamingAnalysis(SseEmitter emitter, Long contractId, String username, String message) {
        try {
            String normalizedMessage = StringUtils.hasText(message) ? message.trim() : DEFAULT_INITIAL_PROMPT;
            Contract contract = getOwnedContract(contractId, username);
            Optional<AnalysisResult> existingResultOptional = analysisResultRepository.findByContractId(contractId);
            AnalysisResult latestResult = existingResultOptional.orElse(null);
            boolean initialRound = !StringUtils.hasText(message);

            sendStatus(emitter, contractId, initialRound ? "session_started" : "follow_up_started",
                    initialRound ? "已创建合同分析会话，开始准备上下文" : "已接收追问，开始准备上下文");

            RetrievalBundle retrievalBundle = resolveRetrievedContext(emitter, contract, latestResult, initialRound);

            if (initialRound) {
                sendStatus(emitter, contractId, "analyzing_contract", "正在生成结构化风险分析");
                latestResult = generateAndSaveStructuredResult(contract, retrievalBundle.retrievedContext(), retrievalBundle.graphContext());
                String answer = buildInitialAnswer(latestResult);
                analysisChatSessionService.appendUserMessage(contractId, normalizedMessage);
                analysisChatSessionService.appendAssistantMessage(contractId, answer);
                sendStatus(emitter, contractId, "streaming_answer", "正在分段返回分析结论");
                streamAnswer(emitter, contractId, answer);
                sendDone(emitter, contractId, "initial_analysis", true, latestResult);
            } else {
                sendStatus(emitter, contractId, "generating_answer", "正在结合合同与历史会话生成追问回答");
                String conversationHistory = analysisChatSessionService.buildPromptHistory(contractId);
                String answer = analyst.answerFollowUp(contract.getContent(), retrievalBundle.retrievedContext(), retrievalBundle.graphContext(), conversationHistory, normalizedMessage);
                analysisChatSessionService.appendUserMessage(contractId, normalizedMessage);
                analysisChatSessionService.appendAssistantMessage(contractId, answer);
                sendStatus(emitter, contractId, "streaming_answer", "正在分段返回追问回答");
                streamAnswer(emitter, contractId, answer);
                sendDone(emitter, contractId, "follow_up", false, latestResult);
            }

            emitter.complete();
        } catch (Exception ex) {
            log.error("Stream analysis failed for contractId={}", contractId, ex);
            sendError(emitter, contractId, ex.getMessage());
        }
    }

    private Contract getOwnedContract(Long contractId, String username) {
        return contractRepository.findByIdAndUserUsername(contractId, username)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
    }

    private RetrievalBundle resolveRetrievedContext(SseEmitter emitter, Contract contract, AnalysisResult latestResult, boolean forceRefresh) throws IOException {
        if (!forceRefresh
                && latestResult != null
                && StringUtils.hasText(latestResult.getRetrievedContext())
                && StringUtils.hasText(latestResult.getGraphContext())) {
            sendStatus(emitter, contract.getId(), "context_ready", "已复用最近一次检索上下文");
            return new RetrievalBundle(latestResult.getRetrievedContext(), latestResult.getGraphContext());
        }

        sendStatus(emitter, contract.getId(), "retrieving_context", "正在检索相关法律知识");
        RetrievalBundle retrievalBundle = retrieveContext(contract.getContent());
        sendStatus(emitter, contract.getId(), "context_ready", "相关法律知识检索完成");
        if (!forceRefresh && latestResult != null) {
            latestResult.setRetrievedContext(retrievalBundle.retrievedContext());
            latestResult.setGraphContext(retrievalBundle.graphContext());
            analysisResultRepository.save(latestResult);
        }
        return retrievalBundle;
    }

    private RetrievalBundle retrieveContext(String contractContent) {
        List<TextSegment> relevantSegments = retriever.findRelevant(contractContent);
        String retrievedContext = relevantSegments.stream()
                .map(TextSegment::text)
                .collect(Collectors.joining("\n\n---\n\n"));
        GraphContextService.GraphContextResult graphContextResult = graphContextService.buildGraphContext(relevantSegments);
        return new RetrievalBundle(retrievedContext, graphContextResult.graphContext());
    }

    private AnalysisResult generateAndSaveStructuredResult(Contract contract, String retrievedContext, String graphContext) throws IOException {
        String jsonResponse = analyst.analyzeContract(contract.getContent(), retrievedContext, graphContext);
        AnalysisResult result = mapJsonToAnalysisResult(contract, retrievedContext, graphContext, jsonResponse);
        return analysisResultRepository.save(result);
    }

    private AnalysisResult mapJsonToAnalysisResult(Contract contract, String retrievedContext, String graphContext, String jsonResponse) throws IOException {
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonResponse);
        } catch (JsonProcessingException ex) {
            String cleaned = JsonSanitizer.extractJsonObject(jsonResponse);
            if (!StringUtils.hasText(cleaned)) {
                throw ex;
            }
            try {
                root = objectMapper.readTree(cleaned);
            } catch (JsonProcessingException second) {
                String preview = cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
                log.warn("AI response is not valid JSON for contractId={}, preview={}", contract.getId(), preview);
                throw second;
            }
        }
        AnalysisResult result = analysisResultRepository.findByContractId(contract.getId()).orElseGet(AnalysisResult::new);
        result.setContract(contract);
        result.setSummary(readText(root, "summary"));
        result.setRiskLevel(defaultText(readText(root, "risk_level"), "中"));
        result.setRiskScore(readInteger(root, "risk_score"));
        result.setPartyLessorRisks(readJson(root, "party_lessor_risks", "[]"));
        result.setPartyTenantRisks(readJson(root, "party_tenant_risks", "[]"));
        result.setSuggestions(readJson(root, "suggestions", "[]"));
        result.setContractTags(readJson(root, "contract_tags", "[]"));
        result.setRetrievedContext(retrievedContext);
        result.setGraphContext(graphContext);
        return result;
    }

    private String buildInitialAnswer(AnalysisResult result) throws JsonProcessingException {
        StringBuilder builder = new StringBuilder();
        builder.append("整体结论：").append(defaultText(result.getSummary(), "已完成租房合同风险分析。")).append("\n\n");
        builder.append("风险评分：").append(result.getRiskScore() != null ? result.getRiskScore() : "暂无")
                .append("；风险等级：").append(defaultText(result.getRiskLevel(), "待确认")).append("\n\n");

        appendRiskSection(builder, "租客视角重点风险", result.getPartyTenantRisks());
        appendRiskSection(builder, "房东视角重点风险", result.getPartyLessorRisks());
        appendSuggestionsSection(builder, result.getSuggestions());
        appendTagsSection(builder, result.getContractTags());

        return builder.toString().trim();
    }

    private void appendRiskSection(StringBuilder builder, String title, String risksJson) throws JsonProcessingException {
        JsonNode riskNodes = parseJsonArray(risksJson);
        if (riskNodes.isEmpty()) {
            return;
        }

        builder.append(title).append("：\n");
        int index = 1;
        for (JsonNode riskNode : riskNodes) {
            builder.append(index++).append(". ")
                    .append(defaultText(readText(riskNode, "risk_type"), "未分类风险"))
                    .append("（").append(defaultText(readText(riskNode, "risk_level"), "待确认")).append("）")
                    .append("\n");
            builder.append("条款：").append(defaultText(readText(riskNode, "clause_text"), "未提供")).append("\n");
            builder.append("说明：").append(defaultText(readText(riskNode, "risk_description"), "未提供")).append("\n");
            builder.append("法律依据：").append(defaultText(readText(riskNode, "legal_basis"), "未提供")).append("\n");
            builder.append("建议：").append(defaultText(readText(riskNode, "suggestion"), "未提供")).append("\n\n");
        }
    }

    private void appendSuggestionsSection(StringBuilder builder, String suggestionsJson) throws JsonProcessingException {
        JsonNode suggestionNodes = parseJsonArray(suggestionsJson);
        if (suggestionNodes.isEmpty()) {
            return;
        }

        builder.append("建议优先处理事项：\n");
        int index = 1;
        for (JsonNode suggestionNode : suggestionNodes) {
            builder.append(index++).append(". ");
            if (suggestionNode.isTextual()) {
                builder.append(suggestionNode.asText());
            } else {
                builder.append(defaultText(readText(suggestionNode, "suggested_text"), readText(suggestionNode, "reason")));
                String originalText = readText(suggestionNode, "original_text");
                if (StringUtils.hasText(originalText)) {
                    builder.append("\n原条款：").append(originalText);
                }
            }
            builder.append("\n");
        }
        builder.append("\n");
    }

    private void appendTagsSection(StringBuilder builder, String tagsJson) throws JsonProcessingException {
        JsonNode tagNodes = parseJsonArray(tagsJson);
        if (tagNodes.isEmpty()) {
            return;
        }

        List<String> tags = new ArrayList<>();
        for (JsonNode tagNode : tagNodes) {
            if (tagNode.isTextual()) {
                tags.add(tagNode.asText());
            }
        }

        if (!tags.isEmpty()) {
            builder.append("标签：").append(String.join("、", tags)).append("\n");
        }
    }

    private JsonNode parseJsonArray(String json) throws JsonProcessingException {
        if (!StringUtils.hasText(json)) {
            return objectMapper.readTree("[]");
        }
        return objectMapper.readTree(json);
    }

    private void streamAnswer(SseEmitter emitter, Long contractId, String answer) throws IOException {
        List<String> chunks = splitAnswer(answer);
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("contractId", contractId);
            payload.put("index", i);
            payload.put("chunk", chunks.get(i));
            payload.put("isLast", i == chunks.size() - 1);
            sendEvent(emitter, "answer", payload);
        }
    }

    private List<String> splitAnswer(String answer) {
        List<String> chunks = new ArrayList<>();
        String normalized = defaultText(answer, "").replace("\r\n", "\n").trim();
        if (!StringUtils.hasText(normalized)) {
            chunks.add("");
            return chunks;
        }

        String[] paragraphs = normalized.split("\\n\\n+");
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            String candidate = paragraph.trim();
            if (!StringUtils.hasText(candidate)) {
                continue;
            }

            if (currentChunk.length() > 0 && currentChunk.length() + candidate.length() + 2 > MAX_CHUNK_LENGTH) {
                chunks.add(currentChunk.toString());
                currentChunk.setLength(0);
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(candidate);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        if (chunks.isEmpty()) {
            chunks.add(normalized);
        }
        return chunks;
    }

    private void sendStatus(SseEmitter emitter, Long contractId, String phase, String message) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("phase", phase);
        payload.put("message", message);
        payload.put("sessionMessageCount", analysisChatSessionService.getMessageCount(contractId));
        sendEvent(emitter, "status", payload);
    }

    private void sendDone(SseEmitter emitter, Long contractId, String roundType, boolean structuredResultUpdated, AnalysisResult analysisResult) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("roundType", roundType);
        payload.put("structuredResultUpdated", structuredResultUpdated);
        payload.put("sessionMessageCount", analysisChatSessionService.getMessageCount(contractId));
        payload.put("analysisResult", AnalysisResultPayload.from(analysisResult));
        sendEvent(emitter, "done", payload);
    }

    private void sendError(SseEmitter emitter, Long contractId, String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("contractId", contractId);
            payload.put("message", defaultText(message, "分析失败"));
            payload.put("retryable", true);
            sendEvent(emitter, "error", payload);
        } catch (IOException ioException) {
            log.warn("Failed to send SSE error event for contractId={}", contractId, ioException);
        } finally {
            emitter.completeWithError(new RuntimeException(defaultText(message, "分析失败")));
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(payload));
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private Integer readInteger(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.isNumber() ? value.asInt() : Integer.parseInt(value.asText());
    }

    private String readJson(JsonNode root, String fieldName, String defaultValue) throws JsonProcessingException {
        JsonNode value = root.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        return objectMapper.writeValueAsString(value);
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text : fallback;
    }

    private record RetrievalBundle(String retrievedContext, String graphContext) {
    }
}
