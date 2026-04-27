# ContractLens · 租房护航

> 智能租房合同风险审查 —— 用 AI + 法律知识库，让合同风险一目了然

---

## 快速开始

### 1. 环境要求

- Java 17+
- Node.js 18+
- MySQL 8.0
- LightRAG Server（默认 RAG 模式）
- 可选：Chroma + Neo4j（仅 legacy 模式需要）

### 2. 配置

```bash
# 1) 后端使用 dev profile（默认启用）
# - 复制模板：contractlens-backend/src/main/resources/application-dev.example.yml
# - 粘贴为：contractlens-backend/src/main/resources/application-dev.yml
# - 在 application-dev.yml 里填入 MySQL/JWT/DashScope/Neo4j（如需）等明文
#
# 注意：application-dev.yml 已被 .gitignore 忽略，不会入库；请勿提交真实密钥。
#
# 2) 数据库连接（url/username 仍走环境变量；password 在 application-dev.yml 里配）
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/contractlens?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=root

# 3) RAG 模式（默认 LightRAG）
CONTRACTLENS_RAG_MODE=lightrag
LIGHTRAG_BASE_URL=http://localhost:9621
LIGHTRAG_QUERY_PATH=/query
CONTRACTLENS_RAG_FALLBACK_TO_LEGACY=true

# legacy（可选）如果要启用：
# CONTRACTLENS_RAG_MODE=legacy
# CHROMA_URL=http://localhost:8000
# NEO4J_URI=bolt://localhost:7687
# NEO4J_USERNAME=neo4j
```

### 3. 启动

```bash
# 1) 启动依赖
# - MySQL 8.0
# - LightRAG Server（默认，见下方“启动 LightRAG”）
# - （可选）Chroma + Neo4j（仅 legacy 需要）

# 2) 启动后端
cd contractlens-backend
mvn spring-boot:run

# 3) 启动前端
cd contractlens-frontend
npm install
npm run dev
```

### 3.1 启动 LightRAG（默认 RAG 模式）

本项目后端默认调用：`http://localhost:9621/query`（可通过 `LIGHTRAG_BASE_URL/LIGHTRAG_QUERY_PATH` 修改）。

你可以用 LightRAG 官方的 API 方式启动（示例命令，按你的 Python 环境调整）：  
- 安装（带 API 依赖）：`pip install "lightrag-hku[api]"`  
- 启动服务：`lightrag-server --host 0.0.0.0 --port 9621`  
  - 或用 Uvicorn：`uvicorn lightrag.api.lightrag_server:app --reload --host 0.0.0.0 --port 9621`  

LightRAG 侧的具体 `.env`/模型配置请以其官方文档为准，确保 `POST /query` 可用后再启动 ContractLens。

### 4. 访问

- 前端：http://localhost:5173
- 后端 API：http://localhost:8080

---

## 项目结构

```
ContractLens/
├── PROJECT.md              # 详细设计文档
├── PROGRESS.md             # 开发进度（以用户可见能力为准）
├── contractlens-backend/   # Spring Boot 后端
├── contractlens-frontend/  # Vue 3 前端
├── lightrag/inputs/        # LightRAG inputs（示例/导出目录）
└── .trae/rules/spec.md     # 开发规范
```

---

## 核心功能

- ✅ 租房合同风险分析
- ✅ 对话式 SSE：status/answer/done/error 分段返回
- ✅ 追问真流式：追问阶段支持 delta 增量输出
- ✅ 会话持久化：刷新后仍可继续上次对话（最近 20 条）
- ✅ RAG 双模式：LightRAG（默认）/ legacy（Chroma + Neo4j）
- ✅ 房东/租客双视角分析
- ✅ 法律依据引用
- ✅ 修改建议生成

---

## 技术栈

| 层级 | 技术 |
|-----|------|
| 前端 | Vue 3 + Vite + Pinia + Axios |
| 后端 | Spring Boot 3.x + Java 17 |
| 数据库 | MySQL 8.0（必需）+ Neo4j（legacy 可选）+ Chroma（legacy 可选） |
| RAG | LightRAG Server（默认）/ legacy（LangChain4j + Chroma + Neo4j） |
| AI | DashScope（兼容 OpenAI 协议）Chat/Embedding API |

---

## 文档

- [详细设计文档](./PROJECT.md)
- [开发规范](./.trae/rules/spec.md)

---

*版本：v1.0.0*
