## Summary
本次报错属于“结构化 JSON 输出被截断，导致 Jackson 解析 EOF”的稳定性问题。修复策略采用：
1) 提高后端模型输出上限（max-tokens）以避免正常结果被硬截断；
2) 保留现有“失败后重试更精简版本”的机制，并扩展为动态降级：默认 5 条，解析失败后自动降到 3 条（更稳）。

## Current State Analysis（已在仓库中确认）
- 报错栈指向 JSON 解析阶段：`AnalysisService.mapJsonToAnalysisResult()` 调用 `ObjectMapper.readTree(...)` 报 `JsonEOFException: Unexpected end-of-input`，典型是输出超过 token 上限被截断。
- 当前重试机制只有 1 次：
  - 第一次：`AiContractAnalyst.analyzeContract(...)`（较完整）
  - 失败后：`AiContractAnalyst.analyzeContractConcise(...)`（更精简，但仍可能超长）
  - 代码位置：[AnalysisService.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java#L367-L389)
- 当前模型输出上限默认 2048：
  - 配置位置：[application.yml](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/resources/application.yml#L28-L39)
  - `langchain4j.open-ai.chat-model.max-tokens: ${DASHSCOPE_CHAT_MAX_TOKENS:2048}`

## Goals / Success Criteria
- SSE 初次分析不再因 JSON 截断导致 500/`error` 事件。
- 正常合同在默认配置下能稳定生成“可解析 JSON”。
- 对极端长合同：即使首次生成失败，也能自动降级并在二次/三次尝试后得到可解析的最小结构化结果（至少 summary/评分/等级 + 精选风险条目）。

## Decisions（来自用户确认）
- 修复策略：提高 max-tokens（推荐方案）。
- 条数上限：动态降级（先 5，失败降到 3）。

## Proposed Changes（文件级别，含 why/how）
### 1) 提高默认 max-tokens（避免硬截断）
**File**: [application.yml](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/resources/application.yml)
- 将 `DASHSCOPE_CHAT_MAX_TOKENS` 的默认值从 `2048` 提升到 `4096`（仍可通过环境变量覆盖）。
- Why：从日志与异常形态看，主要是输出长度超过上限导致截断；提高上限是最直接的“根因修复”。

### 2) 增加“最小输出”降级提示词（3 条版本）
**File**: [AiContractAnalyst.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/ai/AiContractAnalyst.java)
- 新增一个方法 `analyzeContractMinimal(...)`（或类似命名），SystemMessage 强制更短：
  - `party_*_risks` 最多 3 条；`suggestions` 最多 3 条
  - 更严格的长度约束：`clause_text` ≤ 120 字；`risk_description/legal_basis/suggestion` ≤ 80 字
  - 继续保持：只输出 JSON、括号闭合自检、risk_level 只能 高/中/低、risk_type 禁止占位词
- Why：即使提高 max-tokens，极端合同仍可能超长；提供“最小可用结构化结果”的确定性兜底。

### 3) 扩展后端重试逻辑为“动态降级：5 → 3”
**File**: [AnalysisService.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java)
- 改造 `generateAndSaveStructuredResultWithRetry(...)`：
  1) try：`analyzeContract(...)`（5 条）
  2) catch `JsonProcessingException`：`analyzeContractConcise(...)`（仍 5 条但更短）并解析
  3) 若仍抛 `JsonProcessingException`：执行第二次降级重试 `analyzeContractMinimal(...)`（3 条）并解析
- 同步调整重试用的 context 截断参数（越往后越短）：
  - 第 1 次：retrieved 6000 / graph 3000（现状）
  - 第 2 次：retrieved 3000 / graph 1000（现状）
  - 第 3 次：retrieved 1500 / graph 500（新增）
- SSE 状态文案：
  - 第一次失败：维持现有 `retrying_analysis` 文案
  - 第二次失败后进入最小版：新增状态（例如 `retrying_analysis_minimal`）提示“正在生成最小可解析结果”

### 4) 更新用户可见错误提示（与实际重试次数一致）
**File**: [AnalysisService.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java)
- `toUserFacingError(...)` 中的文案目前写“已自动重试一次”，需更新为“已自动重试并降级生成更精简结果，仍失败”之类（匹配 2 次重试/降级逻辑）。

### 5) 更新进度文档
**File**: [PROGRESS.md](file:///e:/code/repository/ContractLens/PROGRESS.md)
- 记录“结构化 JSON 截断稳定性修复：提高 max-tokens + 动态降级重试”。

## Edge Cases / Failure Modes
- 模型服务中途断连/超时：仍会报错，但不应以 JSON 解析错误形式出现；沿用现有 timeout 用户提示。
- 返回内容含多余前后缀/Markdown：继续依赖 `JsonSanitizer.extractJsonObject(...)` 清洗后再解析。
- 依然可能截断：第三次最小版输出应显著更短，理论上能在 4096 上限内稳定返回；若仍失败，说明网络/服务端异常为主，需要另行排查（但不会再误判为“正常长输出”）。

## Verification Steps
- 后端编译与测试：运行 `mvn test`（至少确保编译通过）。
- 手工验证（建议用同一份长合同复现用例）：
  - 触发 `POST /api/analysis/contracts/{id}/stream` 初次分析，确认不再出现 `JsonEOFException`。
  - 人为把 `DASHSCOPE_CHAT_MAX_TOKENS` 设回较小值（如 1024）做一次回归，确认会自动降级到 minimal 并仍能得到可解析 JSON（用于验证降级逻辑有效）。

## Assumptions
- 当前使用的 DashScope 兼容模式模型遵循 `max-tokens` 输出上限，且超过会截断输出（与现象一致）。
- 现有前端能适配“风险条目从 5 降为 3”的情况（只是展示条目更少，不影响页面结构）。
