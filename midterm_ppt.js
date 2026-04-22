const pptxgen = require("pptxgenjs");

// ===== 配色常量 =====
const C = {
  primary: "028090",    // 深青蓝
  secondary: "00A896",  // 青绿
  accent: "02C39A",     // 薄荷绿
  dark: "1A2F38",       // 深色背景
  darkMid: "1F3D44",    // 中深色
  white: "FFFFFF",
  lightBg: "F0F9F7",    // 浅青白背景
  textDark: "1A2F38",
  textMuted: "5A7A7E",
  gold: "F59E0B",
  cardBg: "F8FFFE",
};

// ===== 创建演示文稿 =====
const pres = new pptxgen();
pres.layout = "LAYOUT_16x9";
pres.title = "ContractLens 中期汇报";
pres.author = "ContractLens Team";

// ===== 工具函数 =====
function makeShadow() {
  return { type: "outer", blur: 8, offset: 3, angle: 135, color: "000000", opacity: 0.12 };
}

function accentBar(slide, x, y, h, color = C.primary) {
  slide.addShape(pres.shapes.RECTANGLE, { x, y, w: 0.07, h, fill: { color }, line: { color } });
}

function chip(slide, text, x, y, bg = C.secondary, fg = C.white) {
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x, y, w: text.length * 0.22 + 0.3, h: 0.38,
    fill: { color: bg }, rectRadius: 0.19,
  });
  slide.addText(text, {
    x, y, w: text.length * 0.22 + 0.3, h: 0.38,
    fontSize: 11, color: fg, align: "center", valign: "middle", bold: true,
  });
}

// ===== Slide 1: 封面 =====
{
  const s = pres.addSlide();
  s.background = { color: C.dark };

  // 装饰圆形
  s.addShape(pres.shapes.OVAL, { x: 7.5, y: -1.2, w: 4.5, h: 4.5, fill: { color: C.primary, transparency: 25 } });
  s.addShape(pres.shapes.OVAL, { x: -0.8, y: 3.5, w: 3, h: 3, fill: { color: C.secondary, transparency: 35 } });

  // 项目名
  s.addText("ContractLens", {
    x: 0.7, y: 1.5, w: 8, h: 1,
    fontSize: 56, fontFace: "Arial Black", color: C.white, bold: true,
  });

  // 副标题
  s.addText("租房护航 · 智能合同风险审查平台", {
    x: 0.7, y: 2.55, w: 8, h: 0.6,
    fontSize: 22, color: C.accent,
  });

  // 分隔线
  s.addShape(pres.shapes.RECTANGLE, { x: 0.7, y: 3.3, w: 4, h: 0.05, fill: { color: C.secondary } });

  // 标签
  s.addText("中期汇报", {
    x: 0.7, y: 3.55, w: 3, h: 0.45,
    fontSize: 15, color: C.textMuted,
  });

  // 底部装饰
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 5.15, w: 10, h: 0.475, fill: { color: C.primary, transparency: 60 } });
  s.addText("RAG · Spring Boot · Vue 3 · Chroma · Neo4j", {
    x: 0.7, y: 5.18, w: 8, h: 0.4,
    fontSize: 11, color: C.accent, italic: true,
  });
}

// ===== Slide 2: 项目概述 =====
{
  const s = pres.addSlide();
  s.background = { color: C.lightBg };

  // 标题栏
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 10, h: 0.95, fill: { color: C.dark } });
  s.addText("项目概述", { x: 0.5, y: 0.18, w: 6, h: 0.6, fontSize: 26, color: C.white, bold: true });

  // 项目定位
  s.addText("项目定位", { x: 0.5, y: 1.2, w: 4, h: 0.45, fontSize: 16, color: C.primary, bold: true });
  accentBar(s, 0.4, 1.2, 0.45);
  s.addText(
    "ContractLens 是一款专注住房租赁合同的 AI 智能风险审查工具。\n" +
    "用户上传合同，系统基于 RAG 技术 + 法律知识库，\n" +
    "自动识别风险条款并给出专业修改建议。",
    { x: 0.5, y: 1.7, w: 5.8, h: 1.1, fontSize: 14, color: C.textDark }
  );

  // 目标用户
  s.addText("目标用户", { x: 0.5, y: 2.95, w: 4, h: 0.45, fontSize: 16, color: C.primary, bold: true });
  accentBar(s, 0.4, 2.95, 0.45);
  const users = ["首次租房大学生", "城市租客", "房东"];
  users.forEach((u, i) => {
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: 0.5 + i * 1.85, y: 3.45, w: 1.7, h: 0.5,
      fill: { color: C.secondary }, rectRadius: 0.25,
    });
    s.addText(u, { x: 0.5 + i * 1.85, y: 3.45, w: 1.7, h: 0.5, fontSize: 12, color: C.white, align: "center", valign: "middle" });
  });

  // 右侧：核心价值
  s.addText("核心价值", { x: 6.6, y: 1.2, w: 3.2, h: 0.45, fontSize: 16, color: C.primary, bold: true });
  accentBar(s, 6.5, 1.2, 0.45);
  const values = [
    { icon: "⚖", title: "垂直场景深度", desc: "专为租房合同设计" },
    { icon: "📚", title: "RAG 增强分析", desc: "基于真实法律条文" },
    { icon: "🔍", title: "双视角分析", desc: "房东 + 租客" },
    { icon: "📎", title: "法律依据", desc: "每条风险附法条" },
  ];
  values.forEach((v, i) => {
    const y = 1.75 + i * 0.82;
    s.addShape(pres.shapes.RECTANGLE, { x: 6.5, y, w: 3.1, h: 0.72, fill: { color: C.white }, shadow: makeShadow() });
    s.addText(v.icon, { x: 6.6, y: y + 0.05, w: 0.55, h: 0.55, fontSize: 22, align: "center", valign: "middle" });
    s.addText(v.title, { x: 7.2, y: y + 0.06, w: 2.2, h: 0.32, fontSize: 13, color: C.textDark, bold: true });
    s.addText(v.desc, { x: 7.2, y: y + 0.36, w: 2.2, h: 0.28, fontSize: 10, color: C.textMuted });
  });

  // 风险类型底部
  s.addText("常见风险类型", { x: 0.5, y: 4.1, w: 3, h: 0.4, fontSize: 14, color: C.primary, bold: true });
  const risks = ["押金条款", "租金条款", "提前解约", "维修责任", "转租条款", "房屋交付", "费用约定"];
  risks.forEach((r, i) => {
    chip(s, r, 0.5 + i * 1.3, 4.52, i % 2 === 0 ? C.secondary : C.primary);
  });
}

// ===== Slide 3: 技术架构 =====
{
  const s = pres.addSlide();
  s.background = { color: C.white };

  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 10, h: 0.95, fill: { color: C.dark } });
  s.addText("技术架构", { x: 0.5, y: 0.18, w: 6, h: 0.6, fontSize: 26, color: C.white, bold: true });

  // 三层架构
  const layers = [
    {
      label: "前端", color: C.accent,
      items: ["Vue 3 + Vite", "Axios", "Pinia 状态管理", "ECharts 可视化"],
      x: 0.5,
    },
    {
      label: "后端", color: C.primary,
      items: ["Spring Boot 3.x", "LangChain4j RAG", "JWT 鉴权", "MySQL + Neo4j"],
      x: 3.7,
    },
    {
      label: "AI & 数据", color: C.darkMid,
      items: ["通义千问 (qwen-plus)", "text-embedding-v3", "Chroma 向量库", "Neo4j 图数据库"],
      x: 6.9,
    },
  ];

  layers.forEach((layer, li) => {
    // 顶层
    s.addShape(pres.shapes.RECTANGLE, { x: layer.x, y: 1.2, w: 2.9, h: 0.5, fill: { color: layer.color } });
    s.addText(layer.label, { x: layer.x, y: 1.2, w: 2.9, h: 0.5, fontSize: 16, color: C.white, bold: true, align: "center", valign: "middle" });
    // 内容卡
    s.addShape(pres.shapes.RECTANGLE, {
      x: layer.x, y: 1.72, w: 2.9, h: 2.4,
      fill: { color: C.cardBg }, line: { color: layer.color, width: 1.5 },
    });
    layer.items.forEach((item, i) => {
      s.addText("▸ " + item, {
        x: layer.x + 0.15, y: 1.85 + i * 0.55, w: 2.6, h: 0.5,
        fontSize: 13, color: C.textDark,
      });
    });
  });

  // 数据流箭头
  s.addShape(pres.shapes.LINE, { x: 1.45, y: 4.2, w: 7.1, h: 0, line: { color: C.secondary, width: 1.5, dashType: "dash" } });
  s.addText("合同上传 → RAG 检索 → LLM 分析 → 风险报告", {
    x: 1.4, y: 4.3, w: 7.2, h: 0.4,
    fontSize: 12, color: C.primary, align: "center", italic: true,
  });

  // 底部技术栈标签
  const techs = ["Java 17", "Spring Boot 3", "MySQL 8", "Chroma", "Neo4j", "LangChain4j", "通义千问", "Vue 3"];
  techs.forEach((t, i) => {
    chip(s, t, 0.5 + i * 1.15, 4.9, i % 3 === 0 ? C.secondary : C.primary);
  });
}

// ===== Slide 4: 功能演示 =====
{
  const s = pres.addSlide();
  s.background = { color: C.lightBg };

  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 10, h: 0.95, fill: { color: C.dark } });
  s.addText("核心功能演示", { x: 0.5, y: 0.18, w: 6, h: 0.6, fontSize: 26, color: C.white, bold: true });

  const features = [
    {
      num: "01", title: "合同上传解析",
      desc: "支持 txt / docx / pdf，自动提取合同文本内容，落盘存储并入库",
      tags: ["多格式支持", "自动解析"],
    },
    {
      num: "02", title: "结构化风险分析",
      desc: "基于 RAG 检索法律条文，AI 生成结构化风险清单，附法条依据",
      tags: ["RAG", "法律依据"],
    },
    {
      num: "03", title: "流式对话分析（SSE）",
      desc: "实时流式输出分析过程，支持多轮追问，返回结构化摘要与条款卡片",
      tags: ["流式SSE", "多轮会话"],
    },
    {
      num: "04", title: "知识库管理",
      desc: "MySQL 文档源 + Chroma 向量库，支持 rebuild 重建索引、状态探测",
      tags: ["知识库", "向量检索"],
    },
  ];

  features.forEach((f, i) => {
    const col = i % 2;
    const row = Math.floor(i / 2);
    const x = 0.5 + col * 4.7;
    const y = 1.2 + row * 2.05;

    s.addShape(pres.shapes.RECTANGLE, { x, y, w: 4.4, h: 1.85, fill: { color: C.white }, shadow: makeShadow() });
    // 序号
    s.addShape(pres.shapes.OVAL, { x: x + 0.15, y: y + 0.15, w: 0.6, h: 0.6, fill: { color: C.primary } });
    s.addText(f.num, { x: x + 0.15, y: y + 0.15, w: 0.6, h: 0.6, fontSize: 14, color: C.white, bold: true, align: "center", valign: "middle" });
    // 标题
    s.addText(f.title, { x: x + 0.9, y: y + 0.18, w: 3.3, h: 0.42, fontSize: 16, color: C.textDark, bold: true });
    // 描述
    s.addText(f.desc, { x: x + 0.2, y: y + 0.75, w: 4, h: 0.7, fontSize: 12, color: C.textMuted });
    // 标签
    f.tags.forEach((t, ti) => {
      chip(s, t, x + 0.2 + ti * 1.3, y + 1.45, ti === 0 ? C.secondary : C.primary);
    });
  });
}

// ===== Slide 5: 知识库设计 =====
{
  const s = pres.addSlide();
  s.background = { color: C.white };

  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 10, h: 0.95, fill: { color: C.dark } });
  s.addText("知识库设计", { x: 0.5, y: 0.18, w: 6, h: 0.6, fontSize: 26, color: C.white, bold: true });

  // 左侧：数据源
  s.addText("文档来源", { x: 0.5, y: 1.15, w: 3, h: 0.4, fontSize: 15, color: C.primary, bold: true });
  accentBar(s, 0.4, 1.15, 0.4);

  const docs = [
    { type: "law", label: "法律法规", color: C.primary, count: "7 条" },
    { type: "case", label: "典型案例", color: C.secondary, count: "规划中" },
    { type: "guide", label: "审查指南", color: C.accent, count: "规划中" },
    { type: "risk", label: "风险清单", color: C.gold, count: "7 类" },
  ];
  docs.forEach((d, i) => {
    const y = 1.65 + i * 0.7;
    s.addShape(pres.shapes.RECTANGLE, { x: 0.5, y, w: 4.2, h: 0.6, fill: { color: C.cardBg }, shadow: makeShadow() });
    s.addShape(pres.shapes.RECTANGLE, { x: 0.5, y, w: 0.08, h: 0.6, fill: { color: d.color } });
    s.addText(d.label, { x: 0.72, y: y + 0.05, w: 2.2, h: 0.28, fontSize: 13, color: C.textDark, bold: true });
    s.addText("来源: knowledge_docs 表", { x: 0.72, y: y + 0.3, w: 2.2, h: 0.22, fontSize: 10, color: C.textMuted });
    s.addText(d.count, { x: 3.5, y, w: 1.1, h: 0.6, fontSize: 14, color: d.color, bold: true, align: "center", valign: "middle" });
  });

  // 中间：向量检索流程
  s.addText("向量检索流程", { x: 5.0, y: 1.15, w: 4.5, h: 0.4, fontSize: 15, color: C.primary, bold: true });
  accentBar(s, 4.9, 1.15, 0.4);

  const steps = [
    { text: "合同文本 → Embedding", icon: "1" },
    { text: "知识库文档 → Embedding", icon: "2" },
    { text: "向量相似度检索 Top-K", icon: "3" },
    { text: "拼接上下文 → LLM 分析", icon: "4" },
  ];
  steps.forEach((step, i) => {
    const y = 1.65 + i * 0.78;
    s.addShape(pres.shapes.OVAL, { x: 5.0, y: y + 0.08, w: 0.5, h: 0.5, fill: { color: C.primary } });
    s.addText(step.icon, { x: 5.0, y: y + 0.08, w: 0.5, h: 0.5, fontSize: 14, color: C.white, bold: true, align: "center", valign: "middle" });
    s.addText(step.text, { x: 5.65, y: y + 0.08, w: 4, h: 0.5, fontSize: 13, color: C.textDark, valign: "middle" });
    if (i < steps.length - 1) {
      s.addShape(pres.shapes.LINE, { x: 5.25, y: y + 0.6, w: 0, h: 0.18, line: { color: C.secondary, width: 1.5 } });
    }
  });

  // 底部：已入库法条
  s.addText("已入库法条", { x: 0.5, y: 4.55, w: 3, h: 0.35, fontSize: 13, color: C.primary, bold: true });
  const articles = ["民法典 563 条（合同解除）", "民法典 585 条（违约金）", "民法典 708 条（租赁合同内容）",
    "民法典 713 条（维修义务）", "民法典 716 条（转租）", "民法典 721 条（租金支付）", "民法典 722 条（租金标准）"];
  articles.forEach((a, i) => {
    chip(s, a, 0.5 + i * 1.33, 4.95, i % 2 === 0 ? C.darkMid : C.primary);
  });
}

// ===== Slide 6: 已完成功能 =====
{
  const s = pres.addSlide();
  s.background = { color: C.lightBg };

  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 10, h: 0.95, fill: { color: C.dark } });
  s.addText("已完成功能", { x: 0.5, y: 0.18, w: 6, h: 0.6, fontSize: 26, color: C.white, bold: true });
  chip(s, "中期", 8.3, 0.22, C.accent);

  // API 表格
  const apis = [
    { method: "POST", path: "/api/auth/register", desc: "用户注册" },
    { method: "POST", path: "/api/auth/login", desc: "用户登录（返回 JWT）" },
    { method: "POST", path: "/api/contracts/upload", desc: "上传合同（txt/docx/pdf）" },
    { method: "GET", path: "/api/contracts", desc: "获取当前用户合同列表" },
    { method: "GET", path: "/api/contracts/{id}", desc: "获取合同详情" },
    { method: "DELETE", path: "/api/contracts/{id}", desc: "删除合同（硬删除）" },
    { method: "POST", path: "/api/analysis/contracts/{id}", desc: "结构化风险分析" },
    { method: "POST", path: "/api/analysis/contracts/{id}/stream", desc: "流式对话分析（SSE）" },
    { method: "GET", path: "/api/knowledge/status", desc: "知识库状态探测" },
    { method: "GET", path: "/api/knowledge/docs", desc: "知识库文档列表" },
    { method: "POST", path: "/api/knowledge/rebuild", desc: "重建向量库索引" },
  ];

  s.addText("API 接口一览", { x: 0.5, y: 1.1, w: 4, h: 0.4, fontSize: 14, color: C.primary, bold: true });

  // 表头
  s.addShape(pres.shapes.RECTANGLE, { x: 0.5, y: 1.52, w: 9, h: 0.42, fill: { color: C.darkMid } });
  s.addText("方法", { x: 0.5, y: 1.52, w: 0.9, h: 0.42, fontSize: 11, color: C.white, bold: true, align: "center", valign: "middle" });
  s.addText("路径", { x: 1.4, y: 1.52, w: 4.2, h: 0.42, fontSize: 11, color: C.white, bold: true, valign: "middle" });
  s.addText("说明", { x: 5.6, y: 1.52, w: 3.9, h: 0.42, fontSize: 11, color: C.white, bold: true, valign: "middle" });

  apis.forEach((api, i) => {
    const y = 1.95 + i * 0.37;
    const bg = i % 2 === 0 ? C.white : C.cardBg;
    s.addShape(pres.shapes.RECTANGLE, { x: 0.5, y, w: 9, h: 0.37, fill: { color: bg } });
    const methodColor = api.method === "POST" ? C.secondary : api.method === "GET" ? C.primary : "C0392B";
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: 0.6, y: y + 0.05, w: 0.7, h: 0.27,
      fill: { color: methodColor }, rectRadius: 0.05,
    });
    s.addText(api.method, { x: 0.6, y: y + 0.05, w: 0.7, h: 0.27, fontSize: 9, color: C.white, bold: true, align: "center", valign: "middle" });
    s.addText(api.path, { x: 1.4, y, w: 4.2, h: 0.37, fontSize: 10.5, color: C.textDark, valign: "middle", fontFace: "Consolas" });
    s.addText(api.desc, { x: 5.6, y, w: 3.9, h: 0.37, fontSize: 10.5, color: C.textMuted, valign: "middle" });
  });

  // 完成度统计
  s.addShape(pres.shapes.RECTANGLE, { x: 0.5, y: 6.1, w: 9, h: 0.42, fill: { color: C.darkMid } });
  s.addText("✅ 认证   ✅ 合同管理   ✅ 结构化分析   ✅ 流式SSE分析   ✅ 知识库（RAG）   ✅ 前端工作台", {
    x: 0.5, y: 6.1, w: 9, h: 0.42,
    fontSize: 11, color: C.accent, align: "center", valign: "middle",
  });
}

// ===== Slide 7: 系统架构图 =====
{
  const s = pres.addSlide();
  s.background = { color: C.white };

  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 10, h: 0.95, fill: { color: C.dark } });
  s.addText("系统架构", { x: 0.5, y: 0.18, w: 6, h: 0.6, fontSize: 26, color: C.white, bold: true });

  // 用户层
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 4, y: 1.15, w: 2, h: 0.55, fill: { color: C.accent }, rectRadius: 0.1 });
  s.addText("用户端", { x: 4, y: 1.15, w: 2, h: 0.55, fontSize: 14, color: C.white, bold: true, align: "center", valign: "middle" });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 3.3, y: 1.15, w: 0.6, h: 0.55, fill: { color: C.secondary }, rectRadius: 0.1 });
  s.addText("Web", { x: 3.3, y: 1.15, w: 0.6, h: 0.55, fontSize: 10, color: C.white, bold: true, align: "center", valign: "middle" });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 6.1, y: 1.15, w: 0.6, h: 0.55, fill: { color: C.secondary }, rectRadius: 0.1 });
  s.addText("API", { x: 6.1, y: 1.15, w: 0.6, h: 0.55, fontSize: 10, color: C.white, bold: true, align: "center", valign: "middle" });

  // 箭头
  s.addShape(pres.shapes.LINE, { x: 5, y: 1.72, w: 0, h: 0.28, line: { color: C.primary, width: 2 } });

  // 后端层
  s.addShape(pres.shapes.RECTANGLE, { x: 0.5, y: 2.05, w: 9, h: 0.55, fill: { color: C.primary } });
  s.addText("Spring Boot 后端（Java 17）", { x: 0.5, y: 2.05, w: 9, h: 0.55, fontSize: 14, color: C.white, bold: true, align: "center", valign: "middle" });

  // 服务模块
  const modules = ["AuthService", "ContractService", "AnalysisService", "KnowledgeService"];
  modules.forEach((m, i) => {
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5 + i * 2.25, y: 2.7, w: 2.1, h: 0.5, fill: { color: C.cardBg }, line: { color: C.primary, width: 1 }, rectRadius: 0.08 });
    s.addText(m, { x: 0.5 + i * 2.25, y: 2.7, w: 2.1, h: 0.5, fontSize: 11, color: C.textDark, align: "center", valign: "middle" });
  });

  // LangChain4j 层
  s.addShape(pres.shapes.RECTANGLE, { x: 0.5, y: 3.35, w: 9, h: 0.45, fill: { color: C.darkMid } });
  s.addText("LangChain4j（RAG 编排）", { x: 0.5, y: 3.35, w: 9, h: 0.45, fontSize: 13, color: C.accent, bold: true, align: "center", valign: "middle" });

  const lc_modules = ["EmbeddingModel", "Retriever", "ChatModel"];
  lc_modules.forEach((m, i) => {
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.8 + i * 3, y: 3.9, w: 2.6, h: 0.48, fill: { color: C.lightBg }, line: { color: C.secondary, width: 1 }, rectRadius: 0.08 });
    s.addText(m, { x: 0.8 + i * 3, y: 3.9, w: 2.6, h: 0.48, fontSize: 12, color: C.textDark, align: "center", valign: "middle" });
  });

  // 箭头指向数据层
  s.addShape(pres.shapes.LINE, { x: 5, y: 4.4, w: 0, h: 0.25, line: { color: C.secondary, width: 1.5 } });

  // 数据层
  const dbModules = [
    { name: "MySQL", desc: "用户/合同/分析结果", color: C.primary },
    { name: "Chroma", desc: "向量知识库", color: C.secondary },
    { name: "Neo4j", desc: "图数据库（待接入）", color: C.textMuted },
    { name: "通义千问", desc: "LLM + Embedding", color: C.gold },
  ];
  dbModules.forEach((db, i) => {
    const x = 0.5 + i * 2.35;
    s.addShape(pres.shapes.RECTANGLE, { x, y: 4.72, w: 2.2, h: 0.8, fill: { color: db.color }, shadow: makeShadow() });
    s.addText(db.name, { x, y: 4.72, w: 2.2, h: 0.45, fontSize: 13, color: C.white, bold: true, align: "center", valign: "middle" });
    s.addText(db.desc, { x, y: 5.12, w: 2.2, h: 0.35, fontSize: 9, color: C.white, align: "center", valign: "middle" });
  });
}

// ===== Slide 8: 后续计划 =====
{
  const s = pres.addSlide();
  s.background = { color: C.dark };

  s.addText("后续计划", { x: 0.5, y: 0.4, w: 6, h: 0.7, fontSize: 30, color: C.white, bold: true });
  s.addShape(pres.shapes.RECTANGLE, { x: 0.5, y: 1.05, w: 3, h: 0.06, fill: { color: C.accent } });

  const plans = [
    {
      phase: "P2", title: "GraphRAG 接入",
      desc: "将 Neo4j 图数据库接入分析链路，实现向量检索 + 图谱检索的 RRF 重排序融合",
      status: "待开始", statusColor: C.textMuted,
    },
    {
      phase: "P1", title: "合同归属校验",
      desc: "修复合同详情接口的越权风险，增加用户归属校验逻辑",
      status: "待优化", statusColor: C.gold,
    },
    {
      phase: "P1", title: "知识库数据治理",
      desc: "完善导入工具、案例库、审查指南扩充，支持按类型统计与管理",
      status: "规划中", statusColor: C.secondary,
    },
    {
      phase: "P2", title: "分析历史聚合",
      desc: "支持按用户列出历史分析结果摘要，一键重新打开最近分析记录",
      status: "规划中", statusColor: C.secondary,
    },
    {
      phase: "P3", title: "多租户与部署",
      desc: "支持多用户管理、生产环境部署文档、运维监控接入",
      status: "远期", statusColor: C.textMuted,
    },
  ];

  plans.forEach((p, i) => {
    const y = 1.35 + i * 0.82;
    // 卡片
    s.addShape(pres.shapes.RECTANGLE, { x: 0.5, y, w: 9, h: 0.72, fill: { color: C.darkMid } });
    // Phase 标签
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.65, y: y + 0.18, w: 0.55, h: 0.38, fill: { color: C.secondary }, rectRadius: 0.06 });
    s.addText(p.phase, { x: 0.65, y: y + 0.18, w: 0.55, h: 0.38, fontSize: 10, color: C.white, bold: true, align: "center", valign: "middle" });
    // 标题
    s.addText(p.title, { x: 1.35, y: y + 0.08, w: 3.5, h: 0.35, fontSize: 15, color: C.white, bold: true });
    // 描述
    s.addText(p.desc, { x: 1.35, y: y + 0.4, w: 6.2, h: 0.3, fontSize: 11, color: C.textMuted });
    // 状态
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 8.3, y: y + 0.2, w: 1, h: 0.35, fill: { color: p.statusColor }, rectRadius: 0.06 });
    s.addText(p.status, { x: 8.3, y: y + 0.2, w: 1, h: 0.35, fontSize: 10, color: C.white, bold: true, align: "center", valign: "middle" });
  });
}

// ===== Slide 9: 结束页 =====
{
  const s = pres.addSlide();
  s.background = { color: C.dark };

  s.addShape(pres.shapes.OVAL, { x: -1.5, y: -1, w: 5, h: 5, fill: { color: C.primary, transparency: 30 } });
  s.addShape(pres.shapes.OVAL, { x: 7, y: 3, w: 4, h: 4, fill: { color: C.secondary, transparency: 30 } });

  s.addText("感谢聆听", {
    x: 0.5, y: 1.8, w: 9, h: 1,
    fontSize: 52, fontFace: "Arial Black", color: C.white, bold: true, align: "center",
  });

  s.addShape(pres.shapes.RECTANGLE, { x: 4, y: 2.9, w: 2, h: 0.06, fill: { color: C.accent } });

  s.addText("ContractLens · 租房护航", {
    x: 0.5, y: 3.15, w: 9, h: 0.6,
    fontSize: 18, color: C.accent, align: "center",
  });

  s.addText("RAG 智能合同风险审查 · Spring Boot + Vue 3", {
    x: 0.5, y: 3.8, w: 9, h: 0.4,
    fontSize: 13, color: C.textMuted, align: "center",
  });

  // 关键指标
  const stats = [
    { value: "11", label: "API 接口" },
    { value: "5", label: "核心模块" },
    { value: "14", label: "知识库文档" },
    { value: "100%", label: "前端完成" },
  ];
  stats.forEach((st, i) => {
    const x = 1.5 + i * 2;
    s.addText(st.value, { x, y: 4.45, w: 1.8, h: 0.6, fontSize: 32, color: C.accent, bold: true, align: "center" });
    s.addText(st.label, { x, y: 5.0, w: 1.8, h: 0.35, fontSize: 11, color: C.textMuted, align: "center" });
  });
}

// ===== 输出文件 =====
const outputPath = "C:/Users/unXi/Desktop/ContractLens_中期汇报.pptx";
pres.writeFile({ fileName: outputPath })
  .then(() => console.log("✅ PPT 生成成功: " + outputPath))
  .catch(err => console.error("❌ 生成失败:", err));
