# Tasks

- [ ] Task 1: 设计并落地 Neo4j 图谱 schema（最小可用）
  - [ ] 定义节点：KnowledgeDoc / LawArticle / RiskType 的属性与唯一约束（docId/lawArticle/riskType）
  - [ ] 定义关系：HAS_LAW_ARTICLE / HAS_RISK_TYPE
  - [ ] 规划索引（用于按 docId / lawArticle / riskType 查询）

- [ ] Task 2: 后端接入 Neo4j Driver 与配置
  - [ ] 建立 Neo4j 连接配置（读取 `application.yml` 的 neo4j.uri/username/password）
  - [ ] 提供图检索与建图的基础访问层（repository/service）
  - [ ] 明确连接失败时的错误输出与可观测日志（不打印敏感信息）

- [ ] Task 3: 扩展知识库 rebuild：向量 + 图谱双索引构建
  - [ ] 复用现有 `KnowledgeService.ingestKnowledgeBase()` 的数据读取（knowledge_docs）
  - [ ] 在 rebuild 流程中增加“图谱 upsert”步骤（节点/关系）
  - [ ] 定义 rebuild 的幂等语义（重复调用不产生重复节点/边）
  - [ ] 增加图谱构建后的统计信息（节点/关系数量、最近构建时间）

- [ ] Task 4: 实现 LightRAG 风格的混合检索与融合（RRF）
  - [ ] 向量侧：继续使用现有 Retriever（Chroma）
  - [ ] 图谱侧：实现 query → seed（按关键词/法条号/风险类型匹配）→ 关系扩展 → 候选知识文档/片段
  - [ ] 融合：实现 RRF（或等价可解释融合）并产出最终 TopN 上下文
  - [ ] 输出：分别生成 `retrieved_context`（向量）与 `graph_context`（图谱解释/摘要）

- [ ] Task 5: 替换分析链路使用混合检索（结构化 + SSE 对话）
  - [ ] 修改 `AnalysisService.retrieveContext()`：由单路向量检索改为混合检索
  - [ ] 确保分析结果落库包含 `graph_context`
  - [ ] 确保 SSE done payload（如有）与 DB 结果一致

- [ ] Task 6: 扩展 /api/knowledge/status：返回“真实且可解释”的探测信息
  - [ ] 增加字段：probeQuery、retrieverTopK、retrieverMinScore、returnedSegments
  - [ ] 增加图谱侧指标：graphNodeCount、graphEdgeCount（或等价），graphProbeReturnedNodes
  - [ ] 前端展示文案改为“返回条数（topK/阈值）”，避免误解

- [ ] Task 7: 测试与验收
  - [ ] 单元测试：图谱 upsert 幂等、RRF 融合排序的可预期性
  - [ ] 集成验证：在 Neo4j/Chroma 可用且 knowledge_docs≈200 的情况下，rebuild 后 probe 有命中且分析结果 graph_context 非空

- [ ] Task 8: 文档更新
  - [ ] 更新 PROGRESS.md：说明混合检索语义、status 指标含义、Neo4j 依赖与启动方式

# Task Dependencies
- Task 3 depends on Task 1, Task 2
- Task 4 depends on Task 1, Task 2, Task 3
- Task 5 depends on Task 4
- Task 6 depends on Task 2, Task 4
- Task 7 depends on Task 3, Task 5, Task 6
- Task 8 depends on Task 5, Task 6

