package com.contractlens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "neo4j")
public record Neo4jProperties(String uri, String username, String password) {
}

