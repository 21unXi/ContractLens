# Plan：路线 A 打通（向量 RAG + Neo4j 图谱检索 + 融合）

## Summary

在不引入外部 HKUDS/LightRAG 服务的前提下，把 ContractLens 现有“向量检索 RAG（Chroma）”升级为“向量检索 + Neo4j 图谱检索 + 融合”的混合检索架构，并打通端到端闭环：

1. `knowledge_docs` 仍作为唯一知识源
2. `/api/knowledge/rebuild` 同时构建 Chroma 向量索引 + Neo4j 图谱索引（基于已有结构化字段）
3. 合同分析（结构化 + SSE 对话）检索阶段同时产出 `retrieved_context`（向量）与 `graph_context`（图谱），并共同参与提示词生成
4. `/api/knowledge/status` 可观测：向量侧与图谱侧都能探测并解释清楚（避免“伪总量”）

## Current State Analysis（基于仓库现状）

- 向量检索链路已存在：`AnalysisService.retrieveContext()` → `retriever.findRelevant(contractContent)`（见 [AnalysisService](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java#L132-L138)）
- 知识库重建已存在但仅向量：`POST /api/knowledge/rebuild` → `KnowledgeService.ingestKnowledgeBase()` → `EmbeddingStoreIngestor.ingest(documents)`（见 [KnowledgeController](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/controller/KnowledgeController.java#L23-L27)、[KnowledgeService](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/KnowledgeService.java#L28-L35)）
- `knowledge_docs` 已包含可用于建图的结构化字段：`doc_id/title/doc_type/content/law_article/risk_type`（见 [KnowledgeService.createMetadata](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/KnowledgeService.java#L37-L49)）
- 分析结果表已预留 `graph_context`：`analysis_results.graph_context`（见 [AnalysisResult](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/entity/AnalysisResult.java#L53-L55)）
- Neo4j 依赖与配置已在项目中出现，但当前代码未使用 Neo4j Driver（pom 有 `neo4j-java-driver`，`application.yml` 有 neo4j 配置；但 Java 代码未找到 Driver 使用点）

## Goals / Success Criteria

- 触发 `/api/knowledge/rebuild` 后：
  - Chroma：`Retriever.findRelevant(...)` 可命中（已有能力）
  - Neo4j：图谱中存在对应 `KnowledgeDoc/LawArticle/RiskType` 节点与关系，且图谱探测查询能返回节点
- 触发一次合同分析（结构化或 SSE）后：
  - `analysis_results.retrieved_context` 非空（已有能力）
  - `analysis_results.graph_context` 非空，且内容可解释（包含命中 docId/title/docType + 关联的 law\_article/risk\_type）
  - LLM 提示词中实际包含图谱上下文（可通过日志/保存的上下文核对）
- `/api/knowledge/status` 不出现误导数字：探测结果明确为“返回条数/节点数”，并带上 topK/阈值/探测 query 等解释字段

## Scope / Non-goals

- ✅ 本次只做“路线 A”：在现有 Java 系统内实现混合检索与建图，不下载/运行 HKUDS/LightRAG
- ✅ 图谱数据源：仅从 `knowledge_docs` 构建（你当前约 200 条）
- ❌ 不做“LLM 自动抽取复杂实体关系”（那是更接近 HKUDS/LightRAG 的 indexing pipeline）
- ❌ 不做 GraphRAG 的社区发现/全局搜索等复杂流程

## Proposed Changes（Implementation Plan）

### 1) Neo4j 接入与最小 Schema（后端）

**新增组件**

- `contractlens-backend/src/main/java/com/contractlens/config/Neo4jConfig.java`
  - 读取 `neo4j.uri/username/password`，创建 Neo4j `Driver` Bean
  - 日志不输出密码等敏感信息
- `contractlens-backend/src/main/java/com/contractlens/service/graph/GraphSchemaService.java`
  - 启动时确保约束存在（幂等）：
    - `(:KnowledgeDoc {docId})` 唯一
    - `(:LawArticle {lawArticle})` 唯一
    - `(:RiskType {riskType})` 唯一

**Schema（最小可用）**

- 节点：
  - `KnowledgeDoc(docId, title, docType, createdAt)`
  - `LawArticle(lawArticle)`
  - `RiskType(riskType)`
- 关系：
  - `(KnowledgeDoc)-[:HAS_LAW_ARTICLE]->(LawArticle)`
  - `(KnowledgeDoc)-[:HAS_RISK_TYPE]->(RiskType)`

### 2) 扩展 `/api/knowledge/rebuild`：向量 + 图谱双索引构建

修改 [KnowledgeService](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/KnowledgeService.java)

- 在 `ingestKnowledgeBase()` 中：
  1. 仍按现有逻辑构造 `Document(content, metadata)` 并 ingest 到 Chroma（保持现有行为）
  2. 新增图谱 ingest：对每条 `KnowledgeDoc` 执行 upsert：
     - MERGE `KnowledgeDoc` 节点（docId 唯一）
     - 若 `law_article` 非空：MERGE `LawArticle` 节点，并 MERGE 关系
     - 若 `risk_type` 非空：MERGE `RiskType` 节点，并 MERGE 关系
- 幂等要求：重复 rebuild 不会产生重复节点/关系
- 额外：为后续状态查询记录最后构建时间（可用 MySQL 表/内存字段/Neo4j graph meta 节点三选一；优先选“Neo4j meta 节点”避免新建 MySQL 表）

### 3) 图谱检索（Graph Retrieval）与融合（RRF / 可解释融合）

**新增服务**

- `contractlens-backend/src/main/java/com/contractlens/service/graph/GraphRetrievalService.java`
  - 输入：合同全文（String）+（可选）向量检索命中的 TextSegment 列表
  - 输出：`GraphRetrievalResult`（包含命中的 KnowledgeDoc 列表、命中原因、graph\_context 文本）

**检索策略（路线 A，轻量可落地）**

1. Seed 抽取（不依赖 LLM）
   - 从向量检索命中片段的 metadata 提取（如果存在）：
     - `law_article`、`risk_type`
   - 从合同正文做简单规则抽取：
     - 法条号：匹配 “第\d+条/民法典\d+” 等模式（仅作为补充）
     - 风险关键词：押金/租金/解约/维修/转租/交付/费用 → 映射到既有 `risk_type`（映射表写在代码中，但只包含租房场景）
2. Neo4j 查询（示例）
   - 按 law\_article 命中：
     - `MATCH (d:KnowledgeDoc)-[:HAS_LAW_ARTICLE]->(l:LawArticle) WHERE l.lawArticle IN $laws RETURN d ...`
   - 按 risk\_type 命中：
     - `MATCH (d:KnowledgeDoc)-[:HAS_RISK_TYPE]->(r:RiskType) WHERE r.riskType IN $risks RETURN d ...`
   - 汇总打分（简单可解释）：law 命中计 2 分，risk 命中计 1 分；按分数排序取 TopN
3. graph\_context 构造
   - 以“可审计/可解释”为目标，不直接塞一整篇 content：
     - `title/docType/docId`
     - `law_article/risk_type`
     - `content` 摘要（前 N 字符，N 可配置）

**融合策略**

- 不做复杂 reranker；采用可解释融合：
  - LLM 上下文 = `retrieved_context`（向量拼接） + `graph_context`（图谱摘要）
  - 两者分别落库到 `analysis_results.retrieved_context` 与 `analysis_results.graph_context`
- 如果需要“融合重排”用于选择 TopN，可用 RRF：
  - 向量侧 rank 与图谱侧 rank 分别计算 RRF 分数，选择 TopN doc/片段进入最终上下文

### 4) 替换分析链路使用混合检索（结构化 + SSE）

修改 [AnalysisService](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java)

- 将 `retrieveContext(String contractContent)` 从“仅向量”改为返回结构体：
  - `retrievedContext`（向量）
  - `graphContext`（图谱）
  - `finalContextForPrompt`（拼接后的最终上下文）
- 修改 `generateAndSaveStructuredResult(...)`：
  - 保存 `retrieved_context` 与 `graph_context`
  - 调用 AI 时传入 `finalContextForPrompt`
- 修改 follow-up 逻辑同样使用混合上下文

必要时修改 `AiContractAnalyst`（提示词层）

- 选项 A（更小改动）：继续只传一个 `retrievedContext` 参数，但实际传入的是“向量+图谱拼接后的 finalContext”
- 选项 B（更清晰）：扩展方法签名为 `(contractContent, retrievedContext, graphContext)` 并在 prompt 中分段呈现
- 本计划默认选 B（更可控、可解释）

### 5) `/api/knowledge/status` 增加图谱侧可观测性（真实且可解释）

修改 [KnowledgeStatusController](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/controller/KnowledgeStatusController.java)

- 保留向量 probe（现有行为），并继续输出解释字段（topK/minScore/probeQuery/returnedSegments）
- 增加图谱侧字段：
  - `graphEnabled`（配置开关）
  - `graphNodeCount`、`graphEdgeCount`（或按 label/type 拆分）
  - `graphProbeQuery`（与向量 probe 可能一致）
  - `graphProbeReturnedDocs`（返回的 KnowledgeDoc 数）
  - `graphProbeError`

前端（可选但推荐）

- `contractlens-frontend/src/views/Knowledge.vue`、`Settings.vue` 增加展示：
  - 图谱节点/关系数
  - 图谱探测返回条数

### 6) 配置项（application.yml）

新增 `contractlens.rag.*` 配置（默认启用图谱检索，符合“直接替换”诉求，同时保留开关便于回滚）

- `contractlens.rag.graph.enabled: true`
- `contractlens.rag.graph.top-n: 10`（默认取 10 条 KnowledgeDoc 参与 graph\_context）
- `contractlens.rag.graph.content-snippet-len: 200`
- `contractlens.rag.retriever.top-k` / `min-score` / `probe-query`（现有基础上继续使用）

## Assumptions & Decisions

- Neo4j 以“本机安装服务”方式运行，连接为默认 `bolt://localhost:7687`，用户名默认 `neo4j`；密码通过你本地配置提供（不写入仓库）
- `knowledge_docs` 中的 `law_article/risk_type` 字段已按租房领域写入，足够支撑最小图谱检索
- 由于本次不引入 LLM 抽取关系，图谱的“关系推理”能力主要来自结构化字段与规则映射；后续如果要更强关系抽取，再单独开 spec
- 图谱不可用时采用“自动降级”：分析仍可继续使用向量检索，但必须在 `status` 与日志中明确标记“已降级（graph disabled by runtime error）”，避免产生“看起来正常但其实没用到图谱”的假象

## Verification Steps（实现后执行）

1. 启动 Chroma 与 Neo4j
2. 向 MySQL `knowledge_docs` 写入/确认约 200 条数据
3. 调用 `POST /api/knowledge/rebuild`
4. 调用 `GET /api/knowledge/status`
   - 向量 probe 返回条数 > 0（在 topK 上限内）
   - 图谱 probe 返回 docs 数 > 0
   - 图谱 node/edge 数为非 0 且无 error
5. 上传合同并触发分析（结构化或 SSE）
   - DB 中 `analysis_results.retrieved_context` 与 `analysis_results.graph_context` 均非空
   - 结果内容与命中 docId/title/law\_article/risk\_type 可对上
6. 回归：Neo4j 停止后，系统返回清晰错误（或按开关禁用图谱后仅向量可用），不出现“看起来正常但其实没用到图谱”的假象

