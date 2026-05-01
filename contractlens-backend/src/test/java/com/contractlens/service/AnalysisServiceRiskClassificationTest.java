package com.contractlens.service;

import com.contractlens.entity.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisServiceRiskClassificationTest {

    @Test
    void marksExistingAndMissingRisksInSingleList() throws Exception {
        AnalysisService service = new AnalysisService();
        Method m = AnalysisService.class.getDeclaredMethod("buildInitialAnswer", AnalysisResult.class);
        m.setAccessible(true);

        AnalysisResult result = new AnalysisResult();
        result.setSummary("s");
        result.setRiskScore(37);
        result.setRiskLevel("中");
        result.setPartyLessorRisks("[]");
        result.setSuggestions("[]");
        result.setContractTags("[]");
        result.setPartyTenantRisks("""
                [
                  {
                    "clause_index": 1.1,
                    "clause_text": "已有条款A",
                    "risk_type": "费用代收风险",
                    "risk_level": "中",
                    "risk_description": "x",
                    "legal_basis": "x",
                    "suggestion": "x"
                  },
                  {
                    "clause_index": 0,
                    "clause_text": "【缺失】未约定交接清单与影像存证",
                    "risk_type": "交接存证缺失",
                    "risk_level": "中",
                    "risk_description": "x",
                    "legal_basis": "x",
                    "suggestion": "x"
                  }
                ]
                """);

        String answer = (String) m.invoke(service, result);
        assertThat(answer).contains("租客视角重点风险：");
        assertThat(answer).contains("1. 【现有】费用代收风险（中）");
        assertThat(answer).contains("2. 【缺失】交接存证缺失（中）");
    }
}

