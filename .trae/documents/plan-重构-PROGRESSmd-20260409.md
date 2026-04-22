## Summary
重构 [PROGRESS.md](file:///e:/code/repository/ContractLens/PROGRESS.md) 的内容与结构，使其准确反映当前代码库的真实状态，并补齐：
- 清晰的“文件结构 + 模块职责”说明（前端/后端/数据库/AI）
- “已实现功能”按模块与端点梳理（含流式对话分析、知识库 status/docs、合同删除等）
- “当前进度/下一步”更贴近实际，不再停留在旧阶段描述

## Current State Analysis（基于仓库现状）
### 1) PROGRESS.md 存在的信息偏差
- 记录仍停留在 2026-04-07，且阶段描述与当前代码不一致（例如前端新增的 History/Settings/Knowledge 页面、SSE 对话式分析、合同删除等未体现）。
- 文档中“文件结构”段落未覆盖新增文件与新模块职责。

### 2) 当前仓库真实结构（抽样确认）
**前端**（Vue 3 + Vite）
- `contractlens-frontend/src/api/`：`http.js`（统一 axios 实例）、`contract.js`（合同 API + SSE 解析）、`knowledge.js`（知识库 API）
- `contractlens-frontend/src/stores/`：`auth.js`（token）、`analysisChat.js`（会话/流式状态）
- `contractlens-frontend/src/views/`：Dashboard/History/Settings/Knowledge/Login/Register

**后端**（Spring Boot 3 + JWT）
- `controller/`：Auth/Contract/Analysis/Knowledge/KnowledgeStatus
- `service/`：Contract/Analysis(含 SSE)/Knowledge(含 ingest)/FileStorage
- `util/`：JWT、`JsonSanitizer`（fenced JSON 容错）
- `config/`：Security + 401/403 JSON handlers + CORS + AiConfig(retriever/ingestor)

## Proposed Changes（PROGRESS.md 的新结构）
将 PROGRESS.md 重写为以下结构（保持 Markdown 简洁可扫读）：
1) 顶部：最后更新日期 + 总进度（按“用户可见能力”而不是旧 phase）
2) 已完成功能（按模块分组）
   - Backend：认证、合同（上传/列表/获取/删除）、分析（非流式 + SSE）、知识库（status/docs/rebuild）、安全/CORS、JSON 容错
   - Frontend：认证流、控制台对话分析、结构化摘要视图、历史/设置/知识库页面、动态统计、删除合同
3) API 概览（按资源分组，列出 method/path/说明/鉴权）
4) 文件结构（真实 tree）+ 每个目录/关键文件职责（点到为止，避免展开代码细节）
5) 已知问题/限制（例如：聊天会话后端当前为内存态；GraphRAG/Neo4j 未接入到分析链路；知识库文档来源为 DB 表）
6) 下一步计划（短列表，贴近你当前目标：知识库数据治理、历史分析结果聚合、GraphRAG、权限/审计等）

## Content To Include（必须覆盖的近期改动点）
- 统一 axios 实例与 token 注入、401 跳转策略（前端）
- SSE 对话式分析接入（status/answer/done/error），Dashboard 对话面板
- 合同删除链路（后端 DELETE + 前端按钮）
- 知识库 status/docs/rebuild 与前端知识库页面
- 侧边栏导航与历史/设置页面落地
- 后端 JSON fence 容错（JsonSanitizer）与 Security 异常处理（401/403 JSON、ASYNC/ERROR dispatcher）
- MySQL JSON 字段映射调整（AnalysisResult JSON 列）

## Implementation Steps
1) 对照当前仓库结构与端点，列出“真实文件结构树”（精简但完整）
2) 梳理“已实现功能”列表与 API 概览（以 controller 为准，避免漏项）
3) 重写 PROGRESS.md：更新日期、修正阶段描述、补齐模块职责与能力列表
4) 本地校对：确保文档中的路径、端点、能力与仓库一致（不写虚构目录，如 README 中的 knowledge/ scripts 若不存在则不写）

## Verification
- PROGRESS.md 中提及的目录/文件在仓库中真实存在
- PROGRESS.md 中提及的 API 在 controller 中能找到对应方法
- 文档语义清晰：读者无需看代码也能知道“做了什么/在哪里/怎么验证”

## Out of Scope
- 不在本次重构中引入新的业务功能（仅重写 PROGRESS.md 文档）
- 不对 README/PROJECT.md 做同步更新（除非你后续再要求）

