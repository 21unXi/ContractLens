# ContractLens 项目规则

## 技术栈
- 后端：Spring Boot 3.x + Java 17 + Maven
- 前端：Vue 3 + Vite + npm
- 数据库：MySQL 8.0 + Neo4j + Chroma
- AI：LLM API + Embedding API

## 必须遵守
- 包名：`com.contractlens.*`
- 前端组件：Vue 3 Composition API (`<script setup>`)
- 密码：BCrypt 加密
- API：JWT 认证（除登录/注册）
- 日志：SLF4J，禁用 System.out

## 禁止行为
- ❌ 硬编码 API Key、密码等敏感信息
- ❌ 使用 React、Django、MongoDB
- ❌ 前端直接调用外部 API
- ❌ SQL 拼接（必须参数化查询）
- ❌ 提交 node_modules、target、.idea

## 功能边界
- 只做租房合同，不做通用合同
- 禁止：在线签约、支付、社交功能、移动端 App

## 进度追踪
- 每次修改代码后必须更新 `PROGRESS.md`
- 详细设计见 `PROJECT.md`
