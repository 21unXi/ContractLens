package com.contractlens.service.lightrag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
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
        this.restClient = restClientBuilder
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
                return LightRagQueryResult.ok("", null, null);
            }
            LightRagQueryResult parsed = parseQueryResponse(body);
            if (parsed != null) {
                return parsed;
            }
            return LightRagQueryResult.ok(body, null, body);
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String detail = StringUtils.hasText(responseBody) ? responseBody : ex.getMessage();
            return LightRagQueryResult.error("HTTP " + ex.getRawStatusCode() + ": " + detail);
        } catch (Exception ex) {
            return LightRagQueryResult.error(ex.getMessage());
        }
    }

    private LightRagQueryResult parseQueryResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isTextual()) {
                return LightRagQueryResult.ok(root.asText(), null, body);
            }
            String context = firstText(root, "response", "context", "retrieved_context", "retrieval_context", "prompt", "data");
            Integer chunkCount = firstArraySize(root, "chunks", "chunk_list", "contexts", "context_list", "retrieved_chunks");
            String answer = firstText(root, "answer", "response", "result", "output");
            if (!StringUtils.hasText(context)) {
                context = answer;
            }
            return LightRagQueryResult.ok(StringUtils.hasText(context) ? context : "", chunkCount, body);
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
