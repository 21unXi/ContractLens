# Plan：下一步（真流式追问 + 会话持久化 + LightRAG 知识库治理）

## Summary
- 目标（一次性落地三项改进）：
  1) **真流式追问**：仅针对“追问”（follow-up）把当前“分段返回”升级为 **token/delta 级增量输出**，并支持取消与节流，提升等待体验。
  2) **会话持久化（前后端都持久）**：把后端 `AnalysisChatSessionService` 从内存态改为 MySQL 落库，并新增历史查询接口；前端进入合同对话时拉取并回显历史（刷新不丢）。
  3) **LightRAG 知识库治理**：rebuild 导出 inputs 时做“增量清理旧 txt”，并补充导出统计与时间戳；LightRAG 调用增加短超时（connect 2s / read 10s）；LightRAG 不可用时采用“启动告警（不阻断启动）”策略。
- 范围：后端 + 前端 + 文档（README/PROJECT/PROGRESS），不引入新基础设施（不做 CI）。

## Current State Analysis（基于仓库现状）

### 1) SSE 目前非 token 级
- SSE 入口：`POST /api/analysis/contracts/{id}/stream` → [AnalysisController](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/controller/AnalysisController.java#L22-L38)
- 当前“流式”是 **LLM 完整返回后再 split** → [AnalysisService.streamAnswer/splitAnswer](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java#L295-L342)
- 追问的 LLM 调用是同步 `String` → [AnalysisService](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java#L111-L120) + [AiContractAnalyst](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/ai/AiContractAnalyst.java#L64-L91)

### 2) 会话目前双端内存态
- 后端会话：内存 `ConcurrentMap`，重启丢失 → [AnalysisChatSessionService](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisChatSessionService.java#L16-L68)
- 前端会话：Pinia store 内存态，刷新丢失 → [analysisChat.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/stores/analysisChat.js)

### 3) LightRAG rebuild 导出存在残留
- LightRAG 模式 rebuild：只写/覆盖 `*.txt`，默认不清空，删除文档后旧文件会残留 → [LightRagIngestService](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/lightrag/LightRagIngestService.java#L24-L53)
- status 探测：LightRAG 模式会直接跑一次 `probeQuery` 的 `/query` → [KnowledgeStatusController](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/controller/KnowledgeStatusController.java#L83-L94)

## Decisions（已确认）
- 会话持久化：**前后端都持久**（新增 history API + 前端回显）。
- 真流式范围：**只覆盖追问（follow-up）**；初次结构化分析保持现状。
- LightRAG 不可用：**启动告警**（不阻断启动），分析按 `fallback-to-legacy` 决定降级或报错。
- LightRAG HTTP 默认超时：**connect 2s / read 10s**。
- inputs-dir 清理策略：**增量清理旧 txt**（删除本次 rebuild 不再对应任何 `knowledge_docs` 的文件）。

## Proposed Changes

### A) 真流式追问（token/delta）

#### A1) 后端：新增“追问流式”实现（保留现有 SSE 协议并向后兼容）
- 文件： [AnalysisService.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java)
  - 在处理追问分支中改为使用 streaming LLM，边生成边发送 `event:answer`
  - 发送 payload 兼容两种模式：
    - chunk 模式（现有）：`{ chunk, index, isLast }`
    - delta 模式（新增，仅追问）：`{ delta, seq, isLast }`
  - 增加节流：将 token 在短时间窗内合并后再发送（降低 SSE 事件风暴）
  - 增加取消：客户端断开/stop 时立即停止上游流并结束 emitter（避免继续消耗）

#### A2) 后端：新增 streaming LLM 调用组件（优先走 LangChain4j Streaming）
- 新增类（示例命名）：`com.contractlens.service.ai.StreamingFollowUpAnswerer`
  - 使用 `StreamingChatLanguageModel`（LangChain4j 0.34）输出 token 回调
  - 输入与现有追问保持一致：`contractContent / retrievedContext / graphContext / conversationHistory / question`
  - 输出：回调 `onDelta(String)` + `onComplete()` + `onError(Throwable)`
- 若 LangChain4j starter 无法满足 DashScope streaming 兼容性，则在计划内预留兜底：
  - 新增 `OpenAiCompatibleStreamingClient`（WebClient/RestClient 直连 OpenAI-compatible streaming 并解析 SSE）

#### A3) 前端：支持 delta 拼接（避免每段自动插入空行）
- 文件： [analysisChat.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/stores/analysisChat.js)
  - 对 delta：直接拼接字符串（不插入 `\n\n`）
  - 对 chunk：保持当前分段展示逻辑
- 文件： [contract.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/api/contract.js)
  - SSE 解析保持不变，只需把 `answer` payload 透传给 store，支持 `{chunk|delta}` 两种形态

### B) 会话持久化（前后端）

#### B1) 后端：新增聊天消息表与仓库（JPA 自动建表）
- 新增实体：`com.contractlens.entity.AnalysisChatMessage`
  - 字段：`id, contractId, role(user/assistant), content, createdAt`
  - 索引：`(contract_id, created_at)`
- 新增仓库：`com.contractlens.repository.AnalysisChatMessageRepository`
  - `findTop20ByContractIdOrderByCreatedAtAsc`
  - `deleteByContractId`
  - （可选）用于裁剪的自定义删除（保留最近 20）

#### B2) 后端：改造 AnalysisChatSessionService 由 DB 驱动
- 文件： [AnalysisChatSessionService.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisChatSessionService.java)
  - `appendMessage`：写库 + 裁剪（每合同最多 20 条，保持现有行为）
  - `getHistory/buildPromptHistory/getMessageCount`：改为读库

#### B3) 后端：新增历史接口（供前端回显）
- 在 [AnalysisController.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/controller/AnalysisController.java) 增加：
  - `GET /api/analysis/contracts/{contractId}/chat/history`
  - 鉴权：复用现有 “合同归属校验”逻辑（与分析一致）
  - 返回：最多 20 条 `[{role, content, createdAt}]`

#### B4) 后端：合同删除时级联清理聊天消息
- 文件： [ContractService.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/ContractService.java)
  - 在删除合同前执行 `analysisChatMessageRepository.deleteByContractId(contractId)`（或 FK 级联）

#### B5) 前端：进入对话时加载历史
- 文件： [contract.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/api/contract.js)
  - 新增 `getChatHistory(contractId)` API
- 文件： [analysisChat.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/stores/analysisChat.js)
  - 新增 action：`loadHistory(contractId)` 并写入 session messages
- 文件： [Dashboard.vue](file:///e:/code/repository/ContractLens/contractlens-frontend/src/views/Dashboard.vue)
  - 在选中合同/进入对话区域时调用 `loadHistory`

### C) LightRAG 知识库治理（增量清理 + 统计 + 超时 + 启动告警）

#### C1) 导出 inputs 增量清理旧 txt
- 文件： [LightRagIngestService.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/lightrag/LightRagIngestService.java)
  - rebuild 时构建 `expectedFilenames`
  - 遍历 inputs-dir 下 `*.txt`，删除不在 expected 集合的旧文件
  - 输出统计：written/deleted/skipped（docId 空等）

#### C2) rebuild 结果结构化 + status 展示最后一次 rebuild 信息
- 新增 DTO：`KnowledgeRebuildResponse`（ragMode、written/deleted/duration/finishedAt/inputsDir）
- 修改 [KnowledgeController.rebuildKnowledgeBase](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/controller/KnowledgeController.java#L21-L27) 返回 JSON DTO
- 新增“最后一次 rebuild 元数据”存储（避免重启丢失且不被 LightRAG ingest）：
  - 在 inputs-dir 写入 `.contractlens_rebuild.json`（非 txt），status 读取并返回
- 修改 [KnowledgeStatusResponse](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/dto/KnowledgeStatusResponse.java) + [KnowledgeStatusController](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/controller/KnowledgeStatusController.java)：
  - 增加：`lastRebuildAt / lastRebuildWritten / lastRebuildDeleted / lastRebuildDurationMs`

#### C3) LightRAG HTTP 超时与错误可诊断
- 文件：`LightRagClient`（路径：`com.contractlens.service.lightrag`）
  - 为 LightRAG 专用 RestClient 配置 connect/read timeout（2s/10s）
  - 失败时区分：连接失败/超时/HTTP 4xx/5xx（保留响应体）

#### C4) 启动告警：LightRAG 可用性不阻断启动
- 文件： [StartupConfigValidator](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/config/StartupConfigValidator.java)
  - 继续校验 JWT/LLM key（现有）
  - 新增 LightRAG “warn-only” 自检（当 `rag.mode=lightrag`）：
    - baseUrl/queryPath/inputsDir 是否配置
    - inputsDir 可写性（创建目录+写临时文件）
    - 可选：轻量 ping（带超时），失败仅记录 warn（不抛异常）

### D) 文档更新
- 更新：
  - [README.md](file:///e:/code/repository/ContractLens/README.md)：补充/校对 LightRAG 启动与 dev 配置方式（与实现一致）
  - [PROJECT.md](file:///e:/code/repository/ContractLens/PROJECT.md)：补充“追问真流式（delta）”与“会话持久化”说明
  - [PROGRESS.md](file:///e:/code/repository/ContractLens/PROGRESS.md)：更新“下一步计划”与已完成功能项

## Verification（实现后）
- 编译/诊断：IDE Diagnostics 无错误；（若环境有 Maven）`mvn test`
- 真流式追问：
  - 追问时浏览器 Network stream 可见 `event:answer` 多次、payload 含 `delta`
  - 前端展示不会每 token 插入空行，内容连续可读
  - 点“停止”后服务端不再继续发送 delta（连接及时结束）
- 会话持久化：
  - 刷新页面后再次进入同一合同对话，历史能从后端拉回并回显
  - 删除合同后聊天记录同步删除
- LightRAG 治理：
  - rebuild 后 inputs-dir 中不存在已删除 doc 的残留 txt
  - status 返回 lastRebuild 统计与时间戳
  - LightRAG 不可用时：启动不阻断但会 warn；status/分析按配置降级或报错

