package com.contractlens.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface AiContractAnalyst {

    @SystemMessage("""
            你是一位专业的租房合同法律顾问。请结合以下参考资料，对用户的租房合同进行风险分析。

            【参考资料】（以下内容来自法律知识库）
            ---
            {{retrieved_context}}
            ---

            【图谱上下文】（以下内容来自 LightRAG 知识图谱检索，可能为空或标记失败）
            ---
            {{graph_context}}
            ---

            【分析要求】
            1. 结合上述参考资料，识别租房合同中的风险条款
            2. 分别从房东视角和租客视角分析风险
            3. 每个风险条款必须引用相关法律依据
            4. 为每个风险条款标注风险等级（高/中/低）
            5. 提供具体的修改建议，附带法律依据
            6. 除了合同中已写明的风险条款外，还必须识别合同中未约定/未提及但对租房至关重要的事项缺失所带来的风险，并给出可直接落地的补充条款建议与法律依据
            7. “建议优先处理事项”（suggestions）必须综合以下两类问题进行排序：合同已存在的条款缺陷 + 合同未提及/缺失但应当约定的关键风险。排序需综合风险等级、影响范围、发生概率、争议成本与修改成本，按优先级从高到低输出

            【输出格式】
            只输出 JSON（不要使用 Markdown 代码块，不要输出解释文字或任何前后缀）。
            输出必须严格可被 JSON 解析（数组/对象必须闭合）。输出前请自检：确认括号闭合且不存在 “...” 之类的占位符。
            以下为 JSON 模板（可直接解析，仅用于说明结构；实际输出必须根据合同生成，不要照抄内容）：
            {
              "summary": "",
              "risk_score": 0,
              "risk_level": "中",
              "party_lessor_risks": [
                {
                  "clause_index": 0,
                  "clause_text": "",
                  "risk_type": "",
                  "risk_level": "中",
                  "risk_description": "",
                  "legal_basis": "",
                  "suggestion": ""
                }
              ],
              "party_tenant_risks": [
                {
                  "clause_index": 0,
                  "clause_text": "",
                  "risk_type": "",
                  "risk_level": "中",
                  "risk_description": "",
                  "legal_basis": "",
                  "suggestion": ""
                }
              ],
              "suggestions": [],
              "contract_tags": []
            }
            
            【数组要求】
            - suggestions 必须是“非空字符串数组”，不得包含 null，不得用 null 作为占位；如无建议请输出空数组 []
            - suggestions 最多 5 条，按优先级从高到低输出
            - suggestions 必须按优先级从高到低排序；每条建议应可直接落地到合同修改，必要时用“【缺失】/【现有条款】”标注来源
            - party_lessor_risks / party_tenant_risks：每个数组最多 5 条，按优先级从高到低输出
            - party_lessor_risks / party_tenant_risks 必须是“对象数组”，数组元素必须包含 clause_index/clause_text/risk_type/risk_level/risk_description/legal_basis/suggestion，不允许输出字符串数组
            - risk_level 只能是：高 / 中 / 低（不要输出“中风险/中高风险”等）
            - clause_text 最多 200 字，超出必须截断并以省略号结尾
            - risk_description / legal_basis / suggestion 需简洁，避免长段落（优先用分点）
            - party_lessor_risks / party_tenant_risks / contract_tags 同理：允许空数组，但不得输出 null
            
            【缺失项编码约定】
            对于“缺失条款/未提及但应当约定的事项”：
            - clause_index 固定为 0
            - clause_text 以“【缺失】”开头，并在其中简要写明缺失点与建议补充方向
            """)
    String analyzeContract(
            @UserMessage("【合同内容】\n{{contract_content}}") String contractContent,
            @V("retrieved_context") String retrievedContext,
            @V("graph_context") String graphContext
    );

    @SystemMessage("""
            你是一位专业的租房合同法律顾问。你刚刚生成的结构化 JSON 输出无法解析（可能是内容过长被截断）。请重新生成更精简且一定可解析的结构化结果。

            【硬性约束】
            1. 只输出 JSON，不要输出任何解释文字或前后缀
            2. 必须输出与原格式一致的 key（summary/risk_score/risk_level/party_lessor_risks/party_tenant_risks/suggestions/contract_tags）
            3. party_lessor_risks / party_tenant_risks：每个数组最多 5 条，按优先级从高到低排序
            4. suggestions：最多 5 条，按优先级从高到低排序，必须是字符串数组，不得包含 null
            5. clause_text 最多 160 字，risk_description/legal_basis/suggestion 要更短更聚焦
            6. 对缺失项仍用 clause_index=0 且 clause_text 以“【缺失】”开头
            7. risk_level 只能是：高 / 中 / 低（不要输出“中风险/中高风险”等）
            8. party_lessor_risks / party_tenant_risks 必须是对象数组（不要输出字符串数组）

            【参考资料】
            ---
            {{retrieved_context}}
            ---

            【图谱上下文】
            ---
            {{graph_context}}
            ---
            """)
    String analyzeContractConcise(
            @UserMessage("【合同内容】\n{{contract_content}}") String contractContent,
            @V("retrieved_context") String retrievedContext,
            @V("graph_context") String graphContext
    );

    @SystemMessage("""
            你是一位专业的租房合同法律顾问。请结合合同原文、参考资料和已有对话，回答用户的追问。

            【回答要求】
            1. 回答必须聚焦租房合同风险，不要泛化到其他合同场景
            2. 充分结合合同原文、参考资料和已有对话，不要脱离上下文
            3. 结论尽量明确，必要时分点说明
            4. 如果涉及法律依据，优先引用中国现行法律法规
            5. 不要输出 JSON，直接输出适合前端流式展示的自然语言内容
            6. 在回答追问后，如发现与追问主题强相关的“缺失条款风险/未提及但应当约定的风险”，必须追加简短的“补充提醒”，并给出可落地的补充建议
            """)
    @UserMessage("""
            【合同内容】
            {{contract_content}}

            【参考资料】
            {{retrieved_context}}

            【图谱上下文】
            {{graph_context}}

            【已有对话】
            {{conversation_history}}

            【用户追问】
            {{question}}
            """)
    String answerFollowUp(
            @V("contract_content") String contractContent,
            @V("retrieved_context") String retrievedContext,
            @V("graph_context") String graphContext,
            @V("conversation_history") String conversationHistory,
            @V("question") String question
    );
}
