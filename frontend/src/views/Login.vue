<template>
  <div class="login-wrapper">
    <div class="login-bg">
    </div>
    <div class="login-card">
      <div class="logo-section">
        <div class="logo-icon">
          <svg viewBox="0 0 48 48" width="48" height="48">
            <rect x="4" y="20" width="8" height="24" rx="2" fill="#3b82f6"/>
            <rect x="16" y="12" width="8" height="32" rx="2" fill="#22c55e"/>
            <rect x="28" y="16" width="8" height="28" rx="2" fill="#f59e0b"/>
            <rect x="40" y="8" width="4" height="36" rx="2" fill="#ef4444"/>
          </svg>
        </div>
        <h2>Industrial AI Hub</h2>
        <p class="subtitle">工业设备智能管理平台</p>
      </div>
      <form @submit.prevent="handleLogin">
        <div class="input-group">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" size="large" />
        </div>
        <div class="input-group">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock"
                    size="large" show-password @keyup.enter="handleLogin" />
        </div>
        <p v-if="error" class="error-msg">{{ error }}</p>
        <el-button type="primary" size="large" :loading="loading" @click="handleLogin" class="login-btn">
          {{ loading ? '验证中...' : '登 录' }}
        </el-button>
      </form>
      <div class="footer-text">Industrial AI Hub · 工业设备智能管理平台</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api/index.js'
import { setAuth } from '../composables/useAuth.js'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) { error.value = '请输入用户名和密码'; return }
  loading.value = true; error.value = ''
  try {
    const res = await authApi.login({ username: form.username, password: form.password })
    const token = res.data
    let roles = []
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      roles = payload.roles || []
    } catch { /* roles stays [] */ }
    setAuth(token, form.username, roles)
    router.push('/dashboard')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally { loading.value = false }
}

</script>

<style scoped>
.login-wrapper { position: relative; min-height: 100vh; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.login-bg {
  position: absolute; inset: 0;
  background-color: #0f1117;
  background-image:
    linear-gradient(rgba(59, 130, 246, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.05) 1px, transparent 1px);
  background-size: 32px 32px;
}
.login-card {
  position: relative; z-index: 2;
  background: rgba(26, 29, 38, 0.94);
  border: 1px solid var(--iah-border);
  backdrop-filter: blur(16px);
  border-radius: 8px; padding: 40px 36px; width: 400px; max-width: 90vw;
  box-shadow: 0 20px 60px rgba(0,0,0,0.4);
}
.logo-section { text-align: center; margin-bottom: 32px; }
.logo-icon { margin-bottom: 12px; }
.logo-section h2 { margin: 0; font-size: 20px; color: var(--iah-text); letter-spacing: 1px; }
.subtitle { color: var(--iah-text-secondary); font-size: 13px; margin: 4px 0 0; }
.input-group { margin-bottom: 16px; }
.error-msg { color: var(--iah-danger); font-size: 13px; margin: 0 0 8px; text-align: center; }
.login-btn { width: 100%; margin-top: 8px; }
.register-link { text-align: center; margin-top: 16px; }
.register-link a { color: var(--iah-primary-light); font-size: 13px; text-decoration: none; }
.register-link a:hover { text-decoration: underline; }
.footer-text { text-align: center; color: var(--iah-text-muted); font-size: 12px; margin-top: 24px; }
</style>
