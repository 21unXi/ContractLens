# Plan：配置改造（application-dev.yml 集中密钥，application.yml 引用）

## Summary
- 目标：按你给的示例方式改造配置
  - `application-dev.yml`：存放开发环境的明文密钥/密码（本机使用，不入库）
  - `application.yml`：不写明文密钥，通过占位符引用 `application-dev.yml` 中的属性
- 同时保留现有“启动配置缺失失败早”的校验，避免静默用错配置。

## Current State Analysis（基于仓库现状）
- 后端目前只有一个配置文件：[application.yml](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/resources/application.yml)
  - 已出现 `spring.config.import: optional:classpath:application-local.yml`（你不想要）
  - 明文 key 已清理，但你希望改为“dev 文件集中存明文，application.yml 只引用”的结构
- `.gitignore` 当前已忽略 `application-*.yml`（因此 `application-dev.yml` 默认不会入库）：[.gitignore](file:///e:/code/repository/ContractLens/.gitignore#L1-L5)
- 启动校验已存在：[`StartupConfigValidator`](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/config/StartupConfigValidator.java)（你希望保留）

## Decisions（已确认）
- 使用 `application-dev.yml` 承载明文密钥/密码
- dev profile 激活方式写在 `application.yml` 中（但仍允许用 `SPRING_PROFILES_ACTIVE` 覆盖）
- 不再使用 `application-local.yml`

## Proposed Changes（实现细化）

### 1) 改造 application.yml：引用 dev 属性 + 默认启用 dev profile
- 文件：[application.yml](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/resources/application.yml)
- 修改点：
  1. 移除 `spring.config.import: optional:classpath:application-local.yml`
  2. 增加默认 profile：
     - `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`
     - 解释：仍“写在 application.yml 里”，但允许你在别的环境用环境变量覆盖
  3. 将敏感项改为引用 `contractlens.dev.*`（模仿你的 `sky.*` 示例）：
     - `jwt.secret: ${contractlens.dev.jwt.secret:}`
     - `langchain4j.open-ai.chat-model.api-key: ${contractlens.dev.dashscope.api-key:}`
     - `langchain4j.open-ai.embedding-model.api-key: ${contractlens.dev.dashscope.api-key:}`
     - `spring.datasource.password: ${contractlens.dev.datasource.password:}`
     - `neo4j.password: ${contractlens.dev.neo4j.password:}`（如你希望 dev 文件也管 Neo4j 密码）
  4. 日志项保持为“默认关闭，可通过 env 打开”：
     - `log-requests/log-responses` 保持 `${DASHSCOPE_LOG_REQUESTS:false}` / `${DASHSCOPE_LOG_RESPONSES:false}`

### 2) 新增 application-dev.example.yml（入库模板）+ 本机 application-dev.yml（不入库）
- 目的：你不希望 `application-dev.yml` 入库，但开发者需要一个可复制的模板
- 新增文件（入库，不含真实密钥）：
  - `contractlens-backend/src/main/resources/application-dev.example.yml`
  - 内容结构：
    - `contractlens.dev.datasource.password`
    - `contractlens.dev.jwt.secret`
    - `contractlens.dev.dashscope.api-key`
    - （可选）`contractlens.dev.neo4j.password`
- 本机文件（不入库，由你在本地创建）：
  - `contractlens-backend/src/main/resources/application-dev.yml`
  - 由你把 example 复制为 dev，并填入明文

### 3) .gitignore：允许提交 example 文件
- 文件：[.gitignore](file:///e:/code/repository/ContractLens/.gitignore)
- 修改点：
  - 维持 `application-*.yml` 忽略规则（确保 `application-dev.yml` 不入库）
  - 增加白名单：`!application-dev.example.yml`
  - （可选）如果你希望后续也提供 `application-prod.example.yml`，同理增加白名单

### 4) 启动校验（保留，按新占位符结构验证）
- 文件：[StartupConfigValidator.java](file:///e:/code/repository/ContractLens/contractlens-backend/src/main/java/com/contractlens/config/StartupConfigValidator.java)
- 校验逻辑保持不变（仍校验最终落到 `jwt.secret` / `langchain4j...api-key` 的值是否为空）
- 预期效果：
  - 你没创建 `application-dev.yml` 或没填值时：启动直接失败并提示缺失项
  - 你创建了 `application-dev.yml` 并激活 dev profile：启动通过

### 5) 进度文档同步
- 文件：[PROGRESS.md](file:///e:/code/repository/ContractLens/PROGRESS.md)
- 更新点：把“本地密钥配置方式”描述改成 `application-dev.yml`（替换掉 `application-local.yml` 的表述）

## Verification（实现后）
- 检查仓库不再出现 `sk-` 等明文 key（全仓检索）
- 启动行为（两种场景）：
  1. 不创建 `application-dev.yml`：启动失败，报缺少 `jwt.secret` / `DASHSCOPE_API_KEY`（符合预期）
  2. 创建 `application-dev.yml` 且 dev profile 生效：启动成功
- IDE Diagnostics 无编译错误

