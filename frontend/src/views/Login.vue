<template>
  <div class="login-container">
    <div class="login-card">
      <h2>Industrial AI Hub</h2>
      <p class="subtitle">工业设备管理平台</p>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="form.username" type="text" placeholder="请输入用户名" required />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="请输入密码" required />
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api/index.js'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await authApi.login({ username: form.username, password: form.password })
    localStorage.setItem('token', res.data)
    router.push('/devices')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}
.login-card {
  background: #fff;
  border-radius: 8px;
  padding: 40px;
  width: 360px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.3);
}
.login-card h2 { text-align: center; margin-bottom: 4px; color: #1a1a2e; }
.subtitle { text-align: center; color: #666; font-size: 14px; margin-bottom: 28px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 14px; color: #333; }
.form-group input {
  width: 100%; padding: 10px 12px; border: 1px solid #ddd; border-radius: 4px;
  font-size: 14px; box-sizing: border-box;
}
.form-group input:focus { border-color: #0f3460; outline: none; }
.error { color: #e74c3c; font-size: 13px; margin-bottom: 8px; }
button {
  width: 100%; padding: 12px; background: #0f3460; color: #fff;
  border: none; border-radius: 4px; font-size: 15px; cursor: pointer; margin-top: 8px;
}
button:hover { background: #1a5276; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
