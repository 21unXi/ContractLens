# ContractLens · 租房护航

> 智能租房合同风险审查 —— 用 AI + 法律知识库，让合同风险一目了然

---

## 快速开始

### 1. 环境要求

- Java 17+
- Node.js 18+
- MySQL 8.0
- Neo4j 5.x
- Chroma / Qdrant（向量数据库）

### 2. 配置

```bash
# 后端配置
cp contractlens-backend/src/main/resources/application-example.yml \
   contractlens-backend/src/main/resources/application-local.yml

# 编辑 application-local.yml，填入：
# - 数据库连接信息
# - LLM API Key
# - Neo4j 连接信息
```

### 3. 启动

```bash
# 启动数据库
docker-compose up -d

# 启动后端
cd contractlens-backend
mvn spring-boot:run

# 启动前端
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
├── SPEC.md                 # 开发规范
├── contractlens-backend/   # Spring Boot 后端
├── contractlens-frontend/  # Vue 3 前端
├── knowledge/              # 知识库原始文档
└── scripts/                # 构建脚本
```

---

## 核心功能

- ✅ 租房合同风险分析
- ✅ RAG + GraphRAG 双轨检索
- ✅ 房东/租客双视角分析
- ✅ 法律依据引用
- ✅ 修改建议生成

---

## 技术栈

| 层级 | 技术 |
|-----|------|
| 前端 | Vue 3 + Vite + ECharts |
| 后端 | Spring Boot 3.x + Java 17 |
| 数据库 | MySQL + Neo4j + Chroma |
| AI | LLM API + Embedding API |

---

## 文档

- [详细设计文档](./PROJECT.md)
- [开发规范](./SPEC.md)

---

*版本：v1.0.0*
