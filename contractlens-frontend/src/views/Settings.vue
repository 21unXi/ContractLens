<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h2>设置</h2>
        <p class="sub">查看账户信息与知识库状态</p>
      </div>
    </header>

    <div v-if="error" class="banner banner-error">{{ error }}</div>

    <section class="card">
      <div class="card-header">
        <h3>账户</h3>
      </div>
      <div class="card-body">
        <div class="kv">
          <span class="k">登录状态</span>
          <span class="v">{{ isAuthenticated ? '已登录' : '未登录' }}</span>
        </div>
        <div class="kv">
          <span class="k">Token</span>
          <span class="v mono">{{ tokenPreview }}</span>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="card-header">
        <h3>知识库</h3>
        <button class="primary-btn" :disabled="rebuilding" @click="rebuild">
          {{ rebuilding ? '重建中...' : '重建向量库' }}
        </button>
      </div>
      <div class="card-body">
        <div class="kv">
          <span class="k">文档数量</span>
          <span class="v">{{ status?.knowledgeDocsCount ?? '--' }}</span>
        </div>
        <div class="kv">
          <span class="k">向量库地址</span>
          <span class="v">{{ status?.embeddingStoreUrl ?? '--' }}</span>
        </div>
        <div class="kv">
          <span class="k">检索探测返回条数</span>
          <span class="v">{{ probeSummary }}</span>
        </div>
        <div v-if="status?.retrieverProbeError" class="banner banner-warn">
          探测失败：{{ status.retrieverProbeError }}
        </div>
        <div class="divider"></div>
        <div class="kv">
          <span class="k">图谱启用</span>
          <span class="v">{{ graphEnabledLabel }}</span>
        </div>
        <div class="kv">
          <span class="k">图谱节点/关系</span>
          <span class="v">{{ graphCountSummary }}</span>
        </div>
        <div class="kv">
          <span class="k">图谱探测返回条数</span>
          <span class="v">{{ graphProbeSummary }}</span>
        </div>
        <div v-if="status?.graphProbeError" class="banner banner-warn">
          图谱探测失败：{{ status.graphProbeError }}
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { knowledgeService } from '../api/knowledge';
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const status = ref(null);
const error = ref(null);
const rebuilding = ref(false);

const isAuthenticated = computed(() => authStore.isAuthenticated);
const tokenPreview = computed(() => {
  const t = authStore.token || '';
  if (!t) return '--';
  return `${t.slice(0, 10)}...${t.slice(-6)}`;
});

const probeSummary = computed(() => {
  const s = status.value || {};
  const returned = s.retrieverProbeReturnedSegments ?? s.retrieverProbeHitCount;
  if (returned === null || returned === undefined) return '--';
  const topK = s.retrieverTopK;
  const minScore = s.retrieverMinScore;
  const meta = [];
  if (topK !== null && topK !== undefined) meta.push(`topK=${topK}`);
  if (minScore !== null && minScore !== undefined) meta.push(`阈值=${minScore}`);
  if (meta.length === 0) return `${returned}`;
  return `${returned}（${meta.join('，')}）`;
});

const graphEnabledLabel = computed(() => {
  const s = status.value || {};
  if (s.graphEnabled === null || s.graphEnabled === undefined) return '--';
  return s.graphEnabled ? '是' : '否';
});

const graphCountSummary = computed(() => {
  const s = status.value || {};
  const nodes = s.graphNodeCount;
  const edges = s.graphEdgeCount;
  if ((nodes === null || nodes === undefined) && (edges === null || edges === undefined)) return '--';
  return `${nodes ?? '--'} / ${edges ?? '--'}`;
});

const graphProbeSummary = computed(() => {
  const s = status.value || {};
  const returned = s.graphProbeReturnedDocs;
  if (returned === null || returned === undefined) return '--';
  return `${returned}`;
});

const loadStatus = async () => {
  const res = await knowledgeService.getStatus();
  status.value = res.data;
};

const rebuild = async () => {
  rebuilding.value = true;
  error.value = null;
  try {
    await knowledgeService.rebuild();
    await loadStatus();
  } catch (e) {
    error.value = '重建失败';
  } finally {
    rebuilding.value = false;
  }
};

onMounted(async () => {
  error.value = null;
  try {
    await loadStatus();
  } catch (e) {
    error.value = '加载失败';
  }
});
</script>

<style scoped>
.page {
  padding: 1.75rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.page-header h2 {
  margin: 0;
  font-size: 1.5rem;
}

.sub {
  margin: 0.25rem 0 0;
  color: var(--text-secondary);
}

.card {
  background: var(--surface-color);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.card-header {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.card-header h3 {
  margin: 0;
  font-size: 1rem;
}

.card-body {
  padding: 1rem 1.25rem;
}

.kv {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
  border-bottom: 1px dashed rgba(15, 23, 42, 0.12);
}

.kv:last-child {
  border-bottom: none;
}

.divider {
  height: 1px;
  background: var(--border-color);
  margin: 0.75rem 0;
  opacity: 0.8;
}

.k {
  color: var(--text-secondary);
  font-weight: 600;
}

.v {
  color: var(--text-primary);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

.banner {
  padding: 0.875rem 1rem;
  border-radius: var(--radius-lg);
  font-size: 0.9375rem;
  font-weight: 500;
}

.banner-error {
  background: #fef2f2;
  color: var(--error-color);
  border: 1px solid #fecaca;
}

.banner-warn {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  margin-top: 0.75rem;
}

.primary-btn {
  border: none;
  padding: 0.75rem 1rem;
  border-radius: var(--radius-md);
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  background: var(--primary-color);
  color: white;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>

