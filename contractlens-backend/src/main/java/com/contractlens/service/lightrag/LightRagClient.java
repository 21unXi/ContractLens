package com.contractlens.service.lightrag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LightRagClient {

    private final LightRagProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LightRagClient(LightRagProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String baseUrl = properties.getBaseUrl();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(10000);
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl.trim() : null)
                .build();
    }

    public LightRagQueryResult query(String query) {
        if (!properties.isEnabled()) {
            return LightRagQueryResult.disabled();
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            return LightRagQueryResult.error("contractlens.lightrag.base-url 未配置");
        }
        if (!StringUtils.hasText(query)) {
            return LightRagQueryResult.ok("", null, null);
        }

        String path = StringUtils.hasText(properties.getQueryPath()) ? properties.getQueryPath().trim() : "/query";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("mode", properties.getQueryMode());
        payload.put("only_need_context", properties.isOnlyNeedContext());

        long startNs = System.nanoTime();
        try {
            String json = objectMapper.writeValueAsString(payload);
            ResponseEntity<String> entity = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toEntity(String.class);
            String body = entity.getBody();
            if (!StringUtils.hasText(body)) {
                long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
                return LightRagQueryResult.ok("", null, latencyMs, null);
            }
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            LightRagQueryResult parsed = parseQueryResponse(body, latencyMs);
            if (parsed != null) {
                return parsed;
            }
            return LightRagQueryResult.ok(body, null, latencyMs, body);
        } catch (RestClientResponseException ex) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            String responseBody = ex.getResponseBodyAsString();
            String detail = StringUtils.hasText(responseBody) ? responseBody : ex.getMessage();
            return LightRagQueryResult.error("HTTP " + ex.getRawStatusCode() + ": " + detail, latencyMs);
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            return LightRagQueryResult.error(ex.getMessage(), latencyMs);
        }
    }

    private LightRagQueryResult parseQueryResponse(String body, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isTextual()) {
                return LightRagQueryResult.ok(root.asText(), null, latencyMs, body);
            }
            String context = firstText(root, "response", "context", "retrieved_context", "retrieval_context", "prompt", "data");
            Integer chunkCount = firstArraySize(root, "chunks", "chunk_list", "contexts", "context_list", "retrieved_chunks");
            String answer = firstText(root, "answer", "response", "result", "output");
            if (!StringUtils.hasText(context)) {
                context = answer;
            }
            return LightRagQueryResult.ok(StringUtils.hasText(context) ? context : "", chunkCount, latencyMs, body);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String firstText(JsonNode root, String... keys) {
        if (root == null) return null;
        for (String key : keys) {
            JsonNode node = root.get(key);
            if (node == null || node.isNull()) continue;
            if (node.isTextual()) return node.asText();
            if (node.isObject() || node.isArray()) return node.toString();
            return node.asText();
        }
        return null;
    }

    private static Integer firstArraySize(JsonNode root, String... keys) {
        if (root == null) return null;
        for (String key : keys) {
            JsonNode node = root.get(key);
            if (node == null || node.isNull()) continue;
            if (node.isArray()) return node.size();
        }
        return null;
    }
}
