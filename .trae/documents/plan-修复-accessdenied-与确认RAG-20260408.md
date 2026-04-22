## Summary
解决仍在出现的 `AccessDeniedException`（尤其是 SSE/异步分发场景下 “response is already committed” 的二次异常），并明确当前项目的 RAG 是否已建立、知识库数据来源与如何验证/构建。

## Current State Analysis（基于仓库现状）
### 1) AccessDeniedException 的典型触发点（目前日志形态）
- 你贴的堆栈包含 `AsyncContextImpl`/`asyncDispatch` 与 “Unable to handle the Spring Security Exception because the response is already committed.”，这非常符合 **SSE（SseEmitter）长连接已开始写响应后，发生安全异常/错误分发再次进入过滤器链** 的特征。
- 当前安全配置已放行：
  - `/api/auth/**`
  - `/error`
  - `OPTIONS /**`
  - 见 [SecurityConfig.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/config/SecurityConfig.java#L39-L66)
- 但仍可能触发 AccessDenied 的原因：
  1) **ASYNC/ERROR dispatcher type** 的请求再次进入授权过滤器，而这些 dispatcher type 未显式放行；
  2) 某些请求实际未携带/携带了无效 JWT（例如 SSE 前端请求没带 Authorization、token 过期），导致匿名认证被拒；
  3) SSE 过程中服务端抛异常触发 error dispatch，进而产生二次安全异常。

### 2) RAG 是否已建立、是否有知识库
- **RAG 管线已存在**：
  - `AnalysisService.retrieveContext()` 调用 `retriever.findRelevant(contractContent)` 获取相关片段：[AnalysisService.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java#L131-L137)
  - `Retriever<TextSegment>` 由 `AiConfig` 提供，基于 EmbeddingStoreRetriever(topK=5, minScore=0.6)：[AiConfig.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/config/AiConfig.java#L23-L26)
- **知识库存储与入库机制已存在**：
  - 知识库原始文档在 MySQL 表 `knowledge_docs`（实体 `KnowledgeDoc`）：[KnowledgeDoc.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/entity/KnowledgeDoc.java#L9-L46)
  - `KnowledgeService.ingestKnowledgeBase()` 会把 `knowledge_docs` 全量 ingest 到向量库（EmbeddingStore）：[KnowledgeService.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/KnowledgeService.java#L22-L29)
  - 暴露了一个触发入口：`POST /api/knowledge/rebuild`：[KnowledgeController.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/controller/KnowledgeController.java#L10-L22)
- **但默认不会自动构建**：仓库中没有看到启动时自动调用 `ingestKnowledgeBase()` 的逻辑；因此“是否已经有向量知识库”取决于：
  - `knowledge_docs` 表是否已有数据；
  - 是否调用过 `/api/knowledge/rebuild`；
  - Chroma/EmbeddingStore 服务是否可用（application.yml 指向 `langchain4j.chroma.embedding-store.url`）。
 - **前端当前的“知识库法条 1,240+”展示是硬编码**：Dashboard 统计卡片里直接写死了数值，不是实时读后端或向量库统计；需要单独改造为读取后端 status 才能真实反映知识库数据量。

## Proposed Changes
### A) 定位并消除 AccessDenied（面向 SSE/异步分发）
1) 在 `SecurityConfig` 放行 dispatcher types
- 增加：
  - `dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()`
- 目的：避免异步/错误分发阶段再次触发授权拦截，造成 “response already committed” 的二次异常。

2) 增加统一的 AuthenticationEntryPoint / AccessDeniedHandler（返回 JSON 而不是走 /error）
- 在 `exceptionHandling` 中配置：
  - 未认证：返回 401 JSON
  - 已认证但无权限：返回 403 JSON
- 目的：避免异常链路走到 servlet 容器错误页/二次 dispatch；同时让前端更易处理。
- 对 SSE：若 response 已提交（committed），handler 应直接返回（不再写 body）。

3) 增加最小化的“请求路径/dispatcherType”日志，快速确认触发源
- 在 handler 或过滤器中记录：`request.getMethod()`、`request.getRequestURI()`、`request.getDispatcherType()`、以及是否存在 `Authorization` 头。
- 目的：确认 AccessDenied 到底来自哪个路径（/stream、/error、或其他接口），并确认是否为缺 token。

### B) 明确 RAG/知识库状态（无需猜测）
1) 新增一个只读状态接口（推荐）
- 新增：`GET /api/knowledge/status`
- 返回：
  - `knowledgeDocsCount`（MySQL `knowledge_docs` 行数）
  - `embeddingStoreConfigured`（从配置读取 url/collection-name 的存在性）
  - （可选）`retrieverProbe`：用固定 query 做一次 `retriever.findRelevant()`，返回命中的片段数量（注意不回传片段全文，避免泄露）

2) 把 `/api/knowledge/rebuild` 的执行语义明确化
- 当前实现是同步调用 `embeddingStoreIngestor.ingest(documents)`；根据 embedding store 规模可能耗时。
- 可选改进：改为异步执行并返回 taskId，避免请求阻塞（仅在数据量大时需要）。

### C) 验证步骤（你可手工测试）
1) AccessDenied 排查
- 在浏览器/前端触发同样操作，记录 Network 中发生 403 的请求 URL；
- 查看后端新增日志中打印的 `URI + dispatcherType + 是否携带 Authorization`；
- 预期：不再出现 “response is already committed” 的二次安全异常；SSE 在无 token 时返回 401 JSON（或直接断开）。

2) RAG 状态确认
- 先查 `knowledge_docs` 是否有数据（或调 `/api/knowledge/status`）；
- 若有数据，调用 `POST /api/knowledge/rebuild` 后再次确认 retriever probe 命中数；
- 预期：分析时 `retrieveContext()` 能拼出 `retrievedContext`，并写入 `analysis_results.retrieved_context`。

## Files To Change
- `contractlens-backend/src/main/java/com/contractlens/config/SecurityConfig.java`
- （新增）`contractlens-backend/src/main/java/com/contractlens/config/SecurityExceptionHandlers.java` 或同等位置
- （新增）`contractlens-backend/src/main/java/com/contractlens/controller/KnowledgeStatusController.java`（或扩展现有 KnowledgeController）
- （可选）`contractlens-backend/src/main/java/com/contractlens/service/KnowledgeService.java`（增加 status/probe 方法）

## Assumptions & Decisions
- 默认保持现有 JWT 鉴权策略不变：除 `/api/auth/**` 外均需认证（RAG rebuild/status 也默认需要认证）。
- 允许放行 `DispatcherType.ASYNC/ERROR` 是为了避免容器内部二次分发导致的误拦截；不改变外部请求的认证要求。
