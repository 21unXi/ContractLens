package com.contractlens.service;

import com.contractlens.dto.AnalysisResultPayload;
import com.contractlens.config.SseProperties;
import com.contractlens.entity.AnalysisResult;
import com.contractlens.entity.Contract;
import com.contractlens.rag.RagMode;
import com.contractlens.rag.RagProperties;
import com.contractlens.repository.AnalysisResultRepository;
import com.contractlens.repository.ContractRepository;
import com.contractlens.service.ai.AiContractAnalyst;
import com.contractlens.service.ai.StreamingFollowUpAnswerer;
import com.contractlens.service.graph.GraphContextService;
import com.contractlens.service.lightrag.LightRagClient;
import com.contractlens.service.lightrag.LightRagQueryResult;
import com.contractlens.util.ContentHashUtil;
import com.contractlens.util.JsonSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private StreamingFollowUpAnswerer streamingFollowUpAnswerer;

    @Autowired
    private Retriever<TextSegment> retriever;

    @Autowired
    private AnalysisChatSessionService analysisChatSessionService;

    @Autowired
    private GraphContextService graphContextService;

    @Autowired
    private RagProperties ragProperties;

    @Autowired
    private LightRagClient lightRagClient;

    @Autowired
    private SseProperties sseProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);

    public AnalysisResult analyzeContract(Long contractId, String username) throws IOException {
        Contract contract = getOwnedContract(contractId, username);
        ensureContractHash(contract);
        RetrievalBundle retrievalBundle = retrieveContext(contract.getContent());
        return generateAndSaveStructuredResult(contract, retrievalBundle.retrievedContext(), retrievalBundle.graphContext());
    }

    public SseEmitter streamContractAnalysis(Long contractId, String username, String message) {
        long timeoutMs = sseProperties != null ? sseProperties.timeoutMs() : 0L;
        if (timeoutMs < 0) {
            timeoutMs = 0L;
        }
        SseEmitter emitter = new SseEmitter(timeoutMs);
        streamExecutor.execute(() -> handleStreamingAnalysis(emitter, contractId, username, message));
        return emitter;
    }

    @PreDestroy
    public void shutdownExecutor() {
        streamExecutor.shutdown();
        heartbeatExecutor.shutdown();
    }

    private void handleStreamingAnalysis(SseEmitter emitter, Long contractId, String username, String message) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(ex -> cancelled.set(true));
        ScheduledFuture<?> heartbeat = startHeartbeat(emitter, contractId, cancelled);
        try {
            long sessionStartNs = System.nanoTime();
            Map<String, Long> phaseStartNs = new LinkedHashMap<>();
            Map<String, Long> phaseDurationsMs = new LinkedHashMap<>();

            String normalizedMessage = StringUtils.hasText(message) ? message.trim() : DEFAULT_INITIAL_PROMPT;
            Contract contract = getOwnedContract(contractId, username);
            ensureContractHash(contract);
            Optional<AnalysisResult> existingResultOptional = analysisResultRepository.findByContractId(contractId);
            AnalysisResult latestResult = existingResultOptional.orElse(null);
            boolean initialRound = !StringUtils.hasText(message);

            sendStatusTimed(emitter, contractId, initialRound ? "session_started" : "follow_up_started",
                    initialRound ? "已创建合同分析会话，开始准备上下文" : "已接收追问，开始准备上下文", sessionStartNs, phaseStartNs);
            if (cancelled.get()) {
                return;
            }

            RetrievalBundle retrievalBundle = resolveRetrievedContext(emitter, contract, latestResult, initialRound, contractId, sessionStartNs, phaseStartNs, phaseDurationsMs);
            if (cancelled.get()) {
                return;
            }

            if (initialRound) {
                sendStatusTimed(emitter, contractId, "analyzing_contract", "正在生成结构化风险分析", sessionStartNs, phaseStartNs);
                if (cancelled.get()) {
                    return;
                }
                latestResult = generateAndSaveStructuredResultWithRetry(
                        contract,
                        retrievalBundle.retrievedContext(),
                        retrievalBundle.graphContext(),
                        () -> {
                            try {
                                sendStatusTimed(emitter, contractId, "retrying_analysis", "结构化结果解析失败，正在重试生成更精简的结果", sessionStartNs, phaseStartNs);
                            } catch (IOException ignored) {
                            }
                        }
                );
                String answer = buildInitialAnswer(latestResult);
                analysisChatSessionService.appendUserMessage(contractId, normalizedMessage);
                analysisChatSessionService.appendAssistantMessage(contractId, answer);
                recordPhaseDuration("analyzing_contract", phaseStartNs, phaseDurationsMs);
                sendStatusTimed(emitter, contractId, "streaming_answer", "正在分段返回分析结论", sessionStartNs, phaseStartNs);
                streamAnswer(emitter, contractId, answer);
                recordPhaseDuration("streaming_answer", phaseStartNs, phaseDurationsMs);
                sendDoneTimed(emitter, contractId, "initial_analysis", true, latestResult, sessionStartNs, phaseDurationsMs);
            } else {
                sendStatusTimed(emitter, contractId, "generating_answer", "正在结合合同与历史会话生成追问回答", sessionStartNs, phaseStartNs);
                if (cancelled.get()) {
                    return;
                }
                String conversationHistory = analysisChatSessionService.buildPromptHistory(contractId);

                StringBuilder fullAnswer = new StringBuilder();
                StringBuilder pendingDelta = new StringBuilder();
                long[] lastFlushAt = new long[]{System.nanoTime()};
                int[] seq = new int[]{0};
                boolean[] startedStreaming = new boolean[]{false};

                java.util.function.BiConsumer<Boolean, Boolean> flush = (isLast, force) -> {
                    if (pendingDelta.length() == 0 && !isLast) return;
                    if (!force) {
                        long now = System.nanoTime();
                        long elapsedMs = (now - lastFlushAt[0]) / 1_000_000;
                        if (elapsedMs < 80 && pendingDelta.length() < 64) {
                            return;
                        }
                        lastFlushAt[0] = now;
                    }
                    String delta = pendingDelta.toString();
                    pendingDelta.setLength(0);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("contractId", contractId);
                    payload.put("seq", seq[0]++);
                    payload.put("delta", delta);
                    payload.put("isLast", isLast);
                    try {
                        sendEvent(emitter, "answer", payload);
                    } catch (IOException ioException) {
                        cancelled.set(true);
                        throw new IllegalStateException(ioException.getMessage(), ioException);
                    }
                };

                recordPhaseDuration("generating_answer", phaseStartNs, phaseDurationsMs);
                sendStatusTimed(emitter, contractId, "streaming_answer", "正在实时生成追问回答", sessionStartNs, phaseStartNs);
                String answer = streamingFollowUpAnswerer.streamAnswer(
                        contract.getContent(),
                        retrievalBundle.retrievedContext(),
                        retrievalBundle.graphContext(),
                        conversationHistory,
                        normalizedMessage,
                        token -> {
                            if (cancelled.get()) {
                                throw new IllegalStateException("Cancelled");
                            }
                            if (!startedStreaming[0]) {
                                startedStreaming[0] = true;
                            }
                            fullAnswer.append(token);
                            pendingDelta.append(token);
                            flush.accept(false, false);
                        },
                        cancelled::get
                );

                if (cancelled.get()) {
                    return;
                }
                if (fullAnswer.length() == 0 && StringUtils.hasText(answer)) {
                    fullAnswer.append(answer);
                    pendingDelta.append(answer);
                }
                flush.accept(true, true);
                analysisChatSessionService.appendUserMessage(contractId, normalizedMessage);
                analysisChatSessionService.appendAssistantMessage(contractId, fullAnswer.toString());
                recordPhaseDuration("streaming_answer", phaseStartNs, phaseDurationsMs);
                sendDoneTimed(emitter, contractId, "follow_up", false, latestResult, sessionStartNs, phaseDurationsMs);
            }

            emitter.complete();
        } catch (Exception ex) {
            if (cancelled.get() || "Cancelled".equals(ex.getMessage())) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
                return;
            }
            log.error("Stream analysis failed for contractId={}", contractId, ex);
            sendError(emitter, contractId, toUserFacingError(ex));
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(true);
            }
            if (cancelled.get()) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Contract getOwnedContract(Long contractId, String username) {
        return contractRepository.findByIdAndUserUsername(contractId, username)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
    }

    private String toUserFacingError(Throwable ex) {
        if (ex instanceof JsonProcessingException) {
            return "结构化结果解析失败（可能是模型输出被截断）。已自动重试一次，仍失败。请点击“重试”或稍后再试。";
        }
        Throwable cause = ex != null ? ex.getCause() : null;
        if (cause instanceof JsonProcessingException) {
            return "结构化结果解析失败（可能是模型输出被截断）。已自动重试一次，仍失败。请点击“重试”或稍后再试。";
        }
        String message = ex != null ? ex.getMessage() : null;
        if (message != null && (message.contains("Unexpected end-of-input") || message.contains("JsonParseException"))) {
            return "结构化结果解析失败（可能是模型输出被截断）。已自动重试生成更精简/最小结果，仍失败。请点击“重试”或稍后再试。";
        }
        if (message != null && message.toLowerCase().contains("timeout")) {
            return "模型调用超时。请稍后重试，或在设置中提高超时时间/缩短输出规模。";
        }
        return message;
    }

    private void ensureContractHash(Contract contract) {
        if (contract == null) {
            return;
        }
        String existing = contract.getContentHash();
        if (StringUtils.hasText(existing)) {
            return;
        }
        String computed = ContentHashUtil.sha256HexNormalized(contract.getContent());
        if (StringUtils.hasText(computed)) {
            contract.setContentHash(computed);
            contractRepository.save(contract);
        }
    }

    private boolean isStale(Contract contract, AnalysisResult result) {
        if (contract == null || result == null) {
            return true;
        }
        String contractHash = contract.getContentHash();
        String resultHash = result.getContractContentHash();
        if (!StringUtils.hasText(contractHash) || !StringUtils.hasText(resultHash)) {
            return true;
        }
        return !contractHash.equals(resultHash);
    }

    private RetrievalBundle resolveRetrievedContext(
            SseEmitter emitter,
            Contract contract,
            AnalysisResult latestResult,
            boolean forceRefresh,
            Long contractId,
            long sessionStartNs,
            Map<String, Long> phaseStartNs,
            Map<String, Long> phaseDurationsMs
    ) throws IOException {
        if (!forceRefresh
                && latestResult != null
                && !isStale(contract, latestResult)
                && StringUtils.hasText(latestResult.getRetrievedContext())
                && latestResult.getGraphContext() != null) {
            sendStatusTimed(emitter, contract.getId(), "context_ready", "已复用最近一次检索上下文", sessionStartNs, phaseStartNs);
            return new RetrievalBundle(latestResult.getRetrievedContext(), latestResult.getGraphContext());
        }

        sendStatusTimed(emitter, contract.getId(), "retrieving_context", "正在检索相关法律知识", sessionStartNs, phaseStartNs);
        RetrievalBundle retrievalBundle = retrieveContext(contract.getContent());
        recordPhaseDuration("retrieving_context", phaseStartNs, phaseDurationsMs);
        sendStatusTimed(emitter, contract.getId(), "context_ready", "相关法律知识检索完成", sessionStartNs, phaseStartNs);
        if (!forceRefresh && latestResult != null) {
            latestResult.setRetrievedContext(retrievalBundle.retrievedContext());
            latestResult.setGraphContext(retrievalBundle.graphContext());
            latestResult.setContractContentHash(contract.getContentHash());
            analysisResultRepository.save(latestResult);
        }
        return retrievalBundle;
    }

    private RetrievalBundle retrieveContext(String contractContent) {
        if (ragProperties.getMode() == RagMode.LIGHTRAG) {
            LightRagQueryResult result = lightRagClient.query(contractContent);
            if (result.ok()) {
                String graphContext = "（LightRAG：未提供 Neo4j 图谱上下文）";
                return new RetrievalBundle(truncateText(result.context(), 6000), graphContext);
            }
            if (!ragProperties.isFallbackToLegacy()) {
                String error = result.error() != null ? result.error() : "LightRAG query failed";
                throw new IllegalStateException(error);
            }
            log.warn("LightRAG query failed, fallback to legacy retriever, error={}", result.error());
        }
        List<TextSegment> relevantSegments = retriever.findRelevant(contractContent);
        String retrievedContext = buildRetrievedContext(relevantSegments, 800, 6000);
        GraphContextService.GraphContextResult graphContextResult = graphContextService.buildGraphContext(relevantSegments);
        return new RetrievalBundle(retrievedContext, truncateText(graphContextResult.graphContext(), 3000));
    }

    private AnalysisResult generateAndSaveStructuredResult(Contract contract, String retrievedContext, String graphContext) throws IOException {
        return generateAndSaveStructuredResultWithRetry(contract, retrievedContext, graphContext, null);
    }

    private AnalysisResult generateAndSaveStructuredResultWithRetry(
            Contract contract,
            String retrievedContext,
            String graphContext,
            Runnable onRetryConcise
    ) throws IOException {
        String initialRetrievedContext = truncateText(retrievedContext, 6000);
        String initialGraphContext = truncateText(graphContext, 3000);
        String jsonResponse = analyst.analyzeContract(contract.getContent(), initialRetrievedContext, initialGraphContext);
        try {
            AnalysisResult result = mapJsonToAnalysisResult(contract, initialRetrievedContext, initialGraphContext, jsonResponse);
            return analysisResultRepository.save(result);
        } catch (JsonProcessingException ex) {
            if (onRetryConcise != null) {
                onRetryConcise.run();
            }
            String retryRetrievedContext = truncateText(initialRetrievedContext, 3000);
            String retryGraphContext = truncateText(initialGraphContext, 1000);
            String retryJson = analyst.analyzeContractConcise(contract.getContent(), retryRetrievedContext, retryGraphContext);
            AnalysisResult result = mapJsonToAnalysisResult(contract, retryRetrievedContext, retryGraphContext, retryJson);
            return analysisResultRepository.save(result);
        }
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
        result.setContractContentHash(contract.getContentHash());
        result.setSummary(readText(root, "summary"));
        result.setRiskLevel(normalizeRiskLevel(defaultText(readText(root, "risk_level"), "中")));
        result.setRiskScore(readInteger(root, "risk_score"));
        result.setPartyLessorRisks(readObjectArrayJsonStrict(root, "party_lessor_risks", "[]"));
        result.setPartyTenantRisks(readObjectArrayJsonStrict(root, "party_tenant_risks", "[]"));
        result.setSuggestions(sanitizeSuggestionsJson(root));
        result.setContractTags(readJson(root, "contract_tags", "[]"));
        result.setRetrievedContext(retrievedContext);
        result.setGraphContext(graphContext);
        return result;
    }

    private String sanitizeSuggestionsJson(JsonNode root) throws JsonProcessingException {
        if (root == null) {
            return "[]";
        }
        JsonNode value = root.path("suggestions");
        if (value.isMissingNode() || value.isNull() || !value.isArray()) {
            return "[]";
        }
        ArrayNode sanitized = objectMapper.createArrayNode();
        for (JsonNode node : value) {
            if (node == null || node.isNull() || node.isMissingNode()) {
                continue;
            }
            String text;
            if (node.isTextual()) {
                text = node.asText();
            } else {
                text = firstNonBlank(readText(node, "suggested_text"), readText(node, "reason"), readText(node, "suggestion"));
            }
            if (!StringUtils.hasText(text)) {
                continue;
            }
            sanitized.add(text.trim());
        }
        return objectMapper.writeValueAsString(sanitized);
    }

    private String firstNonBlank(String... items) {
        if (items == null) {
            return null;
        }
        for (String item : items) {
            if (StringUtils.hasText(item)) {
                return item;
            }
        }
        return null;
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
        builder.append("建议优先处理事项：\n");

        int index = 1;
        for (JsonNode suggestionNode : suggestionNodes) {
            if (suggestionNode == null || suggestionNode.isNull() || suggestionNode.isMissingNode()) {
                continue;
            }
            String itemText;
            String originalText = null;
            if (suggestionNode.isTextual()) {
                itemText = suggestionNode.asText();
            } else {
                itemText = firstNonBlank(readText(suggestionNode, "suggested_text"), readText(suggestionNode, "reason"), readText(suggestionNode, "suggestion"));
                originalText = readText(suggestionNode, "original_text");
            }

            if (!StringUtils.hasText(itemText)) {
                continue;
            }
            builder.append(index++).append(". ").append(itemText.trim());
            if (StringUtils.hasText(originalText)) {
                builder.append("\n原条款：").append(originalText);
            }
            builder.append("\n");
        }

        if (index == 1) {
            builder.append("暂无建议优先处理事项\n\n");
            return;
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

    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, Long contractId, AtomicBoolean cancelled) {
        long intervalMs = sseProperties != null ? sseProperties.heartbeatIntervalMs() : 0L;
        if (intervalMs <= 0) {
            return null;
        }
        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (cancelled.get()) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("contractId", contractId);
            payload.put("ts", System.currentTimeMillis());
            try {
                sendEvent(emitter, "ping", payload);
            } catch (IOException ex) {
                cancelled.set(true);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void sendStatusTimed(SseEmitter emitter, Long contractId, String phase, String message, long sessionStartNs, Map<String, Long> phaseStartNs) throws IOException {
        long nowNs = System.nanoTime();
        phaseStartNs.putIfAbsent(phase, nowNs);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("phase", phase);
        payload.put("message", message);
        payload.put("elapsedMs", (nowNs - sessionStartNs) / 1_000_000);
        payload.put("phaseElapsedMs", (nowNs - phaseStartNs.get(phase)) / 1_000_000);
        payload.put("sessionMessageCount", analysisChatSessionService.getMessageCount(contractId));
        sendEvent(emitter, "status", payload);
    }

    private void recordPhaseDuration(String phase, Map<String, Long> phaseStartNs, Map<String, Long> phaseDurationsMs) {
        if (phase == null) {
            return;
        }
        Long startNs = phaseStartNs.get(phase);
        if (startNs == null) {
            return;
        }
        phaseDurationsMs.putIfAbsent(phase, (System.nanoTime() - startNs) / 1_000_000);
    }

    private void sendDoneTimed(
            SseEmitter emitter,
            Long contractId,
            String roundType,
            boolean structuredResultUpdated,
            AnalysisResult analysisResult,
            long sessionStartNs,
            Map<String, Long> phaseDurationsMs
    ) throws IOException {
        long nowNs = System.nanoTime();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("roundType", roundType);
        payload.put("structuredResultUpdated", structuredResultUpdated);
        payload.put("sessionMessageCount", analysisChatSessionService.getMessageCount(contractId));
        payload.put("totalElapsedMs", (nowNs - sessionStartNs) / 1_000_000);
        payload.put("phaseDurationsMs", phaseDurationsMs);
        payload.put("analysisResult", AnalysisResultPayload.from(analysisResult));
        sendEvent(emitter, "done", payload);
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
            emitter.complete();
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

    private String normalizeRiskLevel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "中";
        }
        String text = raw.trim();
        if (text.contains("中") && (text.contains("高") || text.contains("低"))) {
            return "中";
        }
        if (text.contains("高")) {
            return "高";
        }
        if (text.contains("低")) {
            return "低";
        }
        return "中";
    }

    private String buildRetrievedContext(List<TextSegment> segments, int maxSegmentChars, int maxTotalChars) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (TextSegment segment : segments) {
            if (segment == null) {
                continue;
            }
            String text = segment.text();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String snippet = text.length() > maxSegmentChars ? text.substring(0, maxSegmentChars) : text;
            if (builder.length() > 0) {
                builder.append("\n\n---\n\n");
            }
            if (builder.length() + snippet.length() > maxTotalChars) {
                int remaining = maxTotalChars - builder.length();
                if (remaining > 0) {
                    builder.append(snippet, 0, Math.min(remaining, snippet.length()));
                }
                break;
            }
            builder.append(snippet);
        }
        return builder.toString();
    }

    private String readObjectArrayJsonStrict(JsonNode root, String fieldName, String defaultValue) throws JsonProcessingException {
        if (root == null) {
            return defaultValue;
        }
        JsonNode value = root.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        if (!value.isArray()) {
            throw new JsonProcessingException(fieldName + " must be an array") {
            };
        }
        for (JsonNode item : value) {
            if (item == null || item.isNull() || item.isMissingNode()) {
                continue;
            }
            if (!item.isObject()) {
                throw new JsonProcessingException(fieldName + " must be an array of objects") {
                };
            }
        }
        return objectMapper.writeValueAsString(value);
    }

    private String truncateText(String text, int maxChars) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        if (maxChars <= 0) {
            return "";
        }
        String s = text.trim();
        return s.length() <= maxChars ? s : s.substring(0, maxChars);
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text : fallback;
    }

    private record RetrievalBundle(String retrievedContext, String graphContext) {
    }
}
