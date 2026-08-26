<template>
  <div class="register-page">
    <div class="bg-animation">
      <div v-for="i in 20" :key="i" class="particle" :style="particleStyle(i)"></div>
    </div>
    <div class="register-card">
      <h1>用户注册</h1>
      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleRegister" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（至少 3 位）" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（至少 6 位）" :prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" :prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-form-item prop="inviteCode">
          <el-input v-model="form.inviteCode" placeholder="邀请码" size="large" />
        </el-form-item>
        <div v-if="error" class="error-msg">{{ error }}</div>
        <el-button type="primary" size="large" :loading="loading" @click="handleRegister" class="register-btn">
          {{ loading ? '注册中...' : '注 册' }}
        </el-button>
        <div class="footer-links">
          <router-link to="/login">已有账号？返回登录</router-link>
        </div>
      </el-form>
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
const formRef = ref(null)
const form = reactive({ username: '', password: '', confirmPassword: '', inviteCode: '' })

const rules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 3, message: '用户名至少 3 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  inviteCode: [{ required: true, message: '请输入邀请码', trigger: 'blur' }],
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true; error.value = ''
  try {
    await authApi.register({
      username: form.username,
      password: form.password,
      inviteCode: form.inviteCode,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    error.value = e.message || '注册失败'
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
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e3a5f 50%, #0f172a 100%);
  position: relative;
  overflow: hidden;
}
.bg-animation { position: absolute; inset: 0; pointer-events: none; }
.particle { position: absolute; border-radius: 50%; background: rgba(59, 130, 246, 0.15); animation: floatUp 8s infinite ease-in-out; }
@keyframes floatUp { 0%,100% { transform: translateY(0); opacity: 0.3; } 50% { transform: translateY(-30px); opacity: 0.8; } }
.register-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 40px;
  width: 400px;
  max-width: 90vw;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  z-index: 1;
}
.register-card h1 { text-align: center; color: var(--iah-text); font-size: 24px; margin-bottom: 28px; }
.form-group {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f1f5f9;
  border-radius: 8px;
  padding: 0 14px;
  margin-bottom: 16px;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}
.form-group:focus-within { border-color: var(--iah-primary); }
.form-group .el-icon { color: var(--iah-text-muted); }
.form-group input {
  flex: 1;
  border: none;
  background: transparent;
  padding: 12px 0;
  font-size: 14px;
  outline: none;
  color: var(--iah-text);
}
.error-msg { color: var(--iah-danger); font-size: 13px; margin-bottom: 12px; text-align: center; }
.register-btn { width: 100%; margin-bottom: 16px; }
.footer-links { text-align: center; }
.footer-links a { color: var(--iah-primary); font-size: 13px; text-decoration: none; }
.footer-links a:hover { text-decoration: underline; }
</style>
