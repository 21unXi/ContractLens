# /plan：实现 PROGRESS.md 第 3、4、5 点（结果过期判定 / SSE 可靠性 / 可观测性）

目标：完成 PROGRESS.md “下一步计划（建议）”中的第 3、4、5 点，并保证对现有行为尽量兼容、可验证、可回滚。

范围说明（对应 PROGRESS.md）
- 3. 结果过期判定：为合同内容与分析结果建立可比较的版本（内容 hash），避免复用过期摘要/上下文。
- 4. SSE 可靠性：完善服务端取消、超时与 keep-alive；前端补齐断线提示与一键恢复（最小可用）。
- 5. 可观测性：记录并展示检索/生成阶段耗时、LightRAG `/query` 延迟与错误率（以 status 页面与 SSE payload 为主）。

非目标（本次不做）
- 不引入完整 APM/Prometheus/Grafana。
- 不把初次分析升级为 token/delta 真流式（这是第 2 点的内容）。
- 不引入完整的数据库迁移框架（Flyway）到生产；但会补齐 init.sql 与 ALTER 指引，且代码支持“旧数据字段为空”的兼容。

---

## 一、结果过期判定（第 3 点）

### 设计
- 在 `contracts` 增加 `content_hash`（VARCHAR），用于标识“该合同当前内容版本”。
- 在 `analysis_results` 增加 `contract_content_hash`（VARCHAR），用于记录“该次分析基于哪个合同内容版本生成”。
- 过期判定规则：
  - `contract.contentHash` 为空：视为未知版本（兼容旧数据），服务端会在首次用到合同时补齐并持久化。
  - `analysisResult.contractContentHash` 为空：视为旧结果（stale=true）。
  - 两者不一致：stale=true。

### 行为变更
- `GET /api/analysis/contracts/{contractId}/result`
  - 返回 payload 增加 `stale` 与 `contractContentHash`（可选）字段；前端据此展示“结果可能已过期，需要重新分析”提示。
  - 若 stale=true，仍返回旧结果本体（方便用户先看），但 UI 给出一键“重新分析”入口。
- SSE follow-up 复用上下文：
  - 在复用 `retrieved_context/graph_context` 前增加 hash 一致性校验；若 stale=true 则不复用旧上下文，改为重新检索（避免“新合同配旧上下文”）。

### 落地改动（文件）
- 后端实体
  - `Contract`：新增 `contentHash` 字段与持久化列映射。
  - `AnalysisResult`：新增 `contractContentHash` 字段与持久化列映射。
- 后端服务
  - `ContractService.uploadContract`：保存合同时计算并写入 `contentHash`（对 content 先做轻度规范化：trim + 统一换行）。
  - `AnalysisService.mapJsonToAnalysisResult`：写入 `contractContentHash = contract.contentHash`。
  - `AnalysisService.resolveRetrievedContext`：复用上下文前判断 hash 是否一致。
- API
  - 扩展 `AnalysisResultPayload`：增加 `stale`（Boolean）与 `contractContentHash`（String，可选）。
  - `AnalysisController.getAnalysisResult`：计算 stale 并下发。
- 数据库
  - 更新 `database/init.sql`：补齐新列。
  - 文档追加手工迁移命令（ALTER TABLE），并说明旧数据字段为空时的兼容策略。

### 验收
- 合同内容未变更时：刷新/从历史进入可直接回显分析结果，stale=false。
- 旧数据（hash 为空）时：接口可正常返回，stale=true，提示用户可重新分析。
- follow-up 不会在 stale=true 时复用旧上下文。

---

## 二、SSE 可靠性（第 4 点）

### 设计
- 为每个 SSE 会话引入统一的 `AtomicBoolean cancelled`：
  - `emitter.onCompletion/onTimeout/onError` 都将其置为 true。
  - 初次分析与追问两条路径都注册（当前只在追问分支注册）。
- 设置有限超时（可配置）：
  - 新增配置：`contractlens.sse.timeout-ms`（默认例如 600000）。
- 增加 keep-alive 心跳（可配置）：
  - 新增配置：`contractlens.sse.heartbeat-interval-ms`（默认例如 15000）。
  - 心跳事件：`event: ping`，payload 含 `contractId` 与 `ts`；若发送失败立即取消会话并停止心跳。
- 区分“客户端取消/断连”与“真实错误”：
  - cancelled=true 或已断连导致的 IOException：不再发送 `error` 事件（避免误导），直接安静 `complete()`。
  - 真实错误：按现有逻辑发送 `error` 并 `completeWithError()`。

### 落地改动（文件）
- 后端
  - `AnalysisService`：
    - `new SseEmitter(0L)` 改为可配置 timeout。
    - `handleStreamingAnalysis`：统一注册回调、增加阶段间 cancelled 检查、引入心跳调度并在完成/错误时清理。
  - 可选：为 `StreamingAiConfig`/LightRAG client 设置更明确的请求超时（若现有 builder 支持）。
- 前端
  - `contract.js`：解析 `event: ping` 后忽略（不影响现有 UI）。
  - `Dashboard.vue`：当流中断（error/Abort）时显示明确提示，并提供“一键重试/重新连接”（复用现有重试按钮即可）。

### 验收
- 长时间检索/生成时：即使无新 status/answer，连接也不会被空闲断开（心跳可观察到）。
- 点击“停止”或刷新页面：后端能尽早停止后续阶段推进（至少停止继续发送并尽快结束会话），不会继续占用线程长时间跑。
- 不再出现“用户主动取消却弹出分析失败”的误导错误。

---

## 三、可观测性（第 5 点）

### 设计
- LightRAG `/query` 延迟：
  - 在 `LightRagClient.query()` 内测量 `latencyMs` 并写入返回对象（建议扩展 `LightRagQueryResult` 增加字段）。
  - `GET /api/knowledge/status` 增加 `lightRagProbeLatencyMs` 字段。
- SSE 阶段耗时：
  - `status` payload 增加：
    - `elapsedMs`：会话开始到当前的总耗时。
    - `phaseElapsedMs`：当前阶段已耗时（可选）。
  - `done` payload 增加：
    - `totalElapsedMs`
    - `phaseDurationsMs`：Map（retrieving_context / analyzing_contract / streaming_answer 等）
- 前端展示：
  - `Knowledge.vue`、`Settings.vue`：展示 LightRAG probe latency。
  - `Dashboard.vue`：在 status 文案旁展示 `elapsedMs`（和/或阶段耗时）。

### 落地改动（文件）
- 后端
  - `LightRagQueryResult`、`LightRagClient`
  - `KnowledgeStatusResponse`、`KnowledgeStatusController`
  - `AnalysisService`：SSE payload 扩展
- 前端
  - `Knowledge.vue`、`Settings.vue`
  - `Dashboard.vue`（仅展示层，数据透传使用现有 store/status 对象）

### 验收
- `/api/knowledge/status` 返回并显示 LightRAG probe latency（ms）。
- SSE status/done 事件包含耗时字段，前端可见并且不影响旧字段解析。

---

## 四、实施顺序（避免大范围回滚成本）
1) 后端：加 hash 字段与 stale 判定（含 API/DTO），保证只读接口可用且兼容旧数据。
2) 后端：SSE 可靠性（timeout + 回调统一 + keep-alive + 取消安静收口）。
3) 后端：可观测性字段（LightRAG latency + SSE durations）。
4) 前端：展示与交互（stale 提示、latency 展示、status 耗时展示、ping 忽略）。
5) 文档：更新 PROGRESS.md 已完成项与使用说明（若需要）。

---

## 五、数据库变更（手工迁移指令草案）
- `contracts`：
  - `ALTER TABLE contracts ADD COLUMN content_hash VARCHAR(64) NULL;`
- `analysis_results`：
  - `ALTER TABLE analysis_results ADD COLUMN contract_content_hash VARCHAR(64) NULL;`

说明：上线后首次访问旧合同会自动计算并写入 `contracts.content_hash`；旧的 `analysis_results.contract_content_hash` 为空将被判定 stale。

