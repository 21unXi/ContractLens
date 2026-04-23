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
# 推荐使用环境变量（不要把真实密钥写入仓库）
# MySQL
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/contractlens?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your_password

# JWT
JWT_SECRET=dev-only-change-me-change-me-change-me-change-me

# 阿里云百炼（DashScope 兼容 OpenAI 协议）
DASHSCOPE_API_KEY=your_real_key

# 默认 RAG 模式：LightRAG
CONTRACTLENS_RAG_MODE=lightrag
LIGHTRAG_BASE_URL=http://localhost:9621
LIGHTRAG_QUERY_PATH=/query

# 可选：LightRAG 失败时回退 legacy（默认 true）
CONTRACTLENS_RAG_FALLBACK_TO_LEGACY=true

# legacy（可选）如果要启用：
# CONTRACTLENS_RAG_MODE=legacy
# CHROMA_URL=http://localhost:8000
# NEO4J_URI=bolt://localhost:7687
# NEO4J_USERNAME=neo4j
# NEO4J_PASSWORD=your_password
```

### 3. 启动

```bash
# 1) 启动依赖
# - MySQL 8.0
# - LightRAG Server（默认）
# - （可选）Chroma + Neo4j（仅 legacy 需要）

# 2) 启动后端
cd contractlens-backend
mvn spring-boot:run

# 3) 启动前端
cd contractlens-frontend
npm install
npm run dev
```

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
