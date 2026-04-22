# Streaming Analysis Chat Spec

## Why
当前系统以上传合同后返回完整 JSON 报告为主，适合静态审阅，但缺少逐步解释、追问澄清和分析过程可见性。对于合同审查这种高认知负荷任务，引入流式对话能显著提升感知速度、交互深度和用户信任感。

## What Changes
- 新增流式分析会话能力，允许用户围绕单份合同进行连续追问和增量回答
- 新增后端流式接口，按 token 或段落持续返回分析内容与状态事件
- 前端新增聊天式分析界面，支持消息列表、流式渲染、追问输入和分析中断/重试
- 现有报告页保留，但改为结构化总结视图；对话视图成为深度分析主入口
- 新增会话上下文管理，支持同一合同下的多轮问答
- **BREAKING**: 前端分析主流程从点击分析后直接展示最终 JSON调整为先进入分析会话，再生成和沉淀结构化结果

## Impact
- Affected specs: 合同分析体验, RAG 检索链路, 前端工作台交互, 分析结果呈现
- Affected code: `contractlens-backend/src/main/java/com/contractlens/controller/AnalysisController.java`, `contractlens-backend/src/main/java/com/contractlens/service/AnalysisService.java`, `contractlens-backend/src/main/java/com/contractlens/service/ai/AiContractAnalyst.java`, `contractlens-frontend/src/api/contract.js`, `contractlens-frontend/src/views/Dashboard.vue`, `contractlens-frontend/src/router/index.js`, `contractlens-frontend/src/components/*`

## ADDED Requirements
### Requirement: 流式分析会话
系统 SHALL 为每份合同提供流式分析会话，用户在分析过程中可以实时看到模型输出，而不是只能等待最终结果。

#### Scenario: 首次发起流式分析
- **WHEN** 用户在控制台中选择一份合同并点击开始对话式分析
- **THEN** 前端进入聊天式分析界面
- **AND** 后端以流式方式持续返回分析内容
- **AND** 前端逐段渲染模型回答与处理状态

#### Scenario: 分析中展示阶段状态
- **WHEN** 后端正在进行检索、推理、生成等阶段
- **THEN** 流中应包含可识别的阶段事件或状态信息
- **AND** 前端应以非阻塞方式展示检索中 / 生成中 / 已完成等状态

#### Scenario: 分析失败
- **WHEN** 流式分析过程中发生网络异常、模型异常或超时
- **THEN** 前端应保留已收到内容
- **AND** 明确提示失败原因或失败状态
- **AND** 提供重试入口

### Requirement: 多轮追问
系统 SHALL 支持基于同一合同上下文的多轮追问，避免每次重新上传或重新开始完整分析。

#### Scenario: 用户追加问题
- **WHEN** 用户在同一合同的会话中输入押金条款有哪些具体风险？之类的问题
- **THEN** 后端应结合该合同内容、已有检索上下文和会话历史生成回答
- **AND** 前端应将问题和回答追加到消息列表中

#### Scenario: 切换合同
- **WHEN** 用户切换到另一份合同
- **THEN** 系统应切换到对应合同的独立会话上下文
- **AND** 不得混用上一份合同的聊天历史

### Requirement: 结构化结果沉淀
系统 SHALL 在流式会话完成后，仍可生成结构化风险结果，用于摘要面板、风险卡片和后续历史记录展示。

#### Scenario: 流式分析完成
- **WHEN** 一轮流式分析正常结束
- **THEN** 后端应产出可保存的结构化分析结果
- **AND** 前端应同步刷新摘要面板和风险条款卡片

#### Scenario: 用户只查看摘要
- **WHEN** 用户不进入聊天细节而只查看结构化总结
- **THEN** 系统仍应提供风险评分、风险等级、标签和分视角条款列表

### Requirement: 聊天式前端体验
系统 SHALL 提供面向分析任务的聊天界面，而不是通用 IM 界面，需兼顾过程可见与结果可回看。

#### Scenario: 聊天区布局
- **WHEN** 用户进入对话式分析页面
- **THEN** 页面应包含合同信息区、消息区、输入区、状态区和结构化摘要区
- **AND** 消息区支持流式追加
- **AND** 输入区支持禁用态与加载态

#### Scenario: 长消息阅读
- **WHEN** 模型返回较长法律分析内容
- **THEN** 前端应保证段落清晰、引用可识别、建议与风险点易扫描
- **AND** 不得退化为不可读的大段纯文本

## MODIFIED Requirements
### Requirement: 合同分析入口
系统 SHALL 同时提供结构化报告模式和流式对话模式，其中流式对话模式作为默认分析入口。

#### Scenario: 用户点击分析按钮
- **WHEN** 用户从控制台点击分析某份合同
- **THEN** 默认进入该合同的流式分析会话
- **AND** 系统在会话过程中持续更新结构化摘要区域
- **AND** 用户可切换查看摘要与对话两种视图

### Requirement: 分析结果展示
系统 SHALL 将原有单次结果展示页面改造为摘要 + 对话 + 追问的组合体验。

#### Scenario: 查看分析结果
- **WHEN** 用户分析完成后返回控制台
- **THEN** 页面应优先展示摘要卡片与高风险条款
- **AND** 用户可以展开完整对话记录查看分析依据与追问历史

## REMOVED Requirements
### Requirement: 仅支持一次性完整返回分析结果
**Reason**: 该模式反馈慢、交互浅，不适合作为复杂合同审查的唯一入口。
**Migration**: 保留现有结构化结果作为摘要输出层，但将其改为流式会话的沉淀结果，而非唯一交互模式。
