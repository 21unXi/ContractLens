<template>
  <div class="app-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo">CL</div>
        <span class="app-name">ContractLens</span>
      </div>
      <nav class="sidebar-nav">
        <router-link to="/" class="nav-item" active-class="active">
          <span class="icon">📊</span> 控制台
        </router-link>
        <router-link to="/history" class="nav-item" active-class="active">
          <span class="icon">📂</span> 历史记录
        </router-link>
        <router-link to="/knowledge" class="nav-item" active-class="active">
          <span class="icon">📚</span> 知识库
        </router-link>
        <router-link to="/settings" class="nav-item" active-class="active">
          <span class="icon">⚙️</span> 设置
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <button @click="handleLogout" class="logout-btn">
          <span class="icon">🚪</span> 退出登录
        </button>
      </div>
    </aside>

    <main class="main-content">
      <header class="content-header">
        <div class="header-left">
          <h2>控制台概览</h2>
          <p>欢迎回来</p>
        </div>
        <div class="header-right">
          <button class="upload-trigger-btn" @click="triggerFileInput">
            + 上传新合同
          </button>
          <input type="file" ref="fileInput" @change="onFileChange" style="display: none" />
        </div>
      </header>

      <div class="content-body">
        <div class="stats-grid">
          <div class="stat-card">
            <span class="label">累计分析</span>
            <span class="value">{{ contracts.length }}</span>
          </div>
          <div class="stat-card">
            <span class="label">高风险条款</span>
            <span class="value danger">--</span>
          </div>
          <div class="stat-card">
            <span class="label">知识库文档</span>
            <span class="value">{{ knowledgeCount ?? '--' }}</span>
          </div>
        </div>

        <div v-if="uploading" class="upload-overlay">
          <div class="loader"></div>
          <p>正在上传并解析合同内容...</p>
        </div>

        <div v-if="uploadError" class="status-banner status-error">
          {{ uploadError }}
        </div>

        <div class="dashboard-grid" :class="{ collapsed: listCollapsed }">
          <section v-if="!listCollapsed" class="list-section card">
            <div class="section-header">
              <h3>我的合同</h3>
            </div>
            <div class="contract-list">
              <div v-for="contract in contracts" :key="contract.id" 
                   class="contract-item" :class="{ active: selectedContractId === contract.id }"
                   @click="selectContract(contract)">
                <div class="item-info">
                  <span class="title">{{ contract.title }}</span>
                  <span class="date">{{ formatDate(contract.createdAt) }}</span>
                </div>
                <div class="item-actions">
                  <button @click.stop="startAnalysis(contract.id)" 
                          :disabled="isAnalyzing(contract.id)"
                          class="analyze-btn">
                    {{ isAnalyzing(contract.id) ? '分析中...' : '重新分析' }}
                  </button>
                  <button @click.stop="deleteContract(contract.id)" 
                          :disabled="isAnalyzing(contract.id)"
                          class="delete-btn">
                    删除
                  </button>
                </div>
              </div>
              <div v-if="contracts.length === 0" class="empty-state">
                <p>暂无合同数据，点击上方按钮开始上传</p>
              </div>
            </div>
          </section>

          <section class="analysis-section card">
            <div class="section-header">
              <h3>分析报告</h3>
              <div v-if="selectedContractId" class="view-tabs">
                <button :class="{ active: analysisMode === 'chat' }" @click="analysisMode = 'chat'">对话</button>
                <button :class="{ active: analysisMode === 'summary' }" @click="analysisMode = 'summary'">摘要</button>
                <button v-if="analysisMode === 'chat'" @click="toggleList" class="collapse-btn">
                  {{ listCollapsed ? '显示列表' : '隐藏列表' }}
                </button>
                <template v-if="analysisResult && analysisMode === 'summary'">
                  <button :class="{ active: currentView === 'tenant' }" @click="currentView = 'tenant'">租客视角</button>
                  <button :class="{ active: currentView === 'lessor' }" @click="currentView = 'lessor'">房东视角</button>
                </template>
              </div>
            </div>

            <div v-if="selectedContractId" class="analysis-content">
              <div v-if="analysisMode === 'chat'" class="chat-panel">
                <div v-if="chatStreaming" class="chat-status">
                  {{ chatPhaseText }}
                  <span v-if="chatElapsedText"> · {{ chatElapsedText }}</span>
                </div>
                <div v-if="chatError" class="status-banner status-error">
                  {{ chatError.message || '分析失败' }}
                </div>

                <div class="chat-messages">
                  <div v-for="(msg, idx) in chatMessages" :key="idx" class="chat-message" :class="`role-${msg.role}`">
                    <div class="chat-bubble">{{ msg.content }}</div>
                  </div>
                  <div v-if="chatStreaming" class="chat-message role-assistant">
                    <div class="chat-bubble">正在生成…</div>
                  </div>
                </div>

                <div class="chat-actions">
                  <button class="secondary-btn" :disabled="!chatStreaming" @click="stopStreaming">停止</button>
                  <button class="secondary-btn" :disabled="chatStreaming" @click="retryInitial">重试</button>
                </div>

                <form class="chat-input" @submit.prevent="sendFollowUp">
                  <input v-model="followUp" type="text" placeholder="继续追问（发送会中断当前生成），例如：押金条款有哪些具体风险？" />
                  <button type="submit" class="primary-btn" :disabled="!followUp.trim()">发送</button>
                </form>
              </div>

              <div v-else-if="analysisResult" class="fade-in">
                <div v-if="analysisResult?.stale" class="status-banner status-warning">
                  该摘要可能已过期，建议重新分析
                </div>
                <AnalysisSummary :summary="analysisResult" :risks="allRisks" :suggestions="analysisResult.suggestions" />

                <div class="clause-list fade-in">
                  <div class="clause-toolbar">
                    <div class="toolbar-title">
                      <h4 class="view-title">{{ currentView === 'tenant' ? '租客' : '房东' }}视角风险详情</h4>
                      <span v-if="currentRisks.length" class="toolbar-count">{{ filteredRisks.length }}/{{ currentRisks.length }}</span>
                    </div>
                    <div class="toolbar-controls">
                      <div class="filter-row">
                        <span class="filter-label">风险</span>
                        <button class="filter-chip" :class="{ active: riskLevelFilter === 'all' }" @click="riskLevelFilter = 'all'">全部</button>
                        <button class="filter-chip" :class="{ active: riskLevelFilter === '高' }" @click="riskLevelFilter = '高'">高</button>
                        <button class="filter-chip" :class="{ active: riskLevelFilter === '中' }" @click="riskLevelFilter = '中'">中</button>
                        <button class="filter-chip" :class="{ active: riskLevelFilter === '低' }" @click="riskLevelFilter = '低'">低</button>
                      </div>
                      <div class="filter-row">
                        <span class="filter-label">关键词</span>
                        <button
                          v-for="kw in keywordOptions"
                          :key="kw"
                          class="filter-chip"
                          :class="{ active: selectedKeywords.includes(kw) }"
                          @click="toggleKeyword(kw)"
                        >
                          {{ kw }}
                        </button>
                        <button v-if="selectedKeywords.length" class="filter-chip clear" @click="clearKeywords">清空</button>
                      </div>
                    </div>
                  </div>

                  <div v-if="filteredRisks.length > 0">
                    <ClauseCard
                      v-for="(risk, index) in filteredRisks"
                      :key="index"
                      :risk="risk"
                      :highlightKeywords="selectedKeywords"
                    />
                  </div>
                  <div v-else class="empty-analysis">
                    <p>未发现该视角下的显著风险</p>
                  </div>
                </div>
              </div>

              <div v-else class="empty-analysis-state">
                <div class="empty-icon">📝</div>
                <p>请点击开始分析以生成摘要</p>
              </div>
            </div>

            <div v-else class="empty-analysis-state">
              <div class="empty-icon">📝</div>
              <p>请从左侧选择合同并点击分析按钮</p>
            </div>
          </section>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useRouter, useRoute } from 'vue-router';
import { contractService } from '../api/contract';
import { useAnalysisChatStore } from '../stores/analysisChat';
import { knowledgeService } from '../api/knowledge';
import ClauseCard from '../components/ClauseCard.vue';
import AnalysisSummary from '../components/AnalysisSummary.vue';

const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();
const analysisChatStore = useAnalysisChatStore();

const fileInput = ref(null);
const uploading = ref(false);
const uploadError = ref(null);
const knowledgeCount = ref(null);

const contracts = ref([]);
const selectedContractId = ref(null);
const currentView = ref('tenant');
const analysisMode = ref('chat');
const listCollapsed = ref(false);
const followUp = ref('');

const streamControllers = new Map();

const chatSession = computed(() => {
  if (!selectedContractId.value) return null;
  return analysisChatStore.ensureSession(selectedContractId.value);
});

const analysisResult = computed(() => chatSession.value?.analysisResult || null);
const chatMessages = computed(() => chatSession.value?.messages || []);
const chatStatus = computed(() => chatSession.value?.status || null);
const chatStreaming = computed(() => chatSession.value?.streaming || false);
const chatError = computed(() => chatSession.value?.error || null);
const chatElapsedMs = computed(() => chatSession.value?.elapsedMs ?? null);

const formatElapsed = (ms) => {
  const value = Number(ms);
  if (!Number.isFinite(value) || value < 0) return '';
  const totalSeconds = Math.floor(value / 1000);
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
};

const chatPhaseText = computed(() => {
  const message = String(chatStatus.value?.message || '').trim();
  return message || '分析中';
});

const chatElapsedText = computed(() => {
  if (!chatStreaming.value) return '';
  if (chatElapsedMs.value == null) return '';
  return formatElapsed(chatElapsedMs.value);
});

const currentRisks = computed(() => {
  if (!analysisResult.value) return [];
  return currentView.value === 'tenant' 
    ? analysisResult.value.party_tenant_risks || []
    : analysisResult.value.party_lessor_risks || [];
});

const allRisks = computed(() => {
  if (!analysisResult.value) return [];
  const tenant = Array.isArray(analysisResult.value.party_tenant_risks) ? analysisResult.value.party_tenant_risks : [];
  const lessor = Array.isArray(analysisResult.value.party_lessor_risks) ? analysisResult.value.party_lessor_risks : [];
  return [...tenant, ...lessor];
});

const riskLevelFilter = ref('all');
const keywordOptions = ['押金', '违约', '维修', '转租', '退租'];
const selectedKeywords = ref([]);

const toggleKeyword = (kw) => {
  const list = selectedKeywords.value.slice();
  const idx = list.indexOf(kw);
  if (idx >= 0) list.splice(idx, 1);
  else list.push(kw);
  selectedKeywords.value = list;
};

const clearKeywords = () => {
  selectedKeywords.value = [];
};

const riskScoreOf = (level) => {
  if (level === '高') return 3;
  if (level === '中') return 2;
  if (level === '低') return 1;
  return 0;
};

const matchesKeywords = (risk, keywords) => {
  if (!keywords?.length) return true;
  const clause = String(risk?.clause_text || '');
  const desc = String(risk?.risk_description || '');
  const text = `${clause}\n${desc}`;
  return keywords.some((kw) => text.includes(kw));
};

const filteredRisks = computed(() => {
  const list = Array.isArray(currentRisks.value) ? currentRisks.value.slice() : [];
  const keywords = selectedKeywords.value;
  const filtered = list
    .filter((risk) => (riskLevelFilter.value === 'all' ? true : risk?.risk_level === riskLevelFilter.value))
    .filter((risk) => matchesKeywords(risk, keywords));
  filtered.sort((a, b) => riskScoreOf(b?.risk_level) - riskScoreOf(a?.risk_level));
  return filtered;
});

const isAnalyzing = (contractId) => {
  return analysisChatStore.sessions?.[String(contractId)]?.streaming || false;
};

const toggleList = () => {
  listCollapsed.value = !listCollapsed.value;
};

const triggerFileInput = () => {
  fileInput.value.click();
};

const onFileChange = async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
  uploading.value = true;
  uploadError.value = null;
  try {
    const response = await contractService.uploadContract(file);
    await fetchContracts();
    await startAnalysis(response.data.id);
  } catch (err) {
    uploadError.value = '上传或分析失败';
  } finally {
    uploading.value = false;
  }
};

const fetchContracts = async () => {
  try {
    const response = await contractService.getUserContracts();
    contracts.value = response.data;
  } catch (err) {
    uploadError.value = '加载合同失败';
  }
};

const fetchKnowledgeStatus = async () => {
  try {
    const response = await knowledgeService.getStatus();
    knowledgeCount.value = response.data?.knowledgeDocsCount ?? null;
  } catch (err) {
    knowledgeCount.value = null;
  }
};

const selectContract = async (contract) => {
  selectedContractId.value = contract.id;
  await analysisChatStore.loadHistory(contract.id);
  await analysisChatStore.loadAnalysisResult(contract.id);
};

const openContractById = async (contractId) => {
  if (!contractId) return;
  selectedContractId.value = contractId;
  analysisMode.value = 'chat';
  listCollapsed.value = true;
  currentView.value = 'tenant';
  followUp.value = '';
  await analysisChatStore.loadHistory(contractId);
  await analysisChatStore.loadAnalysisResult(contractId);
};

const stopStreaming = () => {
  if (!selectedContractId.value) return;
  const key = String(selectedContractId.value);
  const controller = streamControllers.get(key);
  if (controller) controller.abort();
  streamControllers.delete(key);
  analysisChatStore.setStreaming(selectedContractId.value, false);
};

const startAnalysis = async (contractId) => {
  const key = String(contractId);
  selectedContractId.value = contractId;
  analysisMode.value = 'chat';
  listCollapsed.value = true;
  currentView.value = 'tenant';
  followUp.value = '';

  const existing = streamControllers.get(key);
  if (existing) existing.abort();

  analysisChatStore.resetSession(contractId);
  const controller = new AbortController();
  streamControllers.set(key, controller);
  try {
    await analysisChatStore.startInitial(contractId, { signal: controller.signal });
  } catch (err) {
    if (err?.name !== 'AbortError') {
      analysisChatStore.setError(contractId, { message: err?.message || '分析失败', retryable: true });
    }
  } finally {
    streamControllers.delete(key);
  }
};

const retryInitial = async () => {
  if (!selectedContractId.value) return;
  await startAnalysis(selectedContractId.value);
};

const sendFollowUp = async () => {
  if (!selectedContractId.value) return;
  const message = followUp.value.trim();
  if (!message) return;
  followUp.value = '';

  const contractId = selectedContractId.value;
  const key = String(contractId);
  const existing = streamControllers.get(key);
  if (existing) existing.abort();

  const controller = new AbortController();
  streamControllers.set(key, controller);
  try {
    await analysisChatStore.sendFollowUp(contractId, message, { signal: controller.signal });
  } catch (err) {
    if (err?.name !== 'AbortError') {
      analysisChatStore.setError(contractId, { message: err?.message || '分析失败', retryable: true });
    }
  } finally {
    streamControllers.delete(key);
  }
};

const handleLogout = () => {
  authStore.logout();
  router.push('/login');
};

const deleteContract = async (contractId) => {
  const ok = window.confirm('确认删除该合同？删除后将同时移除对应分析结果。');
  if (!ok) return;

  stopStreaming();
  try {
    await contractService.deleteContract(contractId);
    if (selectedContractId.value === contractId) {
      selectedContractId.value = null;
    }
    await fetchContracts();
  } catch (err) {
    uploadError.value = '删除失败';
  }
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
};

onMounted(() => {
  fetchContracts();
  fetchKnowledgeStatus();

  const initialId = Number(route.query?.contractId);
  if (initialId) {
    openContractById(initialId);
  }
});
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 260px;
  background: var(--surface-color);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 2.5rem;
}

.sidebar-header .logo {
  width: 32px;
  height: 32px;
  background: var(--primary-color);
  color: white;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 0.875rem;
}

.app-name {
  font-weight: 700;
  font-size: 1.125rem;
  color: var(--text-primary);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex-grow: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  text-decoration: none;
  color: var(--text-secondary);
  border-radius: var(--radius-md);
  font-size: 0.9375rem;
  transition: all 0.2s;
}

.nav-item:hover, .nav-item.active {
  background: #f3f4f6;
  color: var(--primary-color);
}

.nav-item.active {
  font-weight: 600;
}

.sidebar-footer {
  padding-top: 1rem;
  border-top: 1px solid var(--border-color);
}

.logout-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: 0.9375rem;
  cursor: pointer;
  border-radius: var(--radius-md);
}

.logout-btn:hover {
  background: #fef2f2;
  color: var(--error-color);
}

.main-content {
  flex-grow: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  background: var(--bg-color);
}

.content-header {
  padding: 1.5rem 2.5rem;
  background: var(--surface-color);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
}

.header-left p {
  margin: 0.25rem 0 0;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.upload-trigger-btn {
  background: var(--primary-color);
  color: white;
  border: none;
  padding: 0.75rem 1.25rem;
  border-radius: var(--radius-md);
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(79, 70, 229, 0.3);
}

.upload-trigger-btn:hover {
  background: var(--primary-hover);
  transform: translateY(-1px);
}

.content-body {
  padding: 2rem 2.5rem;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.5rem;
}

.stat-card {
  background: var(--surface-color);
  padding: 1.25rem;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.stat-card .label {
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.stat-card .value {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-card .value.danger {
  color: var(--error-color);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 350px 1fr;
  gap: 1.5rem;
  align-items: start;
}

.dashboard-grid.collapsed {
  grid-template-columns: 1fr;
}

.card {
  background: var(--surface-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.section-header {
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-header h3 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
}

.contract-list {
  max-height: 600px;
  overflow-y: auto;
}

.contract-item {
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  transition: background 0.2s;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.contract-item:hover {
  background: #f9fafb;
}

.contract-item.active {
  background: #f3f4f6;
  border-left: 4px solid var(--primary-color);
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.item-info .title {
  font-weight: 600;
  font-size: 0.9375rem;
  color: var(--text-primary);
}

.item-info .date {
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.analyze-btn {
  background: #f3f4f6;
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  padding: 0.5rem 0.75rem;
  border-radius: var(--radius-md);
  font-size: 0.8125rem;
  cursor: pointer;
}

.analyze-btn:hover {
  background: white;
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.delete-btn {
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.18);
  color: var(--error-color);
  padding: 0.5rem 0.75rem;
  border-radius: var(--radius-md);
  font-size: 0.8125rem;
  cursor: pointer;
}

.delete-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.14);
}

.delete-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.analysis-content {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.view-tabs {
  display: flex;
  background: #f3f4f6;
  padding: 0.25rem;
  border-radius: var(--radius-md);
}

.view-tabs button {
  padding: 0.4rem 1rem;
  border: none;
  background: transparent;
  font-size: 0.875rem;
  border-radius: 0.4rem;
  cursor: pointer;
  color: var(--text-secondary);
}

.collapse-btn {
  margin-left: auto;
  color: var(--text-primary);
}

.view-tabs button.active {
  background: white;
  color: var(--primary-color);
  box-shadow: var(--shadow-sm);
  font-weight: 600;
}

.view-title {
  margin: 0 0 1rem;
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
}

.clause-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.clause-toolbar {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.toolbar-title {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
}

.toolbar-count {
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.toolbar-controls {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.filter-label {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-secondary);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  margin-right: 0.25rem;
}

.filter-chip {
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.72);
  color: var(--text-secondary);
  font-size: 0.875rem;
  cursor: pointer;
}

.filter-chip.active {
  background: #eef2ff;
  border-color: rgba(79, 70, 229, 0.28);
  color: var(--primary-color);
  font-weight: 600;
}

.filter-chip.clear {
  background: #f3f4f6;
}

.empty-analysis-state {
  padding: 5rem 2rem;
  text-align: center;
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.upload-overlay {
  background: rgba(255, 255, 255, 0.8);
  padding: 2rem;
  text-align: center;
  border-radius: var(--radius-lg);
  margin-bottom: 1.5rem;
  border: 2px dashed var(--primary-color);
}

.status-banner {
  padding: 0.875rem 1rem;
  border-radius: var(--radius-lg);
  font-size: 0.9375rem;
  font-weight: 500;
}

.status-error {
  background: #fef2f2;
  color: var(--error-color);
  border: 1px solid #fecaca;
}

.status-warning {
  background: #fffbeb;
  color: #92400e;
  border: 1px solid #fde68a;
}

.loader {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f4f6;
  border-top: 4px solid var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 1rem;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
  height: 100%;
}

.chat-status {
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.chat-messages {
  flex: 1;
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 1rem;
  background: rgba(255, 255, 255, 0.72);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.chat-message {
  display: flex;
}

.chat-message.role-user {
  justify-content: flex-end;
}

.chat-message.role-assistant {
  justify-content: flex-start;
}

.chat-bubble {
  max-width: 88%;
  padding: 0.75rem 1rem;
  border-radius: 16px;
  border: 1px solid var(--border-color);
  background: var(--surface-color);
  color: var(--text-primary);
  white-space: pre-wrap;
  line-height: 1.6;
}

.chat-message.role-user .chat-bubble {
  background: rgba(79, 70, 229, 0.08);
  border-color: rgba(79, 70, 229, 0.18);
}

.chat-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.chat-input {
  display: flex;
  gap: 0.75rem;
}

.chat-input input {
  flex: 1;
  padding: 0.75rem 1rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: 0.9375rem;
  box-sizing: border-box;
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

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@media (max-width: 1200px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .analysis-section,
  .list-section {
    min-height: auto;
  }
}

@media (max-width: 960px) {
  .app-layout {
    display: block;
    height: auto;
  }

  .sidebar {
    width: auto;
    border-right: none;
    border-bottom: 1px solid var(--border-color);
    padding: 1rem;
  }

  .sidebar-nav {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .sidebar-footer {
    margin-top: 1rem;
  }

  .content-header {
    padding: 1rem 1.25rem;
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }

  .content-body {
    padding: 1.25rem;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .contract-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
  }

  .item-actions {
    width: 100%;
  }

  .analyze-btn,
  .upload-trigger-btn {
    width: 100%;
  }

  .view-tabs {
    width: 100%;
  }

  .view-tabs button {
    flex: 1;
  }
}
</style>
