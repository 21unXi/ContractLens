## Summary

搭建并验证 ContractLens 的 RAG 知识库链路可用，目标是实现你描述的闭环：

1. 往 MySQL `knowledge_docs` 表批量写入法律条文/风险清单示例数据
2. 调用 `POST /api/knowledge/rebuild` 将数据 ingest 到 Chroma
3. `AnalysisService.retrieveContext()` 的检索命中生效（`retrieved_context` 有内容，`/api/knowledge/status` probe 有命中数）

## Current State Analysis（基于仓库现状）

* 向量检索链路已存在：`AnalysisService.retrieveContext()` → `retriever.findRelevant(contractContent)` 拼接 `retrievedContext`。

* 知识库数据源已存在：MySQL 表 `knowledge_docs`（实体 `KnowledgeDoc`）。

* ingest 入口已存在：`POST /api/knowledge/rebuild` 会把 `knowledge_docs` 全量 ingest 到 EmbeddingStore（Chroma）。

* Chroma 配置已存在：`langchain4j.chroma.embedding-store.url=http://localhost:8000`，collection=`rental_contract_knowledge`。

* 当前缺口：

  * 项目内缺少“批量插入示例 knowledge\_docs 数据”的一键机制（需要手工 SQL 或写一个 seed 接口/脚本）。

  * 是否已建立向量库取决于：Chroma 是否运行 + 是否执行过 rebuild。

## Proposed Changes

### 1) 如果你“手动插入 MySQL 数据”，后端不需要再加 seed 功能

你手动把示例 SQL 插入到 `knowledge_docs` 后，本计划不再强制新增 `/api/knowledge/seed` 或 SQL 种子文件（可作为可选项，后续需要“一键初始化演示数据”时再补）。

接下来唯一必须做的是：

1. 启动 Chroma（向量库服务）
2. 调用 `POST /api/knowledge/rebuild` 执行 ingest
3. 用 `GET /api/knowledge/status` / `GET /api/knowledge/docs` 验证数据与检索可用

### 2) Chroma 启动方式（两种任选其一）

**方式 A：Docker（推荐，最省事）**

* 运行（临时数据，重启会丢）：

  * `docker run --rm -p 8000:8000 chromadb/chroma`

* 运行（持久化数据，推荐）：

  * `docker run -p 8000:8000 -v chroma-data:/chroma/chroma chromadb/chroma`

**方式 B：Python（本地运行）**

* 安装并启动（示例）：

  * `pip install chromadb`

  * `chroma run --host 0.0.0.0 --port 8000`

启动后目标是让后端配置中的 `langchain4j.chroma.embedding-store.url`（默认 `http://localhost:8000`）能连通。

### 3) 端到端验证与诊断信息

不改动分析主链路，只补齐验证路径：

* `GET /api/knowledge/status`：确认

  * `knowledgeDocsCount > 0`

  * `embeddingStoreUrl/collection` 有值

  * `retrieverProbeHitCount` 有数值且 `retrieverProbeError` 为空

* `GET /api/knowledge/docs`：能看到新插入文档标题

* `POST /api/knowledge/rebuild`：执行 ingest 后再次看 status

* 触发一次分析（结构化或 SSE）：确认 `analysis_results.retrieved_context` 不为空（或至少明显包含法律条文/风险清单片段）

## Assumptions & Decisions

* 采用“手动触发 rebuild”（你之前已确认），不做启动自动 ingest。

* seed 数据以“租房合同”场景为主，严格限制在项目边界内（不扩展到通用合同）。

* 不在本次计划中引入 GraphRAG/Neo4j 的真实检索落地（仍保留为后续 Phase）。

## Verification Steps（你手动测试清单）

1. 确保 Chroma 可用（localhost:8000）。若未启动，先启动 Chroma 服务。
2. 登录获取 JWT（前端或 `POST /api/auth/login`）。
3. 手工执行你给的 INSERT，把数据写入 MySQL `knowledge_docs`。
4. 调用 `GET /api/knowledge/status`，确认 `knowledgeDocsCount` 增加。
5. 调用 `POST /api/knowledge/rebuild`，等待完成。
6. 再次调用 `GET /api/knowledge/status`，确认 `retrieverProbeHitCount` 有值且无 error。
7. 上传一份租房合同并触发分析，确认 `retrieved_context` 有内容（或从日志/DB 查看）。

## Notes（安全与配置）

* 当前仓库 `application.yml` 含敏感配置（API Key、DB 密码）属于风险项；本计划不处理该问题，但建议后续迁移到 `application-local.yml`/环境变量并加入 `.gitignore` 规则。

## Required Doc Update

* 按项目规则：完成实现后更新 `PROGRESS.md`，增加“RAG 知识库 seed/验证流程”条目。

