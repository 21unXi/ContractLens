<template>
  <div class="auth-page">
    <div class="auth-card fade-in">
      <div class="auth-header">
        <div class="logo">CL</div>
        <h1>创建账号</h1>
        <p>加入 ContractLens，开启智能合同审计</p>
      </div>
      <form @submit.prevent="handleRegister" class="auth-form">
        <div class="form-group">
          <label for="username">用户名</label>
          <div class="input-wrapper">
            <input type="text" v-model="username" id="username" placeholder="请输入用户名" required />
          </div>
        </div>
        <div class="form-group">
          <label for="email">邮箱地址</label>
          <div class="input-wrapper">
            <input type="email" v-model="email" id="email" placeholder="name@example.com" required />
          </div>
        </div>
        <div class="form-group">
          <label for="password">密码</label>
          <div class="input-wrapper">
            <input type="password" v-model="password" id="password" placeholder="至少6位字符" required />
          </div>
        </div>
        <button type="submit" :disabled="loading" class="primary-btn">
          {{ loading ? '正在注册...' : '立即注册' }}
        </button>
        <div v-if="error" class="error-msg">{{ error }}</div>
        <div v-if="success" class="success-msg">{{ success }}</div>
        <div class="auth-footer">
          已有账号？ <router-link to="/login" class="link">立即登录</router-link>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useRouter } from 'vue-router';

const username = ref('');
const email = ref('');
const password = ref('');
const error = ref(null);
const success = ref(null);
const loading = ref(false);
const authStore = useAuthStore();
const router = useRouter();

const handleRegister = async () => {
  loading.value = true;
  error.value = null;
  success.value = null;
  try {
    await authStore.register({ username: username.value, email: email.value, password: password.value });
    success.value = '注册成功！正在跳转到登录页面...';
    setTimeout(() => router.push('/login'), 2000);
  } catch (err) {
    error.value = '注册失败，用户名或邮箱可能已被占用';
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 1.5rem;
}

.auth-card {
  width: 100%;
  max-width: 440px;
  background: var(--surface-color);
  padding: 3rem;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.auth-header {
  text-align: center;
  margin-bottom: 2.5rem;
}

.logo {
  width: 48px;
  height: 48px;
  background: var(--primary-color);
  color: white;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 1.25rem;
  margin: 0 auto 1.5rem;
}

h1 {
  font-size: 1.875rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 0.5rem;
}

p {
  color: var(--text-secondary);
  font-size: 0.9375rem;
  margin: 0;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

label {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
}

input {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: 0.9375rem;
  box-sizing: border-box;
}

.primary-btn {
  background: var(--primary-color);
  color: white;
  border: none;
  padding: 0.875rem;
  border-radius: var(--radius-md);
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  margin-top: 0.5rem;
}

.primary-btn:hover:not(:disabled) {
  background: var(--primary-hover);
}

.primary-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.error-msg {
  color: var(--error-color);
  font-size: 0.875rem;
  text-align: center;
  padding: 0.75rem;
  background: #fef2f2;
  border-radius: var(--radius-md);
}

.success-msg {
  color: var(--success-color);
  font-size: 0.875rem;
  text-align: center;
  padding: 0.75rem;
  background: #ecfdf5;
  border-radius: var(--radius-md);
}

.auth-footer {
  text-align: center;
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.link {
  color: var(--primary-color);
  text-decoration: none;
  font-weight: 600;
}

.link:hover {
  text-decoration: underline;
}
</style>
