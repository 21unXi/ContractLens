<template>
  <section class="summary-container">
    <div class="summary-header">
      <div>
        <span class="eyebrow">风险总览</span>
        <h3>合同整体画像</h3>
      </div>
      <div class="risk-pill" :class="scoreColorClass">{{ summary.risk_level }}风险</div>
    </div>

    <div class="summary-grid">
      <div class="score-panel">
        <div class="score-ring" :class="scoreColorClass">
          <div class="score-inner">
            <span class="score-value">{{ normalizedScore }}</span>
            <span class="score-unit">/ 100</span>
          </div>
        </div>
        <div class="score-copy">
          <span class="metric-label">综合评分</span>
          <p>{{ scoreDescription }}</p>
        </div>
      </div>

      <div class="insight-panel">
        <div class="metric-card">
          <span class="metric-label">高风险标签</span>
          <strong>{{ tagCount }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">分析结论</span>
          <strong>{{ summary.risk_level || '未评级' }}</strong>
        </div>
      </div>
    </div>

    <div class="summary-body">
      <div class="summary-block">
        <span class="block-label">一句话判断</span>
        <p class="summary-text">{{ summary.summary || '暂无摘要' }}</p>
      </div>

      <div class="summary-block">
        <span class="block-label">合同标签</span>
        <div class="tags-container">
          <span v-for="tag in tags" :key="tag" class="tag">{{ tag }}</span>
          <span v-if="tags.length === 0" class="tag tag-muted">暂无标签</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  summary: {
    type: Object,
    required: true,
  },
});

const normalizedScore = computed(() => Number(props.summary.risk_score || 0));
const tags = computed(() => props.summary.contract_tags || []);
const tagCount = computed(() => tags.value.length);

const scoreColorClass = computed(() => {
  if (normalizedScore.value >= 80) return 'score-high';
  if (normalizedScore.value >= 50) return 'score-medium';
  return 'score-low';
});

const scoreDescription = computed(() => {
  if (normalizedScore.value >= 80) return '建议优先处理高风险条款，再进行签署';
  if (normalizedScore.value >= 50) return '存在多处需要复核的条款，建议二次审查';
  return '整体风险相对可控，仍建议保留关键凭证';
});
</script>

<style scoped>
.summary-container {
  padding: 1.5rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(135deg, rgba(79, 70, 229, 0.06), rgba(16, 185, 129, 0.04)),
    var(--surface-color);
}

.summary-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.eyebrow {
  display: inline-block;
  margin-bottom: 0.5rem;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--primary-color);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.summary-header h3 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
}

.risk-pill {
  padding: 0.5rem 0.9rem;
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 700;
}

.summary-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.score-panel {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  padding: 1rem;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.72);
}

.score-ring {
  width: 112px;
  height: 112px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: currentColor;
  flex-shrink: 0;
}

.score-inner {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: var(--surface-color);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.score-value {
  font-size: 1.75rem;
  font-weight: 800;
  line-height: 1;
}

.score-unit {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.score-copy {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.score-copy p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.6;
  font-size: 0.9375rem;
}

.insight-panel {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

.metric-card {
  padding: 1rem;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.72);
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.metric-card strong {
  font-size: 1.375rem;
  color: var(--text-primary);
}

.metric-label,
.block-label {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-secondary);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.summary-body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.summary-block {
  padding: 1rem 1.125rem;
  background: rgba(255, 255, 255, 0.72);
  border-radius: var(--radius-lg);
}

.summary-text {
  margin: 0.75rem 0 0;
  line-height: 1.7;
  color: var(--text-primary);
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.75rem;
}

.tag {
  display: inline-flex;
  align-items: center;
  padding: 0.45rem 0.75rem;
  border-radius: 999px;
  background: #eef2ff;
  color: var(--primary-color);
  font-size: 0.8125rem;
  font-weight: 600;
}

.tag-muted {
  background: #f3f4f6;
  color: var(--text-secondary);
}

.score-high {
  color: #dc2626;
}

.score-high.risk-pill {
  background: #fef2f2;
}

.score-medium {
  color: #d97706;
}

.score-medium.risk-pill {
  background: #fffbeb;
}

.score-low {
  color: #059669;
}

.score-low.risk-pill {
  background: #ecfdf5;
}

@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .score-panel {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-header {
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .summary-container {
    padding: 1rem;
  }

  .score-panel {
    gap: 1rem;
  }
}
</style>
