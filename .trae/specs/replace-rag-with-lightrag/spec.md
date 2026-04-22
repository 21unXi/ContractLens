# LightRAG（向量 + 知识图谱）替换现有 RAG Spec

## Why
当前系统的检索增强主要依赖向量检索（Chroma），对“关系型问题”（条款冲突、权责链路、条款→法条→风险类型的推理路径）支持有限。引入 LightRAG 风格的“向量检索 + 图谱检索 + 融合”可以提升可解释性与覆盖面，并为后续 GraphRAG 能力打基础。

## What Changes
- **替换**分析链路的检索模块：从“仅向量检索”升级为“向量检索 + Neo4j 图谱检索 + 结果融合（RRF）”
- 扩展知识库重建流程：`/api/knowledge/rebuild` 除向量化 ingest 外，增加图谱索引构建/更新
- 扩展状态可观测性：`/api/knowledge/status` 增加图谱侧指标（节点/关系数、最近构建时间、探测结果与解释字段）
- 扩展分析结果落库：`analysis_results.graph_context` 写入图谱检索上下文（并保留 `retrieved_context` 作为向量检索上下文）
- 保持系统边界：仍只面向“租房合同”知识库与分析，不引入通用合同能力

## Impact
- Affected specs: 知识库重建、检索链路、合同分析（结构化 + SSE 对话）、可观测性
- Affected code（预期）:
  - 后端检索与分析：`contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java`
  - 知识库 ingest：`contractlens-backend/src/main/java/com/contractlens/service/KnowledgeService.java`
  - 知识库状态接口：`contractlens-backend/src/main/java/com/contractlens/controller/KnowledgeStatusController.java`
  - AI 配置/检索 Bean：`contractlens-backend/src/main/java/com/contractlens/config/AiConfig.java`
  - 实体：`contractlens-backend/src/main/java/com/contractlens/entity/AnalysisResult.java`
  - 前端状态展示：`contractlens-frontend/src/views/Knowledge.vue`, `contractlens-frontend/src/views/Settings.vue`

## ADDED Requirements
### Requirement: LightRAG 风格的混合检索
系统 SHALL 在合同分析前执行混合检索：向量检索与图谱检索同时进行，并融合为最终上下文供 LLM 使用。

#### Scenario: 合同分析的混合检索
- **WHEN** 系统开始分析一份租房合同（结构化或 SSE 对话）
- **THEN** 系统应执行向量检索得到一组候选知识片段（TextSegment）
- **AND** 系统应执行 Neo4j 图谱检索得到一组相关实体/文档/法条/风险类型
- **AND** 系统应对两路候选进行融合重排序（RRF 或等价策略）
- **AND** 最终上下文应同时包含：
  - `retrieved_context`（向量侧片段拼接）
  - `graph_context`（图谱侧检索解释与关键节点/关系摘要）

### Requirement: 图谱索引构建（基于 knowledge_docs）
系统 SHALL 从 MySQL `knowledge_docs` 构建/更新 Neo4j 图谱索引，使图谱检索对租房法律条文与风险清单可用。

#### Scenario: rebuild 同步构建向量库与图谱
- **WHEN** 调用 `POST /api/knowledge/rebuild`
- **THEN** 系统应将 `knowledge_docs` ingest 到向量库（Chroma）
- **AND** 系统应将同一批 `knowledge_docs` 构建/更新到 Neo4j 图谱中
- **AND** 构建完成后，`GET /api/knowledge/status` 可反映图谱侧指标已更新

### Requirement: 图谱 Schema（最小可用）
系统 SHALL 定义最小图谱 schema，以支持“法条/风险类型/文档”之间的关系检索。

#### Scenario: 节点与关系
- **THEN** 图谱至少包含以下节点类型：
  - `KnowledgeDoc`（docId/title/docType）
  - `LawArticle`（lawArticle，如“民法典721”）
  - `RiskType`（riskType，如“押金风险”）
- **AND** 至少包含以下关系：
  - `(KnowledgeDoc)-[:HAS_LAW_ARTICLE]->(LawArticle)`
  - `(KnowledgeDoc)-[:HAS_RISK_TYPE]->(RiskType)`

### Requirement: 可观测性与“真实数据”
系统 SHALL 在知识库状态接口中提供可解释、可溯源的检索探测信息，不得使用误导性的“伪总量”指标。

#### Scenario: status 返回探测解释字段
- **WHEN** 调用 `GET /api/knowledge/status`
- **THEN** 响应应包含探测 query、topK 上限、阈值（如有）、以及“本次返回条数”
- **AND** 探测字段的语义必须明确为“返回片段数/返回节点数”，不得暗示为“总文档数/总命中数”

## MODIFIED Requirements
### Requirement: 知识库重建
系统 SHALL 将知识库重建升级为“向量库 + 图谱”双索引构建，并对外保持原接口不变。

#### Scenario: 兼容现有 rebuild 调用方
- **WHEN** 前端点击“重建向量库”
- **THEN** 后端仍调用 `POST /api/knowledge/rebuild`
- **AND** 实际执行流程包含向量索引 + 图谱索引

### Requirement: 合同分析结果存储
系统 SHALL 在分析结果中同时存储向量检索上下文与图谱检索上下文，用于审计与调试。

## REMOVED Requirements
### Requirement: 仅向量检索作为唯一检索策略
**Reason**: 无法覆盖关系推理与跨文档关联问题，且可解释性不足。
**Migration**: 保留 Chroma 向量索引作为其中一路检索信号，但不再作为唯一信号。

## Notes / Recommendation
- 在“知识库约 200 条文档”的规模下，是否值得“直接替换”取决于你希望解决的问题类型：
  - 如果主要痛点是“找得到相关法条/风险清单段落”，向量检索通常足够，收益可能有限。
  - 如果你希望解决“条款之间冲突、条款→法条→风险类型的关系链解释”，引入图谱会更有价值。
- 本 spec 以“全套 LightRAG + 直接替换”为目标，但实现上仍需要保证：Neo4j 不可用时应有清晰错误与降级策略（属于实现细节，见 tasks 与 checklist）。

