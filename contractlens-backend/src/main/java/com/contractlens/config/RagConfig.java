package com.contractlens.config;

import com.contractlens.rag.RagProperties;
import com.contractlens.service.lightrag.LightRagProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RagProperties.class, LightRagProperties.class})
public class RagConfig {
}

