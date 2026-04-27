package com.contractlens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contractlens.sse")
public record SseProperties(long timeoutMs, long heartbeatIntervalMs) {
}

