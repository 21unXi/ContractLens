# Plan：修复 LightRAG `/query` 422（body 缺失）并打通“同步到 LightRAG”

## Summary

当前点击“同步到 LightRAG”后出现：

`422 Unprocessable Entity: {"detail":[{"type":"missing","loc":["body"],"msg":"Field required","input":null}]}`

这表示 **LightRAG Server 收到的 `POST /query` 请求体为空**（FastAPI/Pydantic 判定整个 body 缺失），因此无法解析 `QueryRequest`。

本计划将修复 ContractLens 后端对 LightRAG 的 HTTP 调用，确保请求体按 LightRAG 期望的 JSON 结构发送；并补齐可观测性与安全配置的默认值，最终让：

1) `/api/knowledge/status` 的 LightRAG probe 不再 422  
2) “同步到 LightRAG”（`POST /api/knowledge/rebuild`）后能正常探测、可继续用于合同分析检索  

## Current State Analysis（基于现场与代码/日志）

### 现象与证据

- LightRAG 服务端日志显示多次：
  - `"POST /query HTTP/1.1" 422`
  - 返回 detail 为 `loc=["body"]` 缺失，说明 body 为空（而不是 body 内字段缺失）
  - 见 `e:\code\repository\ContractLens\lightrag\lightrag.log`

### LightRAG `/query` 的真实入参要求（代码即真相）

- LightRAG Server 使用 FastAPI，`POST /query` 接收 `QueryRequest`（Pydantic Model），必填字段 `query: str`
  - 见 `query_routes.py`：[query_routes.py](file:///e:/code/repository/ContractLens/venv/Lib/site-packages/lightrag/api/routers/query_routes.py#L16-L33)

### ContractLens 当前调用方式（可能导致 body 为空）

- ContractLens 使用 Spring `RestClient` 调用：
  - `.contentType(application/json)` + `.body(payload)`（payload 是 `Map<String,Object>`）
  - 见 [LightRagClient](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/service/lightrag/LightRagClient.java#L48-L83)
- 推断风险点：
  - `RestClient.builder()` 是手动创建的 Builder，不一定携带 Spring Boot 自动配置的 message converters；
  - 在 message converter 缺失/不匹配时，可能导致请求仍发出但 body 未被写入（服务端看到空 body → 422）。

## Proposed Changes（实现方案与改动点）

### 1) 修复 LightRAG 请求体写入（核心）

目标：确保 `POST /query` 一定携带 JSON body（至少包含 `query/mode/only_need_context`）。

改动文件：
- `contractlens-backend/src/main/java/com/contractlens/service/lightrag/LightRagClient.java`

实施要点（二选一或组合，优先 A）：
- A. 使用 Spring Boot 自动装配的 `RestClient.Builder`
  - 构造器注入 `RestClient.Builder builder`，用 `builder.baseUrl(...).build()` 创建 client
  - 好处：自动携带 Jackson message converter，Map 序列化为 JSON 更可靠
- B. 强制自行序列化 payload 为 JSON 字符串
  - `String json = objectMapper.writeValueAsString(payload)`
  - `.body(json)` 发送字符串，避免 message converter 依赖

同时补齐：
- 明确 `Accept: application/json`
- 错误时记录更多诊断信息（HTTP status + response body），便于排查

### 2) 对齐 LightRAG 返回结构的解析（次要但建议同步）

LightRAG `/query` 的响应模型默认字段是 `response`（而不是 `context`）。

改动文件：
- `contractlens-backend/src/main/java/com/contractlens/service/lightrag/LightRagClient.java`

实施要点：
- `parseQueryResponse` 优先读取 `response` 字段作为主文本
- `only_need_context=true` 时，LightRAG 可能仍返回 `response`（语义可能是“返回检索上下文”），因此不强依赖 `context` 字段

### 3) 改善 status 探测信息（便于你验收）

目标：status 页面能明确显示 LightRAG 是否可用、probe 是否成功、失败原因是否为“请求体缺失/鉴权/路径不对”等。

改动文件：
- `contractlens-backend/src/main/java/com/contractlens/controller/KnowledgeStatusController.java`
- `contractlens-backend/src/main/java/com/contractlens/dto/KnowledgeStatusResponse.java`

实施要点：
- probe 失败时返回更完整的 `lightRagProbeError`（包含 HTTP status 与响应 body，避免只剩 422 文本）

### 4) 配置安全基线（避免把真实密钥写进默认配置）

你本地为了方便把 `application.yml` 的默认值改成了：
- `DASHSCOPE_API_KEY` 默认是一个 `sk-...` 样式字符串
- `SPRING_DATASOURCE_PASSWORD` 默认是 `root`

这会导致“即使不设置环境变量也会使用看似真实的密钥/密码”，不符合项目的安全约束（避免硬编码敏感信息）。

改动文件：
- `contractlens-backend/src/main/resources/application.yml`

实施要点：
- 把默认密码/Key 的 fallback 改回空值（必须显式通过环境变量或 `application-local.yml` 提供）
- 保留你本地调试可用性：推荐用本地环境变量/`application-local.yml`（已在 `.gitignore`）

## Assumptions & Decisions

- LightRAG Server 已在本机启动且地址为 `http://localhost:9621`（你日志已证明）
- `/query` 路径存在且无需额外鉴权（目前日志返回 422 而非 401/403）
- “同步到 LightRAG”阶段先采用 inputs 目录落盘方式；若 LightRAG 实际需要通过 document API 上传，再追加第二阶段改造（本计划先不扩大范围）

## Verification Steps（实现后你可以按这个验收）

1. 保持 LightRAG Server 运行（9621）
2. 重启 ContractLens 后端
3. 调用 `GET /api/knowledge/status`
   - `ragMode` 为 `lightrag`
   - `lightRagProbeError` 为空
4. 点击前端“同步到 LightRAG”（`POST /api/knowledge/rebuild`）
   - `lightrag/inputs` 下文件存在且更新
5. 再次 `GET /api/knowledge/status`
   - `lightRagProbeReturnedChunks` 与 `lightRagProbeContextChars` 有值（不要求>0，但不能 422）
6. 上传合同并触发分析
   - 后端不再出现 LightRAG 422

