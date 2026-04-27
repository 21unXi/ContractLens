# ContractLens · 租房护航

> 智能租房合同风险审查 —— 用 AI + 法律知识库，让合同风险一目了然

---

## 1. 项目概述

ContractLens · 租房护航是一款专注于**住房租赁合同**的 AI 智能风险审查工具。用户上传租房合同，系统结合“检索增强生成（RAG）”能力，输出结构化风险结果与可读的修改建议。

**功能边界**
- 只做租房合同，不做通用合同审查
- 不做在线签约、支付、社交、移动端 App

---

## 2. 当前实现（以仓库为准）

### 2.1 技术栈

| 层级 | 技术选型 |
|------|----------|
| 前端 | Vue 3 + Vite + Pinia + Axios |
| 后端 | Spring Boot 3.x + Java 17 + Maven |
| 关系数据库 | MySQL 8.0（用户/合同/分析结果/知识库文档） |
| RAG（两种模式） | **LightRAG（默认，服务化接入）** / legacy（LangChain4j + Chroma + Neo4j） |
| AI | DashScope（兼容 OpenAI 协议）Chat/Embedding API |

### 2.2 系统架构（概览）

```
Vue 3 前端  ──HTTP/JWT──>  Spring Boot 后端  ──>  MySQL（业务数据）
                                   │
                                   ├─ RAG: LightRAG（默认）──HTTP──> LightRAG Server
                                   │
                                   └─ RAG: legacy（可选）──────────> Chroma（向量） + Neo4j（图谱）
```

---

## 3. 数据与知识库

### 3.1 核心数据表（概念）

- `users`：用户
- `contracts`：合同（解析后的正文）
- `analysis_results`：结构化分析结果（含 JSON 字段、`retrieved_context`、`graph_context`）
- `knowledge_docs`：知识库文档源（用于 rebuild）

### 3.2 知识库重建与模式差异

接口：`POST /api/knowledge/rebuild`

- **lightrag 模式（默认）**
  - 后端把 `knowledge_docs` 导出为 `lightrag/inputs/` 下的文本文件
  - 由 LightRAG Server 自行完成索引与检索
- **legacy 模式（可选）**
  - 向量：把 `knowledge_docs` ingest 到 Chroma
  - 图谱：把 `knowledge_docs` 的结构化字段 upsert 到 Neo4j，并生成可用于提示词的 `graph_context`

状态接口：`GET /api/knowledge/status`（用于可观测：文档数、探测返回条数、错误信息等）

---

## 4. 合同分析链路

### 4.1 结构化分析

接口：`POST /api/analysis/contracts/{contractId}`

- 检索上下文（按当前 rag.mode 走 LightRAG 或 legacy）
- 调用 LLM 生成结构化 JSON
- 解析后写入 `analysis_results`

### 4.2 对话式 SSE（流式）

接口：`POST /api/analysis/contracts/{contractId}/stream`（SSE，POST + ReadableStream）

事件：
- `status`：阶段提示（如检索、生成结构化、分段返回）
- `answer`：支持两种形态
  - 初次分析：按段落切分的文本片段（`chunk/index/isLast`）
  - 追问：增量输出（`delta/seq/isLast`）
- `done`：本轮结束（携带结构化结果 payload）
- `error`：失败（携带可重试提示）

注意：初次分析的“分段返回”是把完整回答切块后发送；追问已支持 token/delta 级增量输出。

### 4.3 会话持久化（最近 20 条）

- 后端会话按 `contractId` 落库保存（最多 20 条），用于追问时构造 `conversation_history`
- 前端进入合同对话时可从后端拉取历史并回显：`GET /api/analysis/contracts/{contractId}/chat/history`

---

## 5. 安全与配置

### 5.1 鉴权

- 除 `/api/auth/**` 外均需 JWT
- 前端统一在 `src/api/http.js` 注入 Authorization token

### 5.2 推荐配置方式（避免泄露密钥）

开发环境采用 profile 文件集中管理明文密钥，避免写入 `application.yml`：

- `application.yml` 默认启用 `dev` profile（可用 `SPRING_PROFILES_ACTIVE` 覆盖）
- 本机创建（不入库）：`contractlens-backend/src/main/resources/application-dev.yml`
  - 复制模板：`application-dev.example.yml` → `application-dev.yml`
  - 在 dev 文件中填写：
    - `contractlens.dev.datasource.password`
    - `contractlens.dev.jwt.secret`
    - `contractlens.dev.dashscope.api-key`
    - （可选）`contractlens.dev.neo4j.password`

非敏感或与运行环境强绑定的配置仍可用环境变量覆盖（示例）：
- MySQL：`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`
- RAG 模式：`CONTRACTLENS_RAG_MODE=lightrag|legacy`
- LightRAG：`LIGHTRAG_BASE_URL`、`LIGHTRAG_QUERY_PATH`
- legacy：`CHROMA_URL`、`NEO4J_URI`、`NEO4J_USERNAME`

---

## 6. API 概览（除 /api/auth/** 外均需 JWT）

### Auth
- `POST /api/auth/register`
- `POST /api/auth/login`

### Contracts
- `POST /api/contracts/upload`
- `GET /api/contracts`
- `GET /api/contracts/{id}`
- `DELETE /api/contracts/{id}`

### Analysis
- `POST /api/analysis/contracts/{contractId}`
- `POST /api/analysis/contracts/{contractId}/stream`

### Knowledge
- `GET /api/knowledge/status`
- `GET /api/knowledge/docs?page=&size=`
- `POST /api/knowledge/rebuild`

---

*文档版本：v4.0.0（对齐当前实现）| 最后更新：2026-04-25*
