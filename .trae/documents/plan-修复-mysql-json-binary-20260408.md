## Summary
修复后端保存 `analysis_results` 时的 `DataIntegrityViolationException: Cannot create a JSON value from a string with CHARACTER SET 'binary'`，并同时纳入你之前提到的 `JsonParseException`（```json fence）与 `AccessDenied`（/error、OPTIONS 预检）问题的最终闭环。

## Current State Analysis
### 1) MySQL JSON 字段写入失败（本次新增报错）
- 报错点：`AnalysisService.generateAndSaveStructuredResult()` 调用 `analysisResultRepository.save(result)` 写库失败。
- SQL 片段显示写入字段包含多个 JSON 列：`party_lessor_risks/party_tenant_risks/suggestions/contract_tags/clause_conflicts`。
- 数据库表定义（repo 内 `database/init.sql`）：这些列是真正的 `JSON` 类型，并且表/库字符集为 `utf8mb4`。见 [init.sql](file:///e:/code/repository/ContractLens/database/init.sql#L36-L53)
- 代码实体映射（`AnalysisResult`）：这些 JSON 列字段当前被 `@Lob` + `@Column(columnDefinition = "JSON")` 标注。见 [AnalysisResult.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/entity/AnalysisResult.java#L31-L58)
- 推断根因：`@Lob` 促使 Hibernate 以 BLOB/二进制方式绑定参数，MySQL 在 JSON 列上收到 `binary` 字符集的参数，从而拒绝创建 JSON 值（即报错中的 `CHARACTER SET 'binary'`）。

### 2) JsonParseException（```json fence）
- LLM 可能返回 ```json ... ``` 包裹的 JSON，Jackson 直接 `readTree()` 会在首字符反引号处失败。
- 已有修复思路：对 AI 输出做 fence 清洗 + `{...}` 兜底提取，并收紧提示词要求只输出纯 JSON。

### 3) AccessDenied（403）
- 典型来源：Spring Boot 的 `/error` 未放行导致异常链路再次被 Security 拦截；或浏览器发 OPTIONS 预检被 `authenticated()` 拦截。
- 当前 `SecurityConfig` 需明确放行 `/error` 与 `OPTIONS /**` 并适当补齐 CORS `allowedHeaders`。

## Proposed Changes
### A) 正确映射 MySQL JSON 列（核心修复）
**目标**：让 Hibernate 以 JSON/TEXT（UTF-8）方式绑定参数，避免 binary charset。

1) 调整实体映射：`AnalysisResult.java`
- 对 JSON 列字段移除 `@Lob`
- 使用 Hibernate 6 推荐写法：
  - `@JdbcTypeCode(SqlTypes.JSON)`（需要 `org.hibernate.annotations.JdbcTypeCode` 与 `org.hibernate.type.SqlTypes`）
  - `@Column(columnDefinition = "json")`（或保持 `JSON`，但建议统一为小写）
- 适用字段：
  - `partyLessorRisks`
  - `partyTenantRisks`
  - `suggestions`
  - `contractTags`
  - `clauseConflicts`
- TEXT 列（`summary/retrievedContext/graphContext`）可保留为 `@Lob + TEXT` 或改为普通 `@Column(columnDefinition="TEXT")`；关键是不要把 JSON 列标为 LOB。

2)（可选但推荐）增加写库前的 JSON 有效性校验
- 在 `AnalysisService.mapJsonToAnalysisResult()` 对要写入 JSON 列的字符串做一次 `objectMapper.readTree(...)` 验证，保证写入的一定是合法 JSON（数组/对象均可）。
- 若非法，降级为 `[]` 或 `null`，并记录 warn 日志（避免把非法 JSON 送到 MySQL JSON 列触发异常）。

### B) 彻底兜住 ```json fence（保持现有修复方向）
涉及文件：
- `contractlens-backend/src/main/java/com/contractlens/util/JsonSanitizer.java`
- `contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java`
- `contractlens-backend/src/main/java/com/contractlens/service/ai/AiContractAnalyst.java`
要点：
- `AnalysisService` 解析失败时使用 `JsonSanitizer.extractJsonObject()` 清洗后重试
- 提示词追加“只输出纯 JSON，不要 Markdown fence/解释文字”

### C) 修复 AccessDenied（403）
涉及文件：
- `contractlens-backend/src/main/java/com/contractlens/config/SecurityConfig.java`
要点：
- 放行 `/error`
- 放行 `OPTIONS /**`
- CORS `allowedHeaders` 至少包含 `Authorization, Content-Type, Accept`

### D) 测试与验证
1) 单测（最小）
- `JsonSanitizerTest`：覆盖纯 JSON、```json fence、无 language fence、前后缀包裹。
- （新增）JSON 列写入字符串的“合法 JSON”校验测试（如果实现了 B-2 的校验/降级逻辑）。

2) 手工回归（你提到你会手工测）
- 触发一次初次流式分析：`POST /api/analysis/contracts/{id}/stream`
  - 预期：不再出现 JsonParseException；done 事件能正常返回；数据库能插入/更新 `analysis_results`
- 触发一次非流式分析：`POST /api/analysis/contracts/{id}`
  - 预期：同样可落库
- 前端跨域：观察浏览器 Network 中 OPTIONS 预检不再 403；服务端不再刷 `AccessDeniedException`

## Assumptions & Decisions
- MySQL 版本支持 JSON（MySQL 5.7+/8.x），且 `analysis_results` 表 JSON 列为真实 JSON 类型（与 `database/init.sql` 一致）。
- 采用 Hibernate 6 的 `@JdbcTypeCode(SqlTypes.JSON)` 作为 JSON 列映射方式，避免引入额外第三方 JSON 类型库。
- 由于本环境缺少 Maven，可在你的本地/CI 中运行测试与打包；本计划提供验证步骤但不在规划阶段执行。

