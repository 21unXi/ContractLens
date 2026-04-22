# ContractLens · 租房护航

> 智能租房合同风险审查 —— 用 AI + 法律知识库，让合同风险一目了然

---

## 1. 项目概述

### 1.1 项目简介

ContractLens · 租房护航是一款专注于**住房租赁合同**的 AI 智能风险审查工具。用户上传租房合同，系统基于 RAG（检索增强生成）技术，结合中国法律法规知识库，自动识别合同中的风险条款，并给出专业修改建议。

> **定位**：不做通用合同审查，专攻租房场景，做深做透。

### 1.2 核心价值

- **垂直场景深度**：专为租房合同设计，内置法律法规知识库
- **RAG 增强分析**：基于真实法律条文分析，而非纯模型"幻觉"
- **双视角分析**：同时从房东、租客视角分析风险
- **法律依据**：每条风险条款附带具体法条引用

### 1.3 目标用户

- 刚毕业的大学生（首次租房，缺乏经验）
- 城市租客（希望能看懂合同、保护自己权益）
- 房东（希望合同规范，避免纠纷）

### 1.4 租房合同常见风险点

| 风险类型 | 说明 | 法律依据 |
|---------|------|---------|
| 押金条款 | 押金数额、退还条件、扣押规则不明确 | 《民法典》第721条 |
| 租金条款 | 支付方式、涨租条件、逾期责任模糊 | 《民法典》第722条 |
| 提前解约 | 违约金过高、单方解约权不对等 | 《民法典》第563条 |
| 维修责任 | 房屋维修责任划分不清 | 《民法典》第713条 |
| 转租条款 | 未经同意转租、转让费 | 《民法典》第716-718条 |
| 房屋交付 | 家具清单、现状确认缺失 | 《民法典》第708条 |
| 费用约定 | 水电费、物业费、维修费分摊不清 | 各地租赁管理办法 |

---

## 2. 技术架构

### 2.1 技术栈

| 层级 | 技术选型 |
|------|----------|
| **前端** | Vue 3 + Vite + Axios + ECharts |
| **后端** | Spring Boot 3.x（Java 17+） |
| **关系数据库** | MySQL 8.0（用户数据、分析结果） |
| **向量数据库** | Milvus / Qdrant / Chroma（语义检索） |
| **图数据库** | **Neo4j**（知识图谱、GraphRAG） |
| **AI 能力** | LLM API + Embedding API |
| **RAG 框架** | LangChain4j（Java） |
| **GraphRAG 工具** | Microsoft GraphRAG（Python离线构建） |
| **向量数据库** | Milvus / Qdrant / Chroma（知识库） |
| **AI 能力** | LLM API + Embedding API |
| **RAG 框架** | LangChain4j（Java） |
| **构建工具** | Maven |

### 2.2 系统架构图（RAG + GraphRAG 双轨）

```
┌──────────────────────────────────────────────────────────────────┐
│                          前端 Vue 3                               │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │
│   │ 用户模块  │  │ 合同模块  │  │分析模块  │  │  知识库管理   │  │
│   └──────────┘  └──────────┘  └──────────┘  └───────────────┘  │
└──────────────────────────────────┬───────────────────────────────┘
                                   │  REST API
┌──────────────────────────────────▼───────────────────────────────┐
│                         后端 Spring Boot                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────────┐  │
│  │ 认证模块  │  │ 文件模块  │  │AI服务    │  │  RAG 融合服务   │  │
│  │ JWT登录  │  │上传解析  │  │LLM调用  │  │ 向量+图谱双检索 │  │
│  └──────────┘  └──────────┘  └──────────┘  └─────────────────┘  │
└──────────────────────────────────┬───────────────────────────────┘
       ┌──────────────┬────────────┼────────────┐
       ▼              ▼            ▼            ▼
 ┌──────────┐  ┌──────────┐  ┌────────┐  ┌──────────┐
 │  MySQL   │  │ 向量数据库 │  │ Neo4j  │  │ LLM API  │
 │ 用户/合同 │  │ Chroma/  │  │ 知识   │  │ GPT/     │
 │ 分析结果  │  │ Qdrant   │  │ 图谱   │  │ Claude   │
 └──────────┘  └──────────┘  └────────┘  └──────────┘
                    │               │
                    ▼               ▼
             ┌──────────┐   ┌──────────────────┐
             │ 文档向量库 │   │  法律知识图谱     │
             │ 法条/案例  │   │  实体+关系+社区  │
             │ 语义检索   │   │  图遍历检索      │
             └──────────┘   └──────────────────┘
                    │               │
                    └───────┬───────┘
                            ▼
                    ┌───────────────┐
                    │  检索结果融合  │
                    │  RRF 重排序   │
                    └───────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │  LLM 分析生成  │
                    └───────────────┘
```

---

## 3. GraphRAG 知识图谱设计

### 3.1 什么是 GraphRAG？

GraphRAG 是微软提出的一种增强检索方法，核心思想：

> **不仅检索相似文本，还检索实体之间的关系。**

| 对比 | 普通 RAG | GraphRAG |
|-----|---------|---------|
| 存储 | 文本块 → 向量 | 实体 + 关系 → 图谱 |
| 检索 | 语义相似度 | 图遍历 + 语义 |
| 擅长 | 找相似段落 | 找关联关系、发现冲突 |

### 3.2 法律知识图谱设计

#### 3.2.1 实体类型

| 实体类型 | 说明 | 示例 |
|---------|------|------|
| `LawArticle` | 法律条文 | 民法典第721条 |
| `RiskType` | 风险类型 | 押金风险、解约风险 |
| `ContractClause` | 合同条款 | 押金条款、租金条款 |
| `Case` | 判例案例 | 某租房纠纷案 |
| `Concept` | 法律概念 | 违约金、押金、租赁期限 |

#### 3.2.2 关系类型

| 关系类型 | 说明 | 示例 |
|---------|------|------|
| `REFERENCES` | 引用关系 | 押金条款 → 民法典721条 |
| `CONFLICTS_WITH` | 冲突关系 | 押金条款 ↔ 违约金条款 |
| `IMPLIES` | 蕴含关系 | 提前解约 → 违约责任 |
| `RELATES_TO` | 关联关系 | 案例 → 法条 |
| `MITIGATES` | 缓解关系 | 某条款 → 降低某风险 |

#### 3.2.3 知识图谱示例

```
┌─────────────────────────────────────────────────────────────────┐
│                        法律知识图谱                               │
│                                                                 │
│   ┌─────────────┐      REFERENCES      ┌─────────────────┐     │
│   │  押金条款    │ ──────────────────▶ │  民法典第721条   │     │
│   └─────────────┘                      └─────────────────┘     │
│         │                                      │               │
│         │ CONFLICTS_WITH                       │ RELATES_TO    │
│         ▼                                      ▼               │
│   ┌─────────────┐                      ┌─────────────────┐     │
│   │  违约金条款  │                      │  民法典第585条   │     │
│   └─────────────┘                      └─────────────────┘     │
│         │                                      │               │
│         │ IMPLIES                              │               │
│         ▼                                      │               │
│   ┌─────────────┐                              │               │
│   │ 提前解约风险 │ ◀───────────────────────────┘               │
│   └─────────────┘                                              │
│         │                                                      │
│         │ RELATES_TO                                           │
│         ▼                                                      │
│   ┌─────────────────────────────────────────────┐             │
│   │ 案例：房东扣留押金案 → 判决：房东败诉        │             │
│   └─────────────────────────────────────────────┘             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 GraphRAG 构建流程

```
① 原始文档（法律法规、案例、风险清单）
        │
        ▼
② Microsoft GraphRAG 索引构建（Python）
   - 实体抽取（NER）
   - 关系抽取
   - 社区发现
   - 图嵌入生成
        │
        ▼
③ 存入 Neo4j 图数据库
   - 节点：实体
   - 边：关系
   - 属性：向量嵌入、元数据
        │
        ▼
④ Spring Boot 通过 Neo4j Java Driver 查询
```

### 3.4 GraphRAG 检索策略

#### 3.4.1 局部检索（Local Search）

查询某条款的相关法条和案例：

```cypher
// 查询"押金条款"相关的所有法条和案例
MATCH (c:ContractClause {name: '押金条款'})-[r:REFERENCES|RELATES_TO]->(target)
RETURN c, r, target
```

#### 3.4.2 全局检索（Global Search）

通过社区发现，找到相关主题：

```cypher
// 查询某社区内的所有相关实体
MATCH (n)-[r]->(m)
WHERE n.community = '租赁风险'
RETURN n, r, m
```

#### 3.4.3 关系推理

发现条款之间的潜在冲突：

```cypher
// 查询可能冲突的条款组合
MATCH (a:ContractClause)-[:CONFLICTS_WITH]->(b:ContractClause)
WHERE a IN $userClauses AND b IN $userClauses
RETURN a, b, '发现潜在冲突' as warning
```

### 3.5 GraphRAG 工具选型

| 工具 | 用途 | 说明 |
|-----|------|------|
| **Microsoft GraphRAG** | 图谱构建 | Python，自动从文档构建知识图谱 |
| **Neo4j** | 图数据库存储 | 生产级图数据库，支持 Cypher 查询 |
| **Neo4j Java Driver** | Java 调用 | Spring Boot 集成 |
| **LangChain4j** | RAG 融合 | Java 端 RAG 框架 |

---

## 4. 向量检索设计（普通 RAG）

### 4.1 向量库选型

| 向量库 | 适用场景 | 特点 |
|--------|---------|------|
| **Milvus** | 生产环境、中大规模 | 功能全、性能强、需要额外部署 |
| **Qdrant** | 生产环境、中大规模 | Rust实现、性能好、提供云服务 |
| **Chroma** | 开发/小规模 | 轻量、Python优先、易用性强 |

> **建议**：开发阶段使用 **Chroma**（简单快速），上线前切换到 **Qdrant**（生产级）。

### 4.2 Collection 设计

```json
{
  "collection_name": "rental_contract_knowledge",
  "fields": {
    "id": "string",
    "content": "string",
    "metadata": {
      "type": "string (law/case/guide/risk)",
      "title": "string",
      "law_article": "string (如民法典721)",
      "risk_type": "string (押金/租金/解约等)",
      "relevance_tags": ["array of strings"]
    },
    "vector": "float[768 or 1536]"
  },
  "index_params": {
    "metric_type": "COSINE",
    "nlist": 1024
  }
}
```

### 3.5 知识库构建流程

```
① 文档收集（法律法规PDF、网页抓取、手工整理）
        │
        ▼
② 文档清洗 & 格式化
        │
        ▼
③ 文档分块（Chunking）
   - 按段落或按语义块
   - 块大小：约500字
   - 块重叠：50字（保持上下文连贯）
        │
        ▼
④ 生成向量（Embedding API）
   - 使用 text-embedding-3-small 或 BGE-large-zh
        │
        ▼
⑤ 写入向量数据库
        │
        ▼
⑥ 同步元数据到 MySQL（knowledge_docs 表）
```

### 3.6 Embedding 模型选型

| 模型 | 维度 | 中文支持 | 推荐场景 |
|------|------|---------|---------|
| **text-embedding-3-small** | 1536 | ✅ | OpenAI用户首选 |
| **BGE-large-zh-v1.5** | 1024 | ✅✅ | 中文场景、国产平替 |

> **推荐**：国内使用 **BGE-large-zh-v1.5**，可部署本地或调用硅基流动等平台API。

---

## 5. 双轨检索融合（核心流程）

### 5.1 整体流程

```
用户上传租房合同
        │
        ▼
┌─────────────────┐
│   文本解析       │
└─────────────────┘
        │
        ├──────────────────────────────┐
        ▼                              ▼
┌─────────────────┐            ┌─────────────────┐
│  向量检索        │            │  图谱检索        │
│  (普通 RAG)      │            │  (GraphRAG)     │
│                 │            │                 │
│  找相似法条      │            │  找关联实体      │
│  找相似案例      │            │  发现条款冲突    │
│                 │            │  推理关系链      │
└─────────────────┘            └─────────────────┘
        │                              │
        │    Top-K 文档块              │    相关实体+关系
        │                              │
        └──────────────┬───────────────┘
                       ▼
              ┌─────────────────┐
              │  检索结果融合    │
              │  RRF 重排序      │
              │  去重 + 加权     │
              └─────────────────┘
                       │
                       ▼
              ┌─────────────────┐
              │  构建 RAG Prompt │
              │  合同 + 检索结果  │
              └─────────────────┘
                       │
                       ▼
              ┌─────────────────┐
              │  调用 LLM API    │
              └─────────────────┘
                       │
                       ▼
              ┌─────────────────┐
              │  解析 JSON 响应  │
              └─────────────────┘
                       │
                       ▼
              ┌─────────────────┐
              │  存储结果 → 返回 │
              └─────────────────┘
```

### 5.2 检索融合策略：RRF（Reciprocal Rank Fusion）

```python
# RRF 融合算法
def rrf_fusion(vector_results, graph_results, k=60):
    """
    vector_results: 向量检索结果 [(doc_id, score), ...]
    graph_results: 图谱检索结果 [(entity_id, score), ...]
    k: RRF 参数，通常取 60
    """
    scores = {}
    
    # 向量检索结果加权
    for rank, (doc_id, _) in enumerate(vector_results):
        scores[doc_id] = scores.get(doc_id, 0) + 1 / (k + rank + 1)
    
    # 图谱检索结果加权
    for rank, (entity_id, _) in enumerate(graph_results):
        scores[entity_id] = scores.get(entity_id, 0) + 1 / (k + rank + 1)
    
    # 按融合分数排序
    return sorted(scores.items(), key=lambda x: x[1], reverse=True)
```

### 5.3 GraphRAG 的独特价值

| 场景 | 普通 RAG | GraphRAG |
|-----|---------|---------|
| 单条款风险 | ✅ 能找到相关法条 | ✅ 能找到相关法条 |
| 条款冲突检测 | ❌ 无法发现 | ✅ 通过关系推理发现 |
| 关联风险链 | ❌ 只能单点检索 | ✅ 图遍历找全链 |
| 案例推理 | ⚠️ 只能找相似案例 | ✅ 能找"同法条→同判决"的案例链 |

**示例：条款冲突检测**

```
用户合同包含：
  - 条款A：押金两个月，提前退租不退
  - 条款B：违约金为一个月租金

GraphRAG 发现：
  条款A ──CONFLICTS_WITH──▶ 条款B
  理由：押金和违约金性质重叠，可能构成双重惩罚

输出：
  "发现潜在冲突：押金条款与违约金条款可能构成双重惩罚，
   建议明确押金性质为履约保证金，与违约金分离。"
```

### 5.4 RAG Prompt 设计（含图谱上下文）

```
你是一位专业的租房合同法律顾问。请结合以下参考资料，对用户的租房合同进行风险分析。

【参考资料】（以下内容来自法律知识库）
---
{retrieved_context}
---

【分析要求】
1. 结合上述参考资料，识别租房合同中的风险条款
2. 分别从房东视角和租客视角分析风险
3. 每个风险条款必须引用相关法律依据
4. 为每个风险条款标注风险等级（高/中/低）
5. 提供具体的修改建议，附带法律依据

【输出格式】
请以JSON格式输出：
{
  "summary": "一句话概括合同整体风险情况",
  "risk_score": 0-100,
  "risk_level": "高/中/低",
  "party_lessor_risks": [
    {
      "clause_index": 1,
      "clause_text": "原条款...",
      "risk_type": "押金风险",
      "risk_level": "高",
      "risk_description": "风险描述...",
      "legal_basis": "《民法典》第XXX条...",
      "suggestion": "修改建议..."
    }
  ],
  "party_tenant_risks": [...],
  "suggestions": [...],
  "contract_tags": ["押金过高", "违约金不对等"]
}

【合同内容】
{contract_content}
```

---

## 6. 数据库设计（MySQL + Neo4j）

### 6.1 MySQL 表结构

#### 6.1.1 用户表（users）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 用户名 |
| password | VARCHAR(255) | NOT NULL | 密码（BCrypt加密） |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 邮箱 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 注册时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

#### 6.1.2 合同表（contracts）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | FK → users.id | 关联用户 |
| title | VARCHAR(255) | NOT NULL | 合同标题 |
| contract_type | VARCHAR(20) | DEFAULT 'rental' | 合同类型（默认租房） |
| content | TEXT | NOT NULL | 合同原文 |
| file_type | VARCHAR(20) | NOT NULL | 文件类型（txt/docx/pdf） |
| file_path | VARCHAR(500) | | 原始文件存储路径 |
| file_size | BIGINT | | 文件大小（字节） |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 上传时间 |

#### 6.1.3 分析结果表（analysis_results）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| contract_id | BIGINT | FK → contracts.id | 关联合同 |
| risk_level | VARCHAR(10) | NOT NULL | 风险等级（高/中/低） |
| risk_score | INT | | 风险评分（0-100） |
| summary | TEXT | | 合同一句话摘要 |
| party_lessor_risks | JSON | | 房东视角风险（JSON数组） |
| party_tenant_risks | JSON | | 租客视角风险（JSON数组） |
| suggestions | JSON | | 修改建议（JSON数组） |
| contract_tags | JSON | | 合同标签（JSON数组） |
| retrieved_context | TEXT | | 向量检索结果 |
| graph_context | TEXT | | 🆕 图谱检索结果（实体+关系） |
| clause_conflicts | JSON | | 🆕 发现的条款冲突 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 分析时间 |

#### 6.1.4 知识库文档表（knowledge_docs）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| doc_id | VARCHAR(50) | NOT NULL, UNIQUE | 文档唯一ID |
| title | VARCHAR(255) | NOT NULL | 文档标题 |
| doc_type | VARCHAR(20) | NOT NULL | 文档类型（law/case/guide/risk） |
| content | TEXT | NOT NULL | 文档正文 |
| law_article | VARCHAR(100) | | 关联法条 |
| risk_type | VARCHAR(50) | | 风险类型 |
| chunk_count | INT | DEFAULT 1 | 被切分的块数 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 录入时间 |

### 6.2 Neo4j 知识图谱设计

#### 6.2.1 节点类型

```cypher
// 法律条文节点
CREATE (:LawArticle {
  id: 'law_721',
  name: '民法典第721条',
  content: '承租人应当按照约定的期限支付租金',
  category: '租赁合同'
})

// 风险类型节点
CREATE (:RiskType {
  id: 'risk_deposit',
  name: '押金风险',
  description: '押金数额过高或退还条件模糊'
})

// 合同条款节点
CREATE (:ContractClause {
  id: 'clause_deposit',
  name: '押金条款',
  template: '押金为人民币___元...'
})

// 案例节点
CREATE (:Case {
  id: 'case_001',
  name: '房东扣留押金案',
  court: '某区人民法院',
  judgment: '房东败诉，退还押金'
})
```

#### 6.2.2 关系类型

```cypher
// 引用关系：条款引用法条
(:ContractClause)-[:REFERENCES]->(:LawArticle)

// 冲突关系：条款之间冲突
(:ContractClause)-[:CONFLICTS_WITH {reason: '...'}]->(:ContractClause)

// 蕴含关系：某条款意味着某风险
(:ContractClause)-[:IMPLIES]->(:RiskType)

// 关联关系：案例引用法条
(:Case)-[:CITES]->(:LawArticle)

// 缓解关系：某条款可缓解某风险
(:ContractClause)-[:MITIGATES]->(:RiskType)
```

#### 6.2.3 常用查询

```cypher
// 1. 查询某条款的所有相关法条
MATCH (c:ContractClause {name: '押金条款'})-[:REFERENCES]->(l:LawArticle)
RETURN c.name, l.name, l.content

// 2. 发现条款冲突
MATCH (a:ContractClause)-[r:CONFLICTS_WITH]->(b:ContractClause)
RETURN a.name, b.name, r.reason

// 3. 查询某风险的相关案例
MATCH (r:RiskType {name: '押金风险'})<-[:IMPLIES]-(c:ContractClause)
MATCH (c)-[:REFERENCES]->(l:LawArticle)
MATCH (case:Case)-[:CITES]->(l)
RETURN case.name, case.judgment

// 4. 查询某法条的完整关系网络
MATCH (l:LawArticle {id: 'law_721'})-[r]-(target)
RETURN l, r, target
```

---

## 7. JSON 结构说明

#### party_lessor_risks / party_tenant_risks

```json
[
  {
    "clause_index": 3,
    "clause_text": "押金为两个月租金，提前退租不予退还",
    "risk_type": "押金风险",
    "risk_level": "高",
    "risk_description": "押金性质为违约金且不退还不公平",
    "legal_basis": "《民法典》第563、585条",
    "suggestion": "建议将押金与违约金分离，明确退还条件"
  }
]
```

#### suggestions

```json
[
  {
    "clause_index": 3,
    "original_text": "押金为两个月租金，提前退租不予退还",
    "suggested_text": "押金为人民币XXX元，租赁期满结算后无息退还",
    "reason": "押金性质应与违约金分离，保障双方权益"
  }
]
```

#### contract_tags

```json
["押金过高", "违约金不对等", "维修责任不清", "转租条款缺失"]
```

---

## 8. API 接口设计

### 6.1 认证模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/login | 用户登录（返回JWT） |

### 6.2 合同模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/contracts/upload | 上传合同文件 |
| GET | /api/contracts | 获取用户合同列表 |
| GET | /api/contracts/{id} | 获取合同详情 |
| DELETE | /api/contracts/{id} | 删除合同 |

### 6.3 分析模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/contracts/{id}/analyze | 发起合同分析（RAG） |
| GET | /api/contracts/{id}/result | 获取分析结果 |
| GET | /api/analysis/history | 获取分析历史 |

### 6.4 知识库管理模块

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/knowledge/docs | 获取知识库文档列表 |
| POST | /api/knowledge/rebuild | 重建知识库索引（管理员） |
| GET | /api/knowledge/search | 测试知识库检索 |

### 6.5 用户模块

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/user/profile | 获取个人资料 |
| PUT | /api/user/profile | 更新个人资料 |

---

## 9. 前端功能模块

### 9.1 页面结构

```
前端 Vue 3
├── 登录/注册页
├── 主控制台
│   ├── 合同上传区（拖拽上传）
│   ├── 合同列表
│   └── 分析结果展示
│       ├── 风险概览（雷达图/评分）
│       ├── 双视角切换（房东/租客）
│       ├── 风险条款卡片列表（含法律依据）
│       └── 修改建议面板
├── 历史记录页
│   └── 历史分析列表
└── 个人中心
```

### 9.2 风险可视化

| 可视化形式 | 说明 |
|-----------|------|
| **风险雷达图** | 从押金、租金、解约、维修、转租等维度展示合同健康度 |
| **风险热力图** | 用颜色深浅表示条款风险等级 |
| **条款卡片** | 每条风险一张卡片，显示原文 + 法律依据 + 建议 |
| **标签云** | 合同标签可视化 |
| **法律依据高亮** | 风险条款中的法律关键词高亮显示 |
| **🆕 关系网络图** | GraphRAG 检索到的实体关系可视化 |

### 9.3 组件清单

| 组件 | 功能 |
|------|------|
| `ContractUploader` | 合同上传（支持拖拽，支持 txt/docx/pdf） |
| `RiskRadarChart` | 风险雷达图（ECharts） |
| `RiskHeatmap` | 风险热力图 |
| `ClauseCard` | 单条风险条款卡片（含法律依据引用） |
| `PartyToggle` | 房东/租客视角切换 |
| `SuggestionPanel` | 修改建议面板 |
| `LegalBasisTag` | 法律依据标签（点击可查看法条原文） |
| `ContractList` | 合同列表 |
| `HistoryTimeline` | 分析历史时间线 |
| `🆕 RelationGraph` | 知识图谱关系网络可视化（ECharts Graph） |
| `🆕 ConflictAlert` | 条款冲突警告组件 |

---

## 10. 项目结构

### 10.1 后端 Spring Boot 结构

```
contractlens-backend/
├── pom.xml
├── src/main/java/com/contractlens/
│   ├── ContractLensApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java         # Spring Security配置
│   │   ├── JwtConfig.java              # JWT配置
│   │   ├── CorsConfig.java             # 跨域配置
│   │   ├── AiConfig.java               # LLM/Embedding 配置
│   │   └── Neo4jConfig.java            # 🆕 Neo4j 配置
│   ├── controller/
│   │   ├── AuthController.java          # 认证接口
│   │   ├── ContractController.java      # 合同接口
│   │   ├── AnalysisController.java      # 分析接口
│   │   ├── KnowledgeController.java     # 知识库管理接口
│   │   └── UserController.java          # 用户接口
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── ContractService.java
│   │   ├── AnalysisService.java         # 分析核心逻辑
│   │   ├── RagFusionService.java        # 🆕 双轨检索融合
│   │   ├── VectorRetrievalService.java  # 向量检索
│   │   ├── GraphRetrievalService.java   # 🆕 图谱检索
│   │   ├── EmbeddingService.java        # Embedding 调用
│   │   └── LlmService.java              # LLM 调用封装
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── ContractRepository.java
│   │   ├── AnalysisResultRepository.java
│   │   └── KnowledgeDocRepository.java
│   ├── entity/
│   │   ├── User.java
│   │   ├── Contract.java
│   │   ├── AnalysisResult.java
│   │   └── KnowledgeDoc.java
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── ContractUploadResponse.java
│   │   ├── AnalysisResultDTO.java
│   │   └── RagRetrievalDTO.java
│   ├── vectorstore/
│   │   ├── VectorStore.java             # 向量库接口抽象
│   │   ├── ChromaVectorStore.java        # Chroma 实现
│   │   └── QdrantVectorStore.java       # Qdrant 实现
│   ├── util/
│   │   ├── JwtUtil.java
│   │   ├── FileUtil.java
│   │   └── JsonUtil.java
│   └── exception/
│       ├── GlobalExceptionHandler.java
│       └── BusinessException.java
└── src/main/resources/
    ├── application.yml
    └── prompt-templates/
        └── rental-analysis-prompt.txt
```

### 8.2 前端 Vue 3 结构

```
contractlens-frontend/
├── package.json
├── vite.config.js
├── src/
│   ├── main.js
│   ├── App.vue
│   ├── api/
│   │   ├── index.js                     # axios封装
│   │   ├── auth.js
│   │   ├── contract.js
│   │   ├── analysis.js
│   │   └── knowledge.js
│   ├── components/
│   │   ├── ContractUploader.vue
│   │   ├── RiskRadarChart.vue
│   │   ├── RiskHeatmap.vue
│   │   ├── ClauseCard.vue
│   │   ├── PartyToggle.vue
│   │   ├── SuggestionPanel.vue
│   │   ├── LegalBasisTag.vue
│   │   ├── ContractList.vue
│   │   ├── HistoryTimeline.vue
│   │   ├── RelationGraph.vue            # 🆕 知识图谱可视化
│   │   └── ConflictAlert.vue            # 🆕 条款冲突警告
│   ├── views/
│   │   ├── Login.vue
│   │   ├── Register.vue
│   │   ├── Dashboard.vue                # 主控制台
│   │   ├── ContractDetail.vue           # 合同详情/分析结果
│   │   ├── History.vue                  # 历史记录
│   │   └── Profile.vue                  # 个人中心
│   ├── router/
│   │   └── index.js
│   ├── stores/
│   │   ├── auth.js
│   │   └── contract.js
│   └── utils/
│       └── fileParser.js                # 文件解析
└── public/
```

---

## 11. 开发规范

### 11.1 代码规范

- **后端**：Google Java Style Guide
- **前端**：ESLint + Prettier，Vue 3 Composition API
- **命名**：RESTful API，驼峰类名

### 11.2 Git 提交规范

```
feat: 新功能
fix: 修复bug
docs: 文档更新
refactor: 重构
chore: 构建/工具相关
```

### 11.3 分支策略

```
main          ← 生产
├── develop   ← 开发主分支
│   ├── feature/rag
│   ├── feature/frontend
│   └── bugfix/xxx
└── release/xxx
```

---

## 12. 功能优先级

### P0（必须完成）

1. 用户注册/登录（JWT）
2. 合同上传（txt/docx/pdf）
3. **向量检索（普通 RAG）**
4. **图谱检索（GraphRAG）**
5. **双轨检索融合（RRF）**
6. LLM 分析生成
7. 分析结果展示（风险列表 + 法律依据）
8. 分析历史记录

### P1（增强体验）

1. 风险雷达图可视化
2. 房东/租客双视角切换
3. **条款冲突检测（图谱推理）**
4. 合同标签体系
5. 一句话摘要
6. 知识库文档管理

### P2（差异化功能）

1. 风险热力图
2. **关系网络可视化（图谱展示）**
3. 修改建议一键复制
4. 分析结果导出
5. 知识库检索测试工具

---

## 13. 环境配置

### 13.1 后端 application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/contractlens?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

jwt:
  secret: your-jwt-secret-key
  expiration: 86400000  # 24小时

# AI 配置
ai:
  llm:
    provider: openai  # openai / zhipu / doubao
    api-key: your-api-key
    base-url: https://api.openai.com/v1
    model: gpt-4o-mini
  embedding:
    provider: openai  # openai / zhipu / bge
    api-key: your-api-key
    base-url: https://api.openai.com/v1
    model: text-embedding-3-small

# 向量数据库配置
vectorstore:
  type: chroma  # chroma / qdrant / milvus
  chroma:
    host: http://localhost:8000
  qdrant:
    host: http://localhost:6333
    collection: rental_contract_knowledge

# 🆕 Neo4j 图数据库配置
neo4j:
  uri: bolt://localhost:7687
  username: neo4j
  password: your_neo4j_password

# 文件上传
upload:
  path: ./uploads
  allowed-types: txt,docx,pdf
```

### 13.2 前端 .env

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_TITLE=ContractLens · 租房护航
```

---

## 14. 部署说明

### 14.1 开发环境

```bash
# 启动向量数据库（Chroma）
docker run -d -p 8000:8000 ghcr.io/chroma-core/chroma:latest

# 🆕 启动 Neo4j 图数据库
docker run -d -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/your_password \
  neo4j:latest

# 启动后端
cd contractlens-backend
mvn spring-boot:run

# 启动前端
cd contractlens-frontend
npm install
npm run dev
```

### 14.2 知识图谱构建（GraphRAG）

```bash
# 使用 Microsoft GraphRAG 构建知识图谱
cd scripts/graphrag

# 安装依赖
pip install graphrag

# 初始化索引
graphrag init --root ./rag_index

# 构建索引（从法律文档构建图谱）
graphrag index --root ./rag_index

# 导出图谱到 Neo4j
python export_to_neo4j.py --index ./rag_index --uri bolt://localhost:7687
```

### 14.3 数据库初始化 SQL

```sql
CREATE DATABASE contractlens DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE contractlens;

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE contracts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    contract_type VARCHAR(20) DEFAULT 'rental',
    content TEXT NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_path VARCHAR(500),
    file_size BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analysis_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    risk_level VARCHAR(10) NOT NULL,
    risk_score INT,
    summary TEXT,
    party_lessor_risks JSON,
    party_tenant_risks JSON,
    suggestions JSON,
    contract_tags JSON,
    retrieved_context TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_docs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_id VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    doc_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    law_article VARCHAR(100),
    risk_type VARCHAR(50),
    chunk_count INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 15. 知识库初始化数据

> 知识库需要预先录入法律法规和风险清单。以下为初始数据示例，正式项目需补充完整。

### 13.1 法律法规类

| doc_id | 标题 | law_article |
|--------|------|------------|
| law_721 | 民法典第七百二十一条-租金支付 | 民法典721 |
| law_722 | 民法典第七百二十二条-租金标准 | 民法典722 |
| law_708 | 民法典第七百零八条-租赁合同内容 | 民法典708 |
| law_713 | 民法典第七百一十三条-维修义务 | 民法典713 |
| law_716 | 民法典第七百一十六条-转租 | 民法典716 |
| law_563 | 民法典第五百六十三条-合同解除 | 民法典563 |
| law_585 | 民法典第五百八十五条-违约金 | 民法典585 |

### 13.2 风险清单类

| doc_id | 风险类型 |
|--------|---------|
| risk_deposit | 押金条款风险 |
| risk_rent | 租金条款风险 |
| risk_termination | 提前解约风险 |
| risk_maintenance | 维修责任风险 |
| risk_sublease | 转租条款风险 |
| risk_delivery | 房屋交付风险 |
| risk_fees | 费用约定风险 |

---

## 16. 注意事项

1. **AI API Key**：支持 OpenAI GPT、Claude、国产平替（智谱GLM、豆包等）
2. **向量数据库**：开发阶段用 Chroma（简单），生产用 Qdrant
3. **图数据库**：Neo4j 是 GraphRAG 的核心，必须部署
4. **Embedding 模型**：国内推荐 BGE-large-zh-v1.5
5. **隐私合规**：合同内容涉及隐私，系统不应用于模型训练
6. **成本控制**：AI API 按 token 计费，建议加入分析频率限制
7. **知识库维护**：法律法规会更新，知识库和图谱需要定期同步

---

*文档版本：v3.0.0（含 GraphRAG + 双轨检索融合）| 最后更新：2026-04-01*
