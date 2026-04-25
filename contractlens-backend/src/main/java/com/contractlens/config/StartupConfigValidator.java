package com.contractlens.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class StartupConfigValidator implements ApplicationRunner {

    private final String jwtSecret;
    private final String chatApiKey;
    private final String embeddingApiKey;

    public StartupConfigValidator(
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${langchain4j.open-ai.chat-model.api-key:}") String chatApiKey,
            @Value("${langchain4j.open-ai.embedding-model.api-key:}") String embeddingApiKey
    ) {
        this.jwtSecret = jwtSecret;
        this.chatApiKey = chatApiKey;
        this.embeddingApiKey = embeddingApiKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("Missing jwt.secret. Set JWT_SECRET env var or provide it in application-local.yml.");
        }
        if (!StringUtils.hasText(chatApiKey)) {
            throw new IllegalStateException("Missing DashScope API key for chat model. Set DASHSCOPE_API_KEY env var or provide it in application-local.yml.");
        }
        if (!StringUtils.hasText(embeddingApiKey)) {
            throw new IllegalStateException("Missing DashScope API key for embedding model. Set DASHSCOPE_API_KEY env var or provide it in application-local.yml.");
        }
    }
}

