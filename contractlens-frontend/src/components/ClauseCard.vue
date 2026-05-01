<template>
  <div class="clause-card" :class="riskLevelClass">
    <div class="card-header">
      <div class="risk-badge">
        <span class="dot"></span>
        {{ risk.risk_level }}风险
      </div>
      <h4 class="risk-type">{{ risk.risk_type }}</h4>
    </div>
    <div class="card-content">
      <div class="content-item">
        <span class="label">条款原文</span>
        <p class="text quote" v-html="clauseTextHtml"></p>
      </div>
      <div class="content-item">
        <span class="label">风险说明</span>
        <p class="text" v-html="riskDescriptionHtml"></p>
      </div>
      <div class="content-row">
        <div class="content-item">
          <span class="label">法律依据</span>
          <p class="text basis">{{ risk.legal_basis }}</p>
        </div>
        <div class="content-item">
          <span class="label">修改建议</span>
          <p class="text suggestion">{{ risk.suggestion }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  risk: {
    type: Object,
    required: true,
  },
  highlightKeywords: {
    type: Array,
    default: () => [],
  },
});

const escapeHtml = (value) => {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
};

const escapeRegExp = (value) => {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
};

const highlightText = (text, keywords) => {
  const escaped = escapeHtml(text);
  const list = (Array.isArray(keywords) ? keywords : []).map((kw) => String(kw || '').trim()).filter(Boolean);
  if (list.length === 0) return escaped;
  const unique = Array.from(new Set(list)).sort((a, b) => b.length - a.length);
  let html = escaped;
  for (const kw of unique) {
    const re = new RegExp(escapeRegExp(kw), 'g');
    html = html.replace(re, `<mark class="hl">${escapeHtml(kw)}</mark>`);
  }
  return html;
};

const riskLevelClass = computed(() => {
  const levels = { '高': 'level-high', '中': 'level-medium', '低': 'level-low' };
  return levels[props.risk.risk_level] || '';
});

const clauseTextHtml = computed(() => highlightText(props.risk?.clause_text, props.highlightKeywords));
const riskDescriptionHtml = computed(() => highlightText(props.risk?.risk_description, props.highlightKeywords));
</script>

<style scoped>
.clause-card {
  background: var(--surface-color);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 1.5rem;
  transition: all 0.2s;
}

.clause-card:hover {
  box-shadow: var(--shadow-md);
  border-color: transparent;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.risk-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

/* Risk Levels */
.level-high { border-left: 4px solid var(--error-color); }
.level-high .risk-badge { background: #fef2f2; color: var(--error-color); }
.level-high .dot { background: var(--error-color); }

.level-medium { border-left: 4px solid var(--warning-color); }
.level-medium .risk-badge { background: #fffbeb; color: var(--warning-color); }
.level-medium .dot { background: var(--warning-color); }

.level-low { border-left: 4px solid var(--success-color); }
.level-low .risk-badge { background: #ecfdf5; color: var(--success-color); }
.level-low .dot { background: var(--success-color); }

.risk-type {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
}

.content-item {
  margin-bottom: 1rem;
}

.label {
  display: block;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  margin-bottom: 0.375rem;
  letter-spacing: 0.025em;
}

.text {
  margin: 0;
  font-size: 0.9375rem;
  line-height: 1.6;
  color: var(--text-primary);
}

.quote {
  padding: 0.75rem 1rem;
  background: #f9fafb;
  border-radius: var(--radius-md);
  font-style: italic;
  color: var(--text-secondary);
}

.hl {
  background: rgba(250, 204, 21, 0.35);
  padding: 0 0.12em;
  border-radius: 0.25em;
}

.basis {
  color: var(--primary-color);
  font-weight: 500;
}

.suggestion {
  color: #065f46;
  background: #f0fdf4;
  padding: 0.5rem 0.75rem;
  border-radius: var(--radius-md);
}

.content-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #f3f4f6;
}
</style>
