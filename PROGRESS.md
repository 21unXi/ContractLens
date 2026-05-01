# ContractLens 开发进度

最后更新：2026-05-01

***

## 整体进度（以用户可见能力为准）

| 模块                   | 状态    | 说明                                                                         |
| -------------------- | ----- | -------------------------------------------------------------------------- |
| 认证与权限（JWT）           | ✅ 已完成 | 登录/注册/鉴权、401/403 统一响应、CORS                                                 |
| 合同管理                 | ✅ 已完成 | 上传/列表/删除（硬删除）/按 ID 获取                                                      |
| 合同分析（结构化）            | ✅ 已完成 | 生成结构化风险结果并落库                                                               |
| 合同分析（对话式流式 SSE）      | ✅ 已完成 | status/answer/done/error 事件、追问、多轮会话（落库 + 历史回显）                             |
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
  - 追问真流式：追问阶段 `answer` 事件支持 `delta/seq` 增量输出（初次分析仍为分段 chunk）
  - 会话持久化：聊天消息落库（每合同最多 20 条），新增历史接口 `GET /api/analysis/contracts/{contractId}/chat/history`
  - 分析结果读取：新增 `GET /api/analysis/contracts/{contractId}/result`，用于刷新/从历史进入时回显已有结构化摘要（不触发重算）
  - 结果过期判定：合同内容写入 `content_hash`，分析结果写入 `contract_content_hash`；`/result` 返回 `stale` 标记并懒计算旧合同 hash
  - SSE 可靠性：SSE 增加可配置超时与 keep-alive（`ping` 事件），统一取消回调并在取消/断连时安静收口
  - 可观测性：SSE status/done 下发耗时字段（elapsedMs/phaseElapsedMs/totalElapsedMs/phaseDurationsMs）；LightRAG `/query` 记录 latencyMs 并在 `/api/knowledge/status` 返回
  - AI 输出容错：支持 fenced JSON（`json ... `  ）清洗后再解析，减少 JsonParseException
  - 结构化结果稳定性：提高模型 max-tokens 默认值（2048→4096）并加入动态降级重试（正常→精简→最小 3 条），降低 JSON 截断导致的解析 EOF
  - 风险条目强约束：party\_\*\_risks 必须为对象数组；risk\_type 禁止占位词且需短标签化；risk\_level 必须为 高/中/低；不合格结构化结果触发重试/报错，首轮答案不再输出“未分类风险（待确认）”占位标题
  - 风险两类与排序：风险列表同时覆盖【现有条款】与【缺失项】两类风险，并在文本答案中以单列表按优先级混排输出（标题标记【现有】/【缺失】）；条数超限时仅输出优先级最高的前几条
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
  - 刷新/从历史进入：优先加载历史消息与已有结构化结果，不自动触发“重新分析”
  - 对话模式可隐藏左侧合同列表，给对话留出更多空间
  - 动态统计：知识库文档数从 `/api/knowledge/status` 获取（不再硬编码）
  - SSE 稳定性：兼容 CRLF 分隔；收到 done/error 后主动结束读取，避免连接未关闭导致“分析中”卡住
  - 可观测性：对话区展示实时运行时间（mm:ss）与状态文案；知识库页/设置页展示 LightRAG 探测延迟（ms）
  - 过期提示：摘要区在 `stale=true` 时提示“可能已过期，建议重新分析”
  - 报告化摘要：风险总览卡片补齐 Top 风险与优先建议
  - 条款可读性：风险条款列表默认按风险高到低排序，支持按风险等级/关键词筛选并高亮命中内容
  - 项目清理：移除未使用的前端 API 封装与无效字段引用；收敛调试输出
- **页面补齐**
  - 历史记录页：合同列表 + 一键进入对话分析
  - 设置页：展示认证状态与知识库状态 + 重建向量库按钮
  - 知识库页：展示 status + 文档列表（不展示正文）

<br />

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
- `GET /api/analysis/contracts/{contractId}/chat/history` 获取最近 20 条对话历史
- `GET /api/analysis/contracts/{contractId}/result` 获取已生成的结构化分析结果（不触发重算）

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
  - `AnalysisChatSessionService`：会话（落库：最近 20 条）
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

## 工程化清理（Repo Hygiene）

- 补齐 `.gitignore`：忽略 Python 缓存/pytest 产物、LightRAG 响应缓存等非源码文件，避免污染仓库
- 清理无关一次性脚本与根目录临时 Node 产物（中期汇报 PPT 生成脚本及其依赖文件）
- 清理不可移植默认配置：LightRAG `inputs-dir` 默认值改为相对路径 `./lightrag/inputs`

## 已知限制

- 初次分析的“分段返回”是把完整回答切块后通过 SSE 发送，用于改善等待体验；并非模型 token 级真流式输出。
- 追问已支持 token/delta 级增量输出；初次分析仍为 chunk 分段（见下一步计划）。
- legacy 模式下可启用 Neo4j 图谱增强；图谱查询失败会自动降级为仅向量检索（会在 `graph_context` 中标记 FAILED）。
- “知识库是否可用”取决于 `knowledge_docs` 是否有数据、是否调用过 `/api/knowledge/rebuild`、以及 Chroma 是否可用。
- LightRAG 模式下，`/api/knowledge/rebuild` 仅负责把 `knowledge_docs` 导出到 LightRAG 的 inputs 目录；LightRAG Server 的索引进度与行为由其自身实现决定。
- LightRAG Server 的 `/query` 调用已修复为强制发送 JSON body；避免 FastAPI 报 422（body 缺失）。

***

## 下一步计划（建议）

1. ⏳ 初次分析复用与显式刷新：初次分析若已有 `analysis_results`，默认复用并直接回放；仅在用户点击“重新分析”或显式 `refresh=true` 时才强制重检索 + 重算
2. ⏳ 初次分析真流式：把“完整答案切块 chunk”升级为 token/delta 级输出（与追问一致），并完善断流/重连体验
3. ⏳ 初次分析过期策略：当 `stale=true` 时，提供“仅回显旧结果 / 自动重算 / 询问用户”三种策略开关，避免误用旧摘要
4. ⏳ 断线恢复：前端在 SSE 中断时提供“重新连接本轮/继续追问”的更明确路径（避免只能重试初次分析）
5. ⏳ 可观测性升级：将 LightRAG `/query` 错误率与耗时做聚合统计（滑窗/最近 N 次），并在 status 展示（现在是单次 probe）
6. ⏳ 数据库治理：补齐迁移脚本与引入 Flyway（生产环境避免依赖 `ddl-auto=update`），并补齐索引/外键策略
7. ⏳ 回归与 CI：补充关键单测（LightRAG client、RAG 模式切换、SSE 解析），并增加最小 CI（后端编译 + 前端 build）
