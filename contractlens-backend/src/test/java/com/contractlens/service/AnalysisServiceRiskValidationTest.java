package com.contractlens.service;

import com.contractlens.entity.AnalysisResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisServiceRiskValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsPlaceholderRiskType() throws Exception {
        AnalysisService service = new AnalysisService();
        Method m = AnalysisService.class.getDeclaredMethod("readRiskArrayJsonStrict", JsonNode.class, String.class, String.class);
        m.setAccessible(true);

        JsonNode root = objectMapper.readTree("""
                {
                  "party_tenant_risks": [
                    {
                      "clause_index": 1,
                      "clause_text": "x",
                      "risk_type": "未分类风险",
                      "risk_level": "中",
                      "risk_description": "x",
                      "legal_basis": "x",
                      "suggestion": "x"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> m.invoke(service, root, "party_tenant_risks", "[]"))
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsMissingRiskLevel() throws Exception {
        AnalysisService service = new AnalysisService();
        Method m = AnalysisService.class.getDeclaredMethod("readRiskArrayJsonStrict", JsonNode.class, String.class, String.class);
        m.setAccessible(true);

        JsonNode root = objectMapper.readTree("""
                {
                  "party_tenant_risks": [
                    {
                      "clause_index": 1,
                      "clause_text": "x",
                      "risk_type": "押金返还",
                      "risk_description": "x",
                      "legal_basis": "x",
                      "suggestion": "x"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> m.invoke(service, root, "party_tenant_risks", "[]"))
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsPlaceholderRiskLevel() throws Exception {
        AnalysisService service = new AnalysisService();
        Method m = AnalysisService.class.getDeclaredMethod("readRiskArrayJsonStrict", JsonNode.class, String.class, String.class);
        m.setAccessible(true);

        JsonNode root = objectMapper.readTree("""
                {
                  "party_tenant_risks": [
                    {
                      "clause_index": 1,
                      "clause_text": "x",
                      "risk_type": "押金返还",
                      "risk_level": "待确认",
                      "risk_description": "x",
                      "legal_basis": "x",
                      "suggestion": "x"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> m.invoke(service, root, "party_tenant_risks", "[]"))
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void buildInitialAnswerSkipsRiskItemsWithMissingTypeOrLevel() throws Exception {
        AnalysisService service = new AnalysisService();
        Method m = AnalysisService.class.getDeclaredMethod("buildInitialAnswer", AnalysisResult.class);
        m.setAccessible(true);

        AnalysisResult result = new AnalysisResult();
        result.setSummary("s");
        result.setRiskScore(1);
        result.setRiskLevel("中");
        result.setPartyTenantRisks("""
                [
                  {
                    "clause_index": 1,
                    "clause_text": "x",
                    "risk_level": "中",
                    "risk_description": "x",
                    "legal_basis": "x",
                    "suggestion": "x"
                  }
                ]
                """);
        result.setPartyLessorRisks("[]");
        result.setSuggestions("[]");
        result.setContractTags("[]");

        String answer = (String) m.invoke(service, result);
        assertThat(answer).doesNotContain("未分类风险").doesNotContain("租客视角重点风险");
    }
}

