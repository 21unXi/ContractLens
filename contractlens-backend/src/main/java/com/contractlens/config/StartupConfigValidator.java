package com.contractlens.config;

import com.contractlens.rag.RagMode;
import com.contractlens.rag.RagProperties;
import com.contractlens.service.lightrag.LightRagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class StartupConfigValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    private final String jwtSecret;
    private final String chatApiKey;
    private final String embeddingApiKey;
    private final RagProperties ragProperties;
    private final LightRagProperties lightRagProperties;

    public StartupConfigValidator(
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${langchain4j.open-ai.chat-model.api-key:}") String chatApiKey,
            @Value("${langchain4j.open-ai.embedding-model.api-key:}") String embeddingApiKey,
            RagProperties ragProperties,
            LightRagProperties lightRagProperties
    ) {
        this.jwtSecret = jwtSecret;
        this.chatApiKey = chatApiKey;
        this.embeddingApiKey = embeddingApiKey;
        this.ragProperties = ragProperties;
        this.lightRagProperties = lightRagProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("Missing jwt.secret. Provide it in application-dev.yml (contractlens.dev.jwt.secret) or set it via environment.");
        }
        if (!StringUtils.hasText(chatApiKey)) {
            throw new IllegalStateException("Missing DashScope API key for chat model. Provide it in application-dev.yml (contractlens.dev.dashscope.api-key).");
        }
        if (!StringUtils.hasText(embeddingApiKey)) {
            throw new IllegalStateException("Missing DashScope API key for embedding model. Provide it in application-dev.yml (contractlens.dev.dashscope.api-key).");
        }

        if (ragProperties.getMode() == RagMode.LIGHTRAG) {
            if (!lightRagProperties.isEnabled()) {
                log.warn("LightRAG is selected but contractlens.lightrag.enabled=false");
                return;
            }
            String baseUrl = lightRagProperties.getBaseUrl();
            if (!StringUtils.hasText(baseUrl)) {
                log.warn("LightRAG is selected but contractlens.lightrag.base-url is empty");
            }
            String inputsDir = lightRagProperties.getInputsDir();
            if (!StringUtils.hasText(inputsDir)) {
                log.warn("LightRAG is selected but contractlens.lightrag.inputs-dir is empty");
                return;
            }
            try {
                Path dir = Paths.get(inputsDir.trim());
                Files.createDirectories(dir);
                Path probe = dir.resolve(".contractlens_write_probe");
                Files.writeString(probe, "ok");
                Files.deleteIfExists(probe);
            } catch (Exception ex) {
                log.warn("LightRAG inputs-dir is not writable: {}", ex.getMessage());
            }
        }
    }
}
