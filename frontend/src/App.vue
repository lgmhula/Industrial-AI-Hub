<template>
  <el-config-provider :locale="zhCn">
    <router-view v-if="isLogin" />
    <el-container v-else class="layout">
      <div v-if="isMobile && !collapse" class="mobile-overlay" @click="collapse = true" />
      <el-aside :width="asideWidth" class="aside" :class="{ 'aside-mobile': isMobile }">
        <div class="logo">
          <el-icon :size="22" color="#3b82f6"><Monitor /></el-icon>
          <span v-show="!collapse" class="logo-text">Industrial AI Hub</span>
        </div>
        <el-menu :default-active="activeMenu" :collapse="collapse" router class="menu"
          background-color="#1e293b" text-color="#94a3b8" active-text-color="#ffffff" @select="onMenuSelect">
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon><template #title>仪表盘</template>
          </el-menu-item>
          <el-menu-item index="/devices">
            <el-icon><Cpu /></el-icon><template #title>设备管理</template>
          </el-menu-item>
          <el-menu-item index="/alarms">
            <el-icon><Bell /></el-icon><template #title>报警管理</template>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/logs">
            <el-icon><Document /></el-icon><template #title>操作日志</template>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/users">
            <el-icon><UserFilled /></el-icon><template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/roles">
            <el-icon><Setting /></el-icon><template #title>角色管理</template>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="header">
          <div class="header-left">
            <el-icon class="collapse-btn" :size="20" @click="toggleCollapse">
              <Fold v-if="!collapse" /><Expand v-else />
            </el-icon>
            <el-breadcrumb separator="/" class="breadcrumb">
              <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <div class="header-right">
            <el-tag size="small" type="info" effect="plain" class="env-tag">{{ envLabel }}</el-tag>
            <el-dropdown @command="handleCommand">
              <span class="user">
                <el-avatar :size="30" class="avatar">{{ username.slice(0, 1).toUpperCase() }}</el-avatar>
                <span class="username">{{ username }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>
        <el-main class="main">
          <router-view v-slot="{ Component, route }">
            <div :key="route.path" class="page-fade"><component :is="Component" /></div>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </el-config-provider>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from './api/index.js'
import { useAuth, clearAuth } from './composables/useAuth.js'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

const route = useRoute()
const router = useRouter()
const collapse = ref(false)
const isMobile = ref(false)
const loggingOut = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) collapse.value = true
}
onMounted(() => { checkMobile(); window.addEventListener('resize', checkMobile) })
onUnmounted(() => window.removeEventListener('resize', checkMobile))

const asideWidth = computed(() => collapse.value ? '64px' : '220px')
const isLogin = computed(() => route.path === '/login')
const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))
const { authState, isAdmin } = useAuth()
const username = computed(() => authState.username || '用户')
const envLabel = computed(() => {
  const host = window.location.hostname
  return (host === 'localhost' || host === '127.0.0.1') ? '开发环境' : '生产环境'
})

const titleMap = {
  '/dashboard': '仪表盘',
  '/devices': '设备管理',
  '/alarms': '报警管理',
  '/logs': '操作日志',
  '/users': '用户管理',
  '/roles': '角色管理',
}
const currentTitle = computed(() => {
  if (route.path.startsWith('/devices/')) return '设备详情'
  return titleMap[activeMenu.value] || '页面'
})

const toggleCollapse = () => { collapse.value = !collapse.value }
const onMenuSelect = () => { if (isMobile.value) collapse.value = true }

const handleCommand = async (cmd) => {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录？', '提示', {
        type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消',
      })
      loggingOut.value = true
      try { await authApi.logout() } catch {}
      clearAuth()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch {
    } finally {
      loggingOut.value = false
    }
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: var(--iah-sidebar-bg); transition: width 0.25s ease; overflow: hidden; }
.aside-mobile { position: fixed; top: 0; left: 0; bottom: 0; z-index: 1001; }
.mobile-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 1000; }
.logo { height: var(--iah-header-h); display: flex; align-items: center; gap: 10px; padding: 0 18px; border-bottom: 1px solid rgba(255,255,255,0.06); }
.logo-text { color: #fff; font-size: 15px; font-weight: 700; white-space: nowrap; }
.menu { border-right: none; }
.menu:not(.el-menu--collapse) { width: 220px; }
:deep(.el-menu-item.is-active) { background: #334155 !important; border-left: 3px solid #3b82f6; }
:deep(.el-menu-item) { border-left: 3px solid transparent; }
.header { height: var(--iah-header-h); background: #fff; border-bottom: 1px solid var(--iah-border); display: flex; align-items: center; justify-content: space-between; padding: 0 20px; }
.header-left { display: flex; align-items: center; gap: 18px; }
.collapse-btn { cursor: pointer; color: var(--iah-text-secondary); }
.collapse-btn:hover { color: var(--iah-primary); }
.header-right { display: flex; align-items: center; gap: 12px; }
.env-tag { white-space: nowrap; }
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; color: var(--iah-text); }
.avatar { background: var(--iah-primary); color: #fff; font-weight: 600; }
.username { font-size: 14px; }
@media (max-width: 768px) { .breadcrumb, .env-tag, .username { display: none; } }
.main { background: var(--iah-bg); overflow-y: auto; padding: 0; }
</style>
