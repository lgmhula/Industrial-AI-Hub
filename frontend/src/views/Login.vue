<template>
  <div class="login-wrapper">
    <div class="login-bg">
      <div class="bg-overlay"></div>
      <div class="bg-particles">
        <div v-for="i in 20" :key="i" class="particle" :style="particleStyle(i)"></div>
      </div>
    </div>
    <div class="login-card">
      <div class="logo-section">
        <div class="logo-icon">
          <svg viewBox="0 0 48 48" width="48" height="48">
            <rect x="4" y="20" width="8" height="24" rx="2" fill="#409EFF"/>
            <rect x="16" y="12" width="8" height="32" rx="2" fill="#67C23A"/>
            <rect x="28" y="16" width="8" height="28" rx="2" fill="#E6A23C"/>
            <rect x="40" y="8" width="4" height="36" rx="2" fill="#F56C6C"/>
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
      <div class="footer-text">Industrial AI Hub v2.0 · Day 042</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api/index.js'
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
    localStorage.setItem('token', res.data)
    localStorage.setItem('username', form.username)
    router.push('/dashboard')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally { loading.value = false }
}

function particleStyle(i) {
  return {
    left: Math.random() * 100 + '%',
    top: Math.random() * 100 + '%',
    animationDelay: Math.random() * 5 + 's',
    animationDuration: (3 + Math.random() * 4) + 's',
    width: (2 + Math.random() * 4) + 'px',
    height: (2 + Math.random() * 4) + 'px',
  }
}
</script>

<style scoped>
.login-wrapper { position: relative; min-height: 100vh; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.login-bg {
  position: absolute; inset: 0;
  background: linear-gradient(135deg, #0a1628 0%, #132347 30%, #1a3a5c 60%, #0d2137 100%);
}
.bg-overlay { position: absolute; inset: 0; background: radial-gradient(ellipse at 30% 50%, rgba(64,158,255,0.08) 0%, transparent 70%); }
.particle { position: absolute; background: rgba(64,158,255,0.3); border-radius: 50%; animation: float linear infinite; }
@keyframes float { 0%, 100% { transform: translateY(0); opacity: 0.3; } 50% { transform: translateY(-20px); opacity: 0.8; } }

.login-card {
  position: relative; z-index: 2;
  background: rgba(255,255,255,0.95); backdrop-filter: blur(16px);
  border-radius: 12px; padding: 48px 40px; width: 400px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.logo-section { text-align: center; margin-bottom: 32px; }
.logo-icon { margin-bottom: 12px; }
.logo-section h2 { margin: 0; font-size: 22px; color: #1a1a2e; letter-spacing: 1px; }
.subtitle { color: #909399; font-size: 14px; margin: 4px 0 0; }
.input-group { margin-bottom: 16px; }
.error-msg { color: #F56C6C; font-size: 13px; margin: 0 0 8px; text-align: center; }
.login-btn { width: 100%; margin-top: 8px; }
.footer-text { text-align: center; color: #c0c4cc; font-size: 12px; margin-top: 24px; }
</style>
