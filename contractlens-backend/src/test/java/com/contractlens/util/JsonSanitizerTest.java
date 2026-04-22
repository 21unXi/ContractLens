package com.contractlens.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSanitizerTest {

    @Test
    void keepsPlainJson() {
        String raw = "{\"risk_score\":0,\"risk_level\":\"低\"}";
        assertThat(JsonSanitizer.extractJsonObject(raw)).isEqualTo(raw);
    }

    @Test
    void stripsJsonFence() {
        String raw = "```json\n{\n  \"risk_score\": 0,\n  \"risk_level\": \"低\"\n}\n```";
        assertThat(JsonSanitizer.extractJsonObject(raw)).isEqualTo("{\n  \"risk_score\": 0,\n  \"risk_level\": \"低\"\n}");
    }

    @Test
    void stripsFenceWithoutLanguage() {
        String raw = "```\n{\n  \"risk_score\": 0,\n  \"risk_level\": \"低\"\n}\n```";
        assertThat(JsonSanitizer.extractJsonObject(raw)).isEqualTo("{\n  \"risk_score\": 0,\n  \"risk_level\": \"低\"\n}");
    }

    @Test
    void extractsFirstJsonObjectWhenWrapped() {
        String raw = "好的，以下为结果：\n```json\n{\n  \"risk_score\": 0,\n  \"risk_level\": \"低\"\n}\n```\n谢谢。";
        assertThat(JsonSanitizer.extractJsonObject(raw)).isEqualTo("{\n  \"risk_score\": 0,\n  \"risk_level\": \"低\"\n}");
    }
}

