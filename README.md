# ContractLens · 租房护航

> 面向租房合同的智能风险审查系统：结合 LLM + 法律知识库（RAG），把“风险点、法律依据、修改建议”结构化呈现为可读报告。

## 这是什么

ContractLens 聚焦“租房合同”场景（不做通用合同）。系统提供两类体验：

- 一次性结构化审查：输出风险等级/风险评分/双视角风险条款/优先建议
- 对话式追问：在同一份合同上进行多轮追问，SSE 流式返回回答

前端不直接调用外部 AI 或检索服务，所有能力统一由后端编排与鉴权。

## 核心能力

- 租房合同结构化风险分析（房东/租客双视角）
- 风险总览卡片与条款列表（支持风险排序/筛选/关键词高亮）
- SSE 对话：`status/answer/done/error/ping` 事件
- 会话持久化：每份合同保留最近 20 条对话历史
- RAG 双模式：
  - 默认：LightRAG Server（服务化检索）
  - 可选：legacy（Chroma + Neo4j）
- 安全边界：JWT 鉴权；对“索要系统提示/密钥/配置”等越狱/注入请求做服务端硬拒绝

## 技术栈

| 层级  | 技术                                                        |
| --- | --------------------------------------------------------- |
| 前端  | Vue 3 + Vite + Pinia + Axios                              |
| 后端  | Spring Boot 3.x + Java 17                                 |
| 数据库 | MySQL 8.0（必需）+ Neo4j（legacy 可选）+ Chroma（legacy 可选）        |
| RAG | LightRAG Server（默认）/ legacy（LangChain4j + Chroma + Neo4j） |
| AI  | DashScope（兼容 OpenAI 协议）Chat/Embedding API                 |

## 架构概览

- contractlens-frontend：上传合同、对话分析、展示报告
- contractlens-backend：JWT 鉴权、合同存储、RAG 检索、LLM 调用、SSE 推送
- MySQL：users/contracts/analysis\_results/analysis\_chat\_messages/knowledge\_docs
- LightRAG（默认）：后端通过 `POST /query` 获取上下文（context）
- legacy（可选）：向量检索（Chroma）+ 图谱上下文（Neo4j）

## 快速开始

### 1) 环境要求

- Java 17+
- Node.js 18+
- MySQL 8.0
- LightRAG Server（默认 RAG 模式）
- 可选：Chroma + Neo4j（仅 legacy 模式需要）

### 2) 初始化数据库

执行 [database/init.sql](file:///e:/code/repository/ContractLens/database/init.sql) 创建库表：

- `contractlens` 库
- users / contracts / analysis\_results / analysis\_chat\_messages / knowledge\_docs

### 3) 配置（本地 dev）

后端默认使用 dev profile，并从 `contractlens-backend/src/main/resources/application-dev.yml` 读取本地明文密钥（该文件不会入库）。

1. 复制模板

- `contractlens-backend/src/main/resources/application-dev.example.yml`
- 重命名为 `contractlens-backend/src/main/resources/application-dev.yml`

1. 环境变量示例

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/contractlens?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=root

CONTRACTLENS_RAG_MODE=lightrag
LIGHTRAG_BASE_URL=http://localhost:9621
LIGHTRAG_QUERY_PATH=/query
CONTRACTLENS_RAG_FALLBACK_TO_LEGACY=true
```

### 4) 启动

```bash
cd contractlens-backend
mvn spring-boot:run
```

```bash
cd contractlens-frontend
npm install
npm run dev
```

### 4.1) 启动 LightRAG（默认 RAG 模式）

后端默认通过 `LIGHTRAG_BASE_URL + LIGHTRAG_QUERY_PATH` 调用 LightRAG 的查询接口（默认 `http://localhost:9621/query`）。启动 ContractLens 之前，建议先确认 LightRAG 的 `POST /query` 可用。

#### 4.1.1 安装

以下为一种常见安装方式（按你的 Python 环境调整）：

```bash
pip install "lightrag-hku[api]"
```

#### 4.1.2 配置

LightRAG 的模型与 `.env` 配置请以其官方文档为准。对 ContractLens 来说，只需要满足两点：

- LightRAG 服务可访问（host/port 正确）
- `POST /query` 能返回 JSON（至少包含可用的上下文/回答字段）

ContractLens 侧的关键配置如下（示例）：

```bash
CONTRACTLENS_RAG_MODE=lightrag
LIGHTRAG_BASE_URL=http://localhost:9621
LIGHTRAG_QUERY_PATH=/query
```

#### 4.1.3 启动

启动 LightRAG Server（示例）：

```bash
cd lightrag
lightrag-server --host 0.0.0.0 --port 9621
```

也可以用 Uvicorn 启动（示例）：

```bash
uvicorn lightrag.api.lightrag_server:app --reload --host 0.0.0.0 --port 9621
```

启动后可通过 `GET /api/knowledge/status` 查看后端的 LightRAG 探测延迟与上下文长度（用于判断链路是否通了）。

- 前端：<http://localhost:5173>
- 后端：<http://localhost:8080>

### 5) 知识库同步（可选但推荐）

MySQL `knowledge_docs` 有数据后，可调用 `POST /api/knowledge/rebuild` 将知识库同步到当前 RAG 模式（legacy ingest / LightRAG inputs 导出）。

## API 概览

- Auth：`POST /api/auth/register`，`POST /api/auth/login`
- Contracts：`POST /api/contracts/upload`，`GET /api/contracts`，`GET /api/contracts/{id}`，`DELETE /api/contracts/{id}`
- Analysis：`POST /api/analysis/contracts/{id}/stream`（SSE），`GET /api/analysis/contracts/{id}/result`，`GET /api/analysis/contracts/{id}/chat/history`
- Knowledge：`GET /api/knowledge/status`，`GET /api/knowledge/docs`，`POST /api/knowledge/rebuild`

## 测试与报告

pytest 测试工程位于 `contractlens-pytests/`，包含工具/安全/检索/集成四层用例，并输出：

- `contractlens-pytests/reports/junit.xml`
- `contractlens-pytests/reports/TEST_REPORT.md`

运行方式：

```bash
cd contractlens-pytests
python -m pip install -r requirements-test.txt
python run_report.py
```

## 项目结构

```
ContractLens/
├── PROJECT.md
├── PROGRESS.md
├── database/
│   └── init.sql
├── contractlens-backend/
├── contractlens-frontend/
├── contractlens-pytests/
```

##
