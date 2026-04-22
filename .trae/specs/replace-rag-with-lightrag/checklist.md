- [ ] /api/knowledge/rebuild 同时构建向量索引（Chroma）与图谱索引（Neo4j），重复调用保持幂等
- [ ] Neo4j 图谱最小 schema 落地：KnowledgeDoc/LawArticle/RiskType 节点与 HAS_* 关系可查询
- [ ] /api/knowledge/status 返回“真实且可解释”的探测字段：returnedSegments、topK、阈值、probeQuery（以及图谱侧指标）
- [ ] 前端 Knowledge/Settings 页展示为“探测返回条数（topK/阈值）”，不再出现易误解的“命中数”表述
- [ ] AnalysisService 的检索逻辑已替换为混合检索 + 融合（RRF），并同时写入 retrieved_context 与 graph_context
- [ ] 在 knowledge_docs≈200 的环境下，完成 rebuild 后：status 探测有返回；分析一次合同后 graph_context 非空且与 probe 语义一致
- [ ] 失败模式明确：Neo4j 不可用时系统给出清晰错误（或可控降级策略），不会产生“看起来正常但其实没用到图谱”的假象
- [ ] PROGRESS.md 更新包含：混合检索说明、status 指标解释、Neo4j/Chroma 启动与运维要点

