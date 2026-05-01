## Summary
按课程要求为 ContractLens 补齐一套 **pytest** 测试体系（≥10 个用例、覆盖≥2个层级），并在仓库内提供 **requirements_test.py** 作为“可执行需求规约”，以及可生成 **测试报告（Markdown + JUnit XML）** 的脚本。

选择的覆盖层级（基于项目真实形态）：
- **工具测试（Tool）**：离线验证 SSE 事件解析、关键词高亮/转义、风险排序/筛选等“前端核心展示逻辑”的输入→输出稳定性。
- **安全测试（Security）**：验证除 `/api/auth/**` 以外的接口必须 JWT；验证越权读取合同返回 404（不泄露存在性）。
- **集成测试（Integration）**：在后端已运行的前提下，走真实 HTTP 端到端链路：注册→登录→上传合同→列表→读取 result/chat-history→删除。
- **检索测试（Retrieval，轻量）**：对 `/api/knowledge/status` 的字段完整性与探测结果一致性做断言（不引入 RAGAS，避免额外依赖与不稳定性）。

## Current State Analysis（仓库现状已确认）
- 仓库目前 **没有任何 Python/pytest 文件**；后端已有 JUnit 测试，但不满足“pytest”要求。
- 后端核心接口（真实可测）：
  - Auth：`POST /api/auth/register`、`POST /api/auth/login`
  - Contracts：`POST /api/contracts/upload`、`GET /api/contracts`、`GET /api/contracts/{id}`、`DELETE /api/contracts/{id}`
  - Analysis：`GET /api/analysis/contracts/{id}/result`（无结果时返回 `null`）、`GET /api/analysis/contracts/{id}/chat/history`
  - Knowledge：`GET /api/knowledge/status`
- Spring Security 规则：仅 `/api/auth/**`、`/error`、`OPTIONS /**` 放行，其余均需 JWT（可用于安全测试）。

## Goals / Success Criteria
- `pytest` 用例数 **≥ 10** 且默认可运行（后端不可达时，集成/安全/检索相关用例应清晰地跳过或给出可操作的失败提示）。
- 覆盖 **≥ 3 层级**（工具 + 安全 + 集成；附加轻量检索层）。
- 提交 `requirements_test.py`：用测试用例精确定义系统“应该做什么”（至少覆盖：鉴权边界、合同 CRUD 主流程、result/chat-history 的返回约定、status 字段约定）。
- 生成测试报告：
  - `junit.xml`（便于平台采集）
  - `TEST_REPORT.md`（包含通过率统计与关键评估数据：通过/失败/跳过数、基础指标如 knowledge.status 的 returnedSegments/latency 分布）

## Decisions（已确认）
- 允许 pytest 依赖正在运行的后端（默认 `http://localhost:8080`），用于真实 HTTP 集成测试。
- 尽可能覆盖项目用到的方面，并最终生成一个测试报告。

## Proposed Changes（文件级别）
> 说明：以下均为新增 Python 测试资产，不影响现有 Java/Vue 构建；不引入敏感信息；默认通过环境变量配置 base_url。

### 1) 新增 Python 测试工程骨架
**新增目录**：`e:\code\repository\ContractLens\contractlens-pytests\`
- `requirements-test.txt`
  - 最小依赖：`pytest`、`requests`
- `pytest.ini`
  - 定义 markers：`tool`, `security`, `integration`, `retrieval`
  - 默认输出 `junit.xml`（或在报告脚本中指定）
- `requirements_test.py`
  - 以“需求规约”形式组织断言：哪些端点必须鉴权、越权应返回什么、result 的 null 语义等。

### 2) 新增测试辅助代码（可复用、可读）
**新增目录**：`contractlens-pytests/contractlens_testkit/`
- `api_client.py`
  - 负责：base_url、register/login、带 token 的请求封装、上传合同（multipart）、CRUD 等
  - 避免硬编码账号：使用随机用户名（时间戳/UUID），每次运行自建用户
- `sse.py`
  - SSE 文本块解析（对应前端 `contract.js/parseSseResponse` 的约定：event/data、多行 data 拼接、空行分隔）
- `text.py`
  - 关键词高亮：**先 HTML 转义再高亮**，避免 XSS；行为与前端 ClauseCard 的实现保持一致
- `risk.py`
  - 风险排序与筛选：高→中→低；按关键词命中 clause_text/risk_description

### 3) 测试用例设计（≥10）
**新增目录**：`contractlens-pytests/tests/`

#### A. 工具测试（离线、稳定）
1. `test_sse_parser_parses_status_answer_done`：输入一段 SSE 原始文本 → 输出事件序列正确（status/answer/done）
2. `test_sse_parser_handles_multiline_data`：多行 `data:` 拼接后 JSON 仍可解析
3. `test_highlight_escapes_html_then_marks_keywords`：含 `<script>` 等特殊字符时不注入；关键词被 `<mark>` 包裹
4. `test_risk_sorting_high_medium_low`：risk_level 排序稳定
5. `test_risk_keyword_filter_matches_clause_or_desc`：关键词过滤命中 clause_text 或 risk_description

#### B. 安全测试（需要后端运行）
6. `test_requires_jwt_for_contracts_list`：无 token 调 `GET /api/contracts` 应为 401
7. `test_requires_jwt_for_knowledge_status`：无 token 调 `GET /api/knowledge/status` 应为 401
8. `test_cross_user_contract_access_returns_404`：A 用户上传合同，B 用户读取该 id 返回 404（不泄露存在性）

#### C. 集成测试（需要后端运行）
9. `test_register_login_returns_jwt_like_token`：注册→登录成功，token 形态符合 JWT（三段 base64url）
10. `test_upload_contract_then_list_contains_it`：上传 txt 合同后，列表包含该合同
11. `test_get_analysis_result_initially_null`：未触发分析时 `GET /api/analysis/contracts/{id}/result` 返回 `null`
12. `test_chat_history_initially_empty`：`GET /api/analysis/contracts/{id}/chat/history` 初始为空数组
13. `test_delete_contract_then_not_in_list`：删除后列表不再包含

#### D. 检索测试（轻量，基于项目已有 status 探测）
14. `test_knowledge_status_schema_and_probe_fields`：
   - 必含字段：ragMode/knowledgeDocsCount/retrieverProbeReturnedSegments/lightRagProbeLatencyMs 等
   - ragMode 为 `lightrag` 或 `legacy`
   - 若 `knowledgeDocsCount > 0` 且 `ragMode == lightrag` 且 `lightRagOk == true`，则 `lightRagProbeReturnedChunks >= 1`

> 说明：B/C/D 类用例在执行前会做后端可达性探测；不可达时标记为 skip，并在报告中统计。

### 4) 测试报告生成
**新增文件**：`contractlens-pytests/run_report.py`
- 运行 pytest 并生成：
  - `contractlens-pytests/reports/junit.xml`
  - `contractlens-pytests/reports/TEST_REPORT.md`
- 报告内容：
  - 总用例数、通过/失败/跳过数量、通过率
  - 分层级统计（tool/security/integration/retrieval）
  - 关键评估数据：`/api/knowledge/status` 的 returnedSegments 与 latencyMs 采样值（若接口可用）

## Assumptions & Constraints
- 测试环境具备 Python 3.10+（或 3.9+ 亦可，最终以项目环境为准）。
- 后端若已成功启动，说明关键配置（JWT_SECRET、DashScope key 等）已满足；本次 pytest 默认 **不强制**触发 AI 分析（避免成本与不稳定性），核心走合同管理 + result/history/status 约定。
- 不在仓库中写入任何 API Key/密码；测试用户为动态生成的随机用户名。

## Verification（实施后如何验收）
- 本地：
  - 安装测试依赖：在 `contractlens-pytests` 下安装 `requirements-test.txt`
  - 启动后端（按现有方式）
  - 运行：`python run_report.py`
  - 期望：生成 `reports/TEST_REPORT.md` 与 `reports/junit.xml`，且 pytest 用例数 ≥10
- 代码层面：
  - 确保所有新增文件仅为 Python 测试资产，不影响现有后端/前端构建
