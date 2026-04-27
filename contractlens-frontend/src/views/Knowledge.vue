<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h2>知识库</h2>
        <p class="sub">查看已收录的知识库文档，并确认向量检索是否可用</p>
      </div>
      <button class="primary-btn" :disabled="rebuilding" @click="rebuild">
        {{ rebuilding ? '重建中...' : rebuildLabel }}
      </button>
    </header>

    <div v-if="error" class="banner banner-error">{{ error }}</div>

    <section class="card">
      <div class="card-header">
        <h3>状态</h3>
      </div>
      <div class="card-body">
        <div class="kv">
          <span class="k">RAG 模式</span>
          <span class="v">{{ ragModeLabel }}</span>
        </div>
        <div class="kv">
          <span class="k">文档数量</span>
          <span class="v">{{ status?.knowledgeDocsCount ?? '--' }}</span>
        </div>
        <template v-if="isLightRag">
          <div class="kv">
            <span class="k">LightRAG 服务</span>
            <span class="v">{{ status?.lightRagBaseUrl ?? '--' }}</span>
          </div>
          <div class="kv">
            <span class="k">LightRAG 检索模式</span>
            <span class="v">{{ status?.lightRagQueryMode ?? '--' }}</span>
          </div>
          <div class="kv">
            <span class="k">探测返回 chunks</span>
            <span class="v">{{ lightRagProbeSummary }}</span>
          </div>
          <div class="kv">
            <span class="k">探测上下文长度</span>
            <span class="v">{{ status?.lightRagProbeContextChars ?? '--' }}</span>
          </div>
          <div class="kv">
            <span class="k">探测延迟(ms)</span>
            <span class="v">{{ status?.lightRagProbeLatencyMs ?? '--' }}</span>
          </div>
          <div v-if="status?.lightRagProbeError" class="banner banner-warn">
            探测失败：{{ status.lightRagProbeError }}
          </div>
        </template>
        <template v-else>
          <div class="kv">
            <span class="k">向量库地址</span>
            <span class="v">{{ status?.embeddingStoreUrl ?? '--' }}</span>
          </div>
          <div class="kv">
            <span class="k">Collection</span>
            <span class="v">{{ status?.embeddingStoreCollection ?? '--' }}</span>
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
        </template>
      </div>
    </section>

    <section class="card">
      <div class="card-header">
        <h3>文档列表</h3>
        <div class="pager">
          <button class="secondary-btn" :disabled="page === 0 || loading" @click="page--">上一页</button>
          <span class="page-no">第 {{ page + 1 }} 页</span>
          <button class="secondary-btn" :disabled="loading || isLastPage" @click="page++">下一页</button>
        </div>
      </div>
      <div class="card-body">
        <div v-if="loading" class="muted">加载中...</div>
        <div v-else-if="docs.length === 0" class="muted">暂无知识库文档</div>
        <div v-else class="doc-list">
          <div v-for="doc in docs" :key="doc.docId" class="doc-item">
            <div class="doc-main">
              <div class="doc-title">{{ doc.title }}</div>
              <div class="doc-meta">
                <span class="pill">{{ doc.docType }}</span>
                <span class="muted">{{ formatDate(doc.createdAt) }}</span>
                <span class="muted">#{{ doc.docId }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { knowledgeService } from '../api/knowledge';

const status = ref(null);
const docs = ref([]);
const page = ref(0);
const size = ref(20);
const loading = ref(false);
const rebuilding = ref(false);
const error = ref(null);
const isLastPage = computed(() => docs.value.length < size.value);
const isLightRag = computed(() => (status.value?.ragMode || '') === 'lightrag');
const ragModeLabel = computed(() => {
  const mode = status.value?.ragMode;
  if (!mode) return '--';
  if (mode === 'lightrag') return 'LightRAG';
  if (mode === 'legacy') return 'Legacy（Chroma/Neo4j）';
  return mode;
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

const rebuildLabel = computed(() => (isLightRag.value ? '同步到 LightRAG' : '重建向量库'));

const lightRagProbeSummary = computed(() => {
  const s = status.value || {};
  const returned = s.lightRagProbeReturnedChunks;
  if (returned === null || returned === undefined) return '--';
  return `${returned}`;
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

const loadDocs = async () => {
  loading.value = true;
  try {
    const res = await knowledgeService.listDocs({ page: page.value, size: size.value });
    docs.value = res.data?.content || [];
  } finally {
    loading.value = false;
  }
};

const rebuild = async () => {
  rebuilding.value = true;
  error.value = null;
  try {
    await knowledgeService.rebuild();
    await loadStatus();
    await loadDocs();
  } catch (e) {
    error.value = '重建失败';
  } finally {
    rebuilding.value = false;
  }
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

watch(page, async () => {
  error.value = null;
  try {
    await loadDocs();
  } catch (e) {
    error.value = '加载文档失败';
  }
});

onMounted(async () => {
  error.value = null;
  try {
    await loadStatus();
    await loadDocs();
  } catch (e) {
    error.value = '加载知识库状态失败';
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

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
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

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.doc-item {
  padding: 0.875rem 1rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.doc-title {
  font-weight: 700;
}

.doc-meta {
  margin-top: 0.25rem;
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
}

.pill {
  display: inline-flex;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.75rem;
  background: rgba(79, 70, 229, 0.08);
  color: var(--primary-color);
  border: 1px solid rgba(79, 70, 229, 0.18);
  font-weight: 700;
}

.muted {
  color: var(--text-secondary);
}

.pager {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.page-no {
  color: var(--text-secondary);
  font-weight: 600;
}

.primary-btn,
.secondary-btn {
  border: none;
  padding: 0.75rem 1rem;
  border-radius: var(--radius-md);
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
}

.primary-btn {
  background: var(--primary-color);
  color: white;
}

.secondary-btn {
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-primary);
}

.primary-btn:disabled,
.secondary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>

