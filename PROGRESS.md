# ContractLens 开发进度

最后更新：2026-04-25

***

## 整体进度（以用户可见能力为准）

| 模块                   | 状态    | 说明                                                                         |
| -------------------- | ----- | -------------------------------------------------------------------------- |
| 认证与权限（JWT）           | ✅ 已完成 | 登录/注册/鉴权、401/403 统一响应、CORS                                                 |
| 合同管理                 | ✅ 已完成 | 上传/列表/删除（硬删除）/按 ID 获取                                                      |
| 合同分析（结构化）            | ✅ 已完成 | 生成结构化风险结果并落库                                                               |
| 合同分析（对话式流式 SSE）      | ✅ 已完成 | status/answer/done/error 事件、追问、多轮会话（内存态）                                   |
| 知识库（RAG）             | ✅ 已完成 | 默认 LightRAG（服务化接入）；可切换 legacy（Chroma + Neo4j）；以 `contractlens.rag.mode` 控制 |
| 前端工作台                | ✅ 已完成 | 控制台、对话区、摘要区、合同列表、删除与动态统计                                                   |
| 历史/设置/知识库页面          | ✅ 已完成 | 可导航、可用、支持查看/触发 rebuild                                                     |
| 图谱增强（Neo4j，仅 legacy） | ✅ 已完成 | 分析链路接入：向量检索 + Neo4j 图谱上下文，生成 graph\_context 并落库；图谱失败自动降级并显式标记              |

***

## 已完成功能

### Backend（Spring Boot）

- **认证与安全**
  - 注册/登录：`POST /api/auth/register`、`POST /api/auth/login`
  - JWT 过滤与鉴权：除 `/api/auth/**` 外均需登录
  - 统一 401/403 JSON 响应；放行 ASYNC/ERROR dispatcher type，降低 SSE 场景 “response already committed” 连锁异常
  - 配置安全：`application.yml` 不包含明文 API Key/密码；默认启用 dev profile 并从 `application-dev.yml` 读取密钥（该文件不入库）；启动时校验关键配置缺失并失败早
- **合同模块**
  - 上传：`POST /api/contracts/upload`（支持 txt/docx/pdf，解析后入库，文件落盘到 `upload.path`）
  - 列表：`GET /api/contracts`（仅当前用户）
  - 获取：`GET /api/contracts/{id}`（已做归属校验：不属于当前用户或不存在均返回 404）
  - 删除：`DELETE /api/contracts/{id}`（硬删除：先删分析结果，再删合同；best-effort 删除上传文件）
- **分析模块**
  - 结构化分析：`POST /api/analysis/contracts/{contractId}`（生成 JSON → 解析 → 写入 `analysis_results`）
  - 流式对话分析（SSE）：`POST /api/analysis/contracts/{contractId}/stream`（事件：status/answer/done/error）
  - AI 输出容错：支持 fenced JSON（`json ... ` ）清洗后再解析，减少 JsonParseException
  - MySQL JSON 列映射：`analysis_results` 的 JSON 字段使用 Hibernate JSON 类型绑定，避免 binary charset 写入失败
- **知识库模块（RAG）**
  - 文档源：MySQL `knowledge_docs`
  - RAG 模式：默认 `contractlens.rag.mode=lightrag`；可切换 `legacy`；`fallback-to-legacy` 控制 LightRAG 失败时是否回退
  - 状态：`GET /api/knowledge/status`
    - legacy：文档数、向量库配置、向量/图谱探测返回条数、图谱节点/关系数、错误信息
    - lightrag：显示 ragMode、LightRAG 服务与探测结果（returnedChunks / contextChars / error）
  - 列表：`GET /api/knowledge/docs`（分页返回 docId/title/docType/createdAt）
  - rebuild：`POST /api/knowledge/rebuild`
    - legacy：将 `knowledge_docs` ingest 到向量库；Neo4j 图谱 upsert 失败时自动降级但会记录失败原因
    - lightrag：把 `knowledge_docs` 同步为 LightRAG inputs 目录下的文本文件（由 LightRAG Server 负责索引）
  - Embedding 分批：对 `embedAll` 做批量封装，避免单次请求超过阿里云 text-embedding-v3 的 10 条文本限制
  - 检索探测说明：`retrieverProbe*` 表示 probeQuery 的“返回片段数”，且受 `retrieverTopK` 上限影响（默认 topK=5）

### Frontend（Vue 3）

- **认证**
  - 登录/注册页、登出
  - token 持久化与统一 HTTP 客户端；401 自动清理 token 并跳转登录
- **控制台（Dashboard）**
  - 上传合同、合同列表、删除合同
  - 对话式分析（默认入口）：流式渲染、追问、停止/重试、完成后刷新结构化摘要与条款卡片
  - 对话模式可隐藏左侧合同列表，给对话留出更多空间
  - 动态统计：知识库文档数从 `/api/knowledge/status` 获取（不再硬编码）
  - SSE 稳定性：兼容 CRLF 分隔；收到 done/error 后主动结束读取，避免连接未关闭导致“分析中”卡住
  - 项目清理：移除未使用的前端 API 封装与无效字段引用；收敛调试输出
- **页面补齐**
  - 历史记录页：合同列表 + 一键进入对话分析
  - 设置页：展示认证状态与知识库状态 + 重建向量库按钮
  - 知识库页：展示 status + 文档列表（不展示正文）

***

## API 概览（鉴权：除 /api/auth/\*\* 外均需 JWT）

### Auth

- `POST /api/auth/register` 注册
- `POST /api/auth/login` 登录（返回 token）

### Contracts

- `POST /api/contracts/upload` 上传合同
- `GET /api/contracts` 获取当前用户合同列表
- `GET /api/contracts/{id}` 获取合同详情（已做归属校验：不属于当前用户或不存在均返回 404）
- `DELETE /api/contracts/{id}` 删除合同（硬删除，含分析结果清理）

### Analysis

- `POST /api/analysis/contracts/{contractId}` 结构化分析
- `POST /api/analysis/contracts/{contractId}/stream` 流式对话分析（SSE）

### Knowledge

- `GET /api/knowledge/status` 知识库状态
- `GET /api/knowledge/docs?page=&size=` 知识库文档列表（分页）
- `POST /api/knowledge/rebuild` 重建知识库（根据 `contractlens.rag.mode` 同步到 legacy 或 LightRAG）

***

## 文件结构与职责（真实仓库）

### 后端：contractlens-backend

- `src/main/java/com/contractlens/config/`
  - `SecurityConfig`：JWT 鉴权/CORS/异常处理配置
  - `JwtRequestFilter`：解析 Authorization Bearer token
  - `AiConfig`：Retriever/EmbeddingStoreIngestor 装配
  - `RagConfig`：RAG/LightRAG 配置属性装配
  - `Neo4jDriverConfig`：Neo4j Driver 装配
- `src/main/java/com/contractlens/ai/`
  - `BatchingEmbeddingModel`：EmbeddingModel 批量封装（分批 embedAll）
- `src/main/java/com/contractlens/controller/`
  - `AuthController`：登录/注册
  - `ContractController`：上传/列表/删除
  - `AnalysisController`：结构化分析 + SSE 流式分析入口
  - `KnowledgeController`：rebuild + docs 列表
  - `KnowledgeStatusController`：知识库状态接口
- `src/main/java/com/contractlens/service/`
  - `ContractService`：合同保存/查询/删除
  - `FileStorageService`：上传文件落盘、解析、删除（best-effort）
  - `AnalysisService`：RAG 检索、结构化结果落库、SSE 推送、会话串联
  - `AnalysisChatSessionService`：会话（内存态）
  - `KnowledgeService`：从 knowledge\_docs ingest 到向量库、同步 Neo4j 知识图谱、分页列表
  - `service/graph/GraphSchemaService`：Neo4j 约束与索引
  - `service/lightrag/`：LightRAG（Server）集成：query 与 inputs-dir 同步
- `src/main/java/com/contractlens/rag/`
  - `RagMode`、`RagProperties`：RAG 模式切换与 fallback 配置
- `src/main/java/com/contractlens/entity/`
  - `User`、`Contract`、`AnalysisResult`、`KnowledgeDoc`：MySQL 实体模型
- `src/main/java/com/contractlens/repository/`
  - `UserRepository`、`ContractRepository`、`AnalysisResultRepository`、`KnowledgeDocRepository`
- `src/main/java/com/contractlens/util/`
  - `JwtUtil`：JWT 工具
  - `JsonSanitizer`：AI 返回 JSON 的 fence 容错清洗

### 前端：contractlens-frontend

- `src/api/`
  - `http.js`：统一 axios 实例 + token 同步 + 401 处理
  - `contract.js`：合同 API + SSE 解析订阅（POST 流式）
  - `knowledge.js`：知识库 status/docs/rebuild API
- `src/stores/`
  - `auth.js`：登录态（token）管理
  - `analysisChat.js`：对话会话状态（消息/状态/结构化结果）
- `src/views/`
  - `Dashboard`：控制台（上传/列表/对话/摘要/删除/动态统计）
  - `History`：历史记录
  - `Knowledge`：知识库
  - `Settings`：设置
  - `Login`、`Register`：认证页
- `src/router/index.js`：路由与守卫
- `src/style.css`：设计令牌与全局样式

***

## 已知限制

- SSE 对话会话在后端为内存态（重启会丢失）；目前不做持久化聊天历史。
- “分段返回”是把完整回答切块后通过 SSE 发送，用于改善等待体验；并非模型 token 级真流式输出。
- legacy 模式下可启用 Neo4j 图谱增强；图谱查询失败会自动降级为仅向量检索（会在 `graph_context` 中标记 FAILED）。
- “知识库是否可用”取决于 `knowledge_docs` 是否有数据、是否调用过 `/api/knowledge/rebuild`、以及 Chroma 是否可用。
- LightRAG 模式下，`/api/knowledge/rebuild` 仅负责把 `knowledge_docs` 导出到 LightRAG 的 inputs 目录；LightRAG Server 的索引进度与行为由其自身实现决定。
- LightRAG Server 的 `/query` 调用已修复为强制发送 JSON body；避免 FastAPI 报 422（body 缺失）。

***

## 下一步计划（建议）

1. ✅ 安全与配置治理：移除仓库内的明文密钥/密码，改为 `application-dev.yml` 集中存放并由 `application.yml` 引用（dev 文件不入库）；需要轮换已泄露的 API Key（平台侧操作）
2. ✅ 修复合同详情接口的归属校验（防止越权读取）
3. LightRAG 体验：补齐 LightRAG Server 启动/配置说明与一键自检（例如后端启动前检测 `POST /query` 可用）
4. 流式体验优化：支持模型 token 级真流式输出，并增加服务端取消/超时处理与耗时分段统计
5. 会话持久化：将 SSE 对话历史从内存态迁移为可持久化（按用户+合同维度），支持“继续上次会话”
6. 知识库治理：doc_type 规范化、按类型统计、rebuild 记录最后一次同步时间与 LightRAG/legacy 探测指标
7. 可观测与回归：补充关键单测（LightRAG client 解析、RAG 模式切换），并增加最小 CI 校验（至少前端 build）
