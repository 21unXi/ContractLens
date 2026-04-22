package com.contractlens.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Neo4jProperties.class)
public class Neo4jDriverConfig {

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(Neo4jProperties properties) {
        String uri = blankToNull(properties.uri());
        if (uri == null) {
            throw new IllegalStateException("neo4j.uri is required");
        }

        String username = blankToNull(properties.username());
        String password = properties.password() == null ? "" : properties.password();

        Config config = Config.builder().build();
        if (username == null) {
            return GraphDatabase.driver(uri, AuthTokens.none(), config);
        }
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

