<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h2>历史记录</h2>
        <p class="sub">查看已上传的合同并快速进入对话式分析</p>
      </div>
      <router-link to="/" class="primary-link">回到控制台</router-link>
    </header>

    <div v-if="error" class="banner banner-error">{{ error }}</div>

    <section class="card">
      <div class="card-header">
        <h3>我的合同</h3>
      </div>
      <div class="card-body">
        <div v-if="loading" class="muted">加载中...</div>
        <div v-else-if="contracts.length === 0" class="muted">暂无合同数据</div>
        <div v-else class="list">
          <div v-for="c in contracts" :key="c.id" class="row">
            <div class="meta">
              <div class="title">{{ c.title }}</div>
              <div class="sub muted">{{ formatDate(c.createdAt) }}</div>
            </div>
            <div class="actions">
              <router-link :to="{ path: '/', query: { contractId: c.id } }" class="secondary-link">进入对话</router-link>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { contractService } from '../api/contract';

const contracts = ref([]);
const loading = ref(false);
const error = ref(null);

const load = async () => {
  loading.value = true;
  error.value = null;
  try {
    const res = await contractService.getUserContracts();
    contracts.value = res.data || [];
  } catch (e) {
    error.value = '加载失败';
  } finally {
    loading.value = false;
  }
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

onMounted(load);
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
}

.card-header h3 {
  margin: 0;
  font-size: 1rem;
}

.card-body {
  padding: 1rem 1.25rem;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.875rem 1rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.title {
  font-weight: 700;
}

.actions {
  display: flex;
  align-items: center;
}

.muted {
  color: var(--text-secondary);
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

.primary-link,
.secondary-link {
  text-decoration: none;
  padding: 0.75rem 1rem;
  border-radius: var(--radius-md);
  font-weight: 700;
  display: inline-flex;
  align-items: center;
}

.primary-link {
  background: var(--primary-color);
  color: white;
}

.secondary-link {
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-primary);
}
</style>

