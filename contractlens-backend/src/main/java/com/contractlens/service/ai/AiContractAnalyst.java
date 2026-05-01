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
            8. party_lessor_risks / party_tenant_risks 必须同时覆盖两类风险：合同现有条款风险 + 合同缺失项风险（只要能识别到缺失项就必须输出）；两类风险必须在同一数组内按优先级混排（不得把缺失项统一放在末尾）

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
            - 每个风险对象的 risk_type 必须为非空短标签（2-12字），示例：押金返还、违约金畸高、抵押风险、维修报销、备案义务、占用费过高、物业规约未附、入户隐私风险、不可抗力约定缺失、装修限制缺失
            - risk_type 禁止使用占位词或泛化词：未分类/未分类风险/待确认/未知/其他/其它/不确定/未提供
            - 每个风险对象的 risk_level 必须为非空，且只能是：高 / 中 / 低（不要输出“中风险/中高风险”等）
            - clause_text 最多 200 字，超出必须截断并以省略号结尾
            - risk_description / legal_basis / suggestion 需简洁，避免长段落（优先用分点）
            - party_lessor_risks / party_tenant_risks / contract_tags 同理：允许空数组，但不得输出 null
            - 输出前自检：遍历 party_lessor_risks 与 party_tenant_risks，确保每个对象上述字段齐全且非空
            
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
            9. 每个风险对象必须包含非空 risk_type（2-12字短标签）与 risk_level（高/中/低），不得留空
            10. risk_type 禁止使用占位词：未分类/未分类风险/待确认/未知/其他/其它/不确定/未提供
            11. party_lessor_risks / party_tenant_risks 必须同时覆盖现有条款风险与缺失项风险，且两类风险在同一数组内按优先级混排（不得把缺失项统一放在末尾）

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
            你是一位专业的租房合同法律顾问。你刚刚生成的结构化 JSON 输出仍无法解析（很可能是输出过长被截断）。请生成“最小可用且一定可解析”的结构化结果。

            【硬性约束（必须严格执行）】
            1. 只输出 JSON，不要输出任何解释文字或前后缀，不要使用 Markdown 代码块
            2. 必须输出与原格式一致的 key（summary/risk_score/risk_level/party_lessor_risks/party_tenant_risks/suggestions/contract_tags）
            3. party_lessor_risks / party_tenant_risks：每个数组最多 3 条，按优先级从高到低排序
            4. suggestions：最多 3 条，按优先级从高到低排序，必须是字符串数组，不得包含 null
            5. clause_text 最多 120 字；risk_description/legal_basis/suggestion 每项最多 80 字（超出必须截断并以省略号结尾）
            6. 每个风险对象必须包含非空字段：clause_index/clause_text/risk_type/risk_level/risk_description/legal_basis/suggestion
            7. risk_level 只能是：高 / 中 / 低（不要输出“中风险/中高风险”等）
            8. risk_type 必须为非空短标签（2-12字），不得使用占位词：未分类/未分类风险/待确认/未知/其他/其它/不确定/未提供
            9. party_lessor_risks / party_tenant_risks 必须同时覆盖现有条款风险与缺失项风险，且两类风险在同一数组内按优先级混排（不得把缺失项统一放在末尾）
            10. 对缺失项仍用 clause_index=0 且 clause_text 以“【缺失】”开头
            11. 输出前自检：确保括号闭合、字符串已闭合、JSON 可被解析

            【参考资料】
            ---
            {{retrieved_context}}
            ---

            【图谱上下文】
            ---
            {{graph_context}}
            ---
            """)
    String analyzeContractMinimal(
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
