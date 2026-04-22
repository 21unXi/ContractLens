## 目标
解决两类后端报错：
- `JsonParseException`：LLM 返回被 Markdown 代码块包裹的 JSON（```json ... ```），导致 `ObjectMapper.readTree()` 解析失败。
- `AccessDeniedException`：异常链路/预检请求落到 Spring Security 的受保护路径时返回 403，导致前端看到 Access Denied（常见于 `/error` 未放行或 OPTIONS 预检未放行）。

## 现状定位（已确认）
- JSON 解析失败发生在 `AnalysisService.mapJsonToAnalysisResult()`，输入字符串以 ```json 开头。
- 安全配置当前仅放行 `/api/auth/**`，其余全部 `authenticated()`；未显式放行 `/error` 与 OPTIONS。

## 改造方案
### 1) 让结构化 JSON 解析对“代码块包裹”具备容错
1. 在 `AnalysisService` 增加一个“模型 JSON 清洗/提取”方法（例如 `extractJsonObject(String raw)`）：
   - 优先尝试直接 `readTree(raw)`
   - 失败后执行清洗：
     - 去掉 Markdown fence：去除开头 ``` 或 ```json 及结尾 ```
     - 兜底：截取第一个 `{` 到最后一个 `}`（只支持对象输出）
   - 再次 `readTree(cleaned)`；若仍失败，抛出带有更友好信息的异常（避免日志打印敏感内容，最多打印前 N 字符）
2. 在 `generateAndSaveStructuredResult()` 调用 `mapJsonToAnalysisResult()` 前应用该清洗逻辑，确保落库与 SSE 初次分析不会被 fence 破坏。

### 2) 收紧/修正模型提示词，减少 fence 概率
在 `AiContractAnalyst.analyzeContract` 的 `@SystemMessage` 里补充硬性约束：
- “只输出 JSON，不要使用 Markdown 代码块，不要输出解释文字/前后缀”
这样从源头降低 ```json 包裹概率，但仍保留第 1) 的容错作为保险。

### 3) 修复 Access Denied（错误页与预检）
在 `SecurityConfig` 增加放行规则：
- 放行 `/error`（避免异常时 Spring Boot 错误处理再次被 Security 拦截）
- 放行 `OPTIONS /**`（避免跨域预检被 `authenticated()` 拦截导致 403）
并（可选）扩充 CORS `allowedHeaders` 为 `Authorization, Content-Type, Cache-Control` + 常用头（如 `Accept`），减少浏览器差异导致的预检失败。

### 4) 补充测试用例（最小）
新增一个单测类，覆盖以下输入：
- 纯 JSON 字符串
- ```json ... ``` 包裹
- ``` ... ``` 包裹
- 前后带少量解释文字但中间包含 `{...}` 的情况（走 `{` 到 `}` 截取兜底）
断言：最终能解析并取到 `risk_score/risk_level` 等字段。

## 验证方式
- 后端：运行单测；手工调用一次 `/api/analysis/contracts/{id}` 与 `/stream`（确保不会因 fence 导致 500）。
- 前端：保持不改，仅观察 SSE 初次分析能正常落地结构化摘要。

## 实施顺序
1. 实现 JSON 清洗/提取并接入分析流程
2. 更新提示词
3. 调整 SecurityConfig 放行项
4. 增加单测并跑通
