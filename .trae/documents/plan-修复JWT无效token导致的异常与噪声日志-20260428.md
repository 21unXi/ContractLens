# /plan：修复 JWT 无效 token 导致的异常与噪声日志（前后端协同）

## Summary
当前后端会出现两类 JWT 相关问题：
- `MalformedJwtException: Invalid compact JWT string ... Found: 0`：说明服务端尝试解析了**非 JWT 格式**的 token（不含 `.` 分隔）。
- `JWT Token does not begin with Bearer String`：说明请求带了 `Authorization` 但不是 `Bearer <token>`（或根本没带也在 warn，日志噪声较大）。

目标：
1) 无论客户端传了什么奇怪的 `Authorization`，后端都不应抛栈/中断请求链（应当“忽略无效 token → 以未登录处理”，最终由鉴权层返回 401/403）。  
2) 前端不再把 `null/undefined/空串/非 JWT` 写入 localStorage 或塞进 Authorization 头，避免反复触发后端解析异常。  
3) 降低无意义 warn 日志（未登录请求是常态，不应刷屏）。

## Current State Analysis（已基于仓库核对）
- 后端过滤器 [JwtRequestFilter](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/config/JwtRequestFilter.java)：
  - 仅捕获 `IllegalArgumentException` 和 `ExpiredJwtException`，未捕获 `io.jsonwebtoken.JwtException`（`MalformedJwtException` 属于此类），因此会直接抛出异常栈。
  - 当请求未带 `Authorization` 或不是 `Bearer ` 时，会 `warn`，导致日志噪声。
  - 即使 `Bearer ` 后面是空串，也会继续解析（`substring(7)` 结果为空）并触发异常。
- JWT 解析工具 [JwtUtil](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/util/JwtUtil.java)：
  - `getAllClaimsFromToken()` 直接 `parseSignedClaims(token)`，对空串/非 JWT 会抛 `MalformedJwtException`。
- 前端 token 写入与默认头：
  - [auth store](file:///e:/code/repository/ContractLens/contractlens-frontend/src/stores/auth.js) 在 `login()` 里不校验 `response.data.token`，若 token 异常（null/空/非 JWT）会直接写入 localStorage。
  - [http.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/api/http.js) `setAuthToken(token)` 只判断 truthy；若 localStorage 中意外存在 `"null"` 字符串，会被当成真值并写入 `Authorization: Bearer null`。
  - [contract.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/api/contract.js) 的 `streamAnalyzeContract` 也会从 localStorage 读取 token 并拼接 Authorization。

## Proposed Changes

### 1) 后端：JWT 过滤器“只在 token 可信时才解析”，并捕获所有 JWT 解析异常
文件：
- [JwtRequestFilter.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/config/JwtRequestFilter.java)

改动：
- 仅当 `Authorization` 存在且以 `Bearer ` 开头，并且提取出的 token：
  - 非空
  - 形如 `xxx.yyy.zzz`（至少包含两个 `.`）
  才调用 `jwtUtil.getUsernameFromToken(...)`。
- 捕获 `io.jsonwebtoken.JwtException`（覆盖 `MalformedJwtException` 等）并将其视为“无效 token”，不抛栈、不设置认证信息，继续 filter chain。
- 将“未带 Authorization / 不是 Bearer”的 `warn` 降为 `debug` 或直接不打（避免日志刷屏）。

### 2) 前端：严格校验 token 再写入/再设置请求头
文件：
- [auth.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/stores/auth.js)
- [http.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/api/http.js)
- [contract.js](file:///e:/code/repository/ContractLens/contractlens-frontend/src/api/contract.js)

改动：
- 增加一个小工具函数 `isLikelyJwt(token)`（例如：字符串、trim 后非空、包含两个 `.`）。
- `auth.login()`：
  - 若后端未返回合法 token：抛出错误并不写入 localStorage/不设置默认头。
- `syncAuthToken()` / `setAuthToken()`：
  - 若 localStorage 里是 `null/undefined/空串/不符合 JWT 格式`：清理 localStorage 并删除默认 Authorization 头。
- `streamAnalyzeContract()`：
  - 发送前再次校验 token，避免把垃圾 token 发送到 SSE 请求里。

### 3) 验证与验收
- 手工验证（浏览器）：
  - localStorage token 为空/为 `"null"`/为 `"abc"` 时访问任意 API：后端不再输出 `MalformedJwtException` 栈；前端应自动清理 token 并跳转登录（在 401 场景）。
  - 正常登录后访问 API：一切如常。
- 日志验证：
  - 未登录访问时不再出现大量 `JWT Token does not begin with Bearer String` warn。

## Assumptions & Decisions
- 无效/畸形 token 的处理策略：后端**忽略并继续**（不在 filter 内主动返回响应），由 Spring Security 的鉴权链统一给出 401/403 JSON。
- JWT 格式校验仅做轻量快速判断（`.` 个数），不做昂贵解析；真正验证仍在 `JwtUtil.validateToken`。

