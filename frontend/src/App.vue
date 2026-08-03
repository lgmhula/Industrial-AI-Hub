<template>
  <el-config-provider :locale="zhCn">
    <!-- 登录页：全屏独立布局 -->
    <router-view v-if="isLogin" />

    <!-- 主布局：侧边栏 + 内容区 -->
    <el-container v-else class="layout">
    <el-aside :width="collapse ? '64px' : '220px'" class="aside">
      <div class="logo">
        <el-icon :size="22" color="#3b82f6"><Monitor /></el-icon>
        <span v-show="!collapse" class="logo-text">Industrial AI Hub</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapse"
        router
        class="menu"
        background-color="#1e293b"
        text-color="#94a3b8"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Cpu /></el-icon>
          <template #title>设备管理</template>
        </el-menu-item>
        <el-menu-item index="/alarms">
          <el-icon><Bell /></el-icon>
          <template #title>报警管理</template>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Document /></el-icon>
          <template #title>操作日志</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="20" @click="collapse = !collapse">
            <Fold v-if="!collapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user">
              <el-avatar :size="30" class="avatar">
                {{ username.slice(0, 1).toUpperCase() }}
              </el-avatar>
              <span class="username">{{ username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component, route }">
          <div :key="route.path" class="page-fade">
            <component :is="Component" />
          </div>
        </router-view>
      </el-main>
    </el-container>
    </el-container>
  </el-config-provider>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

const route = useRoute()
const router = useRouter()
const collapse = ref(false)

const isLogin = computed(() => route.path === '/login')
const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))
const username = computed(() => localStorage.getItem('username') || '管理员')

const titleMap = {
  '/dashboard': '仪表盘',
  '/devices': '设备管理',
  '/alarms': '报警管理',
  '/logs': '操作日志',
}
const currentTitle = computed(() => titleMap[activeMenu.value] || '详情')

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确认退出登录？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    }).then(() => {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      router.push('/login')
    }).catch(() => {})
  }
}
</script>

<style scoped>
.layout { height: 100vh; }

.aside {
  background: #1e293b;
  transition: width 0.25s ease;
  overflow: hidden;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.logo-text {
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.3px;
  white-space: nowrap;
}
.menu {
  border-right: none;
}
.menu:not(.el-menu--collapse) {
  width: 220px;
}
:deep(.el-menu-item.is-active) {
  background: #334155 !important;
  border-left: 3px solid #3b82f6;
}
:deep(.el-menu-item) {
  border-left: 3px solid transparent;
}

.header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid var(--iah-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 18px;
}
.collapse-btn {
  cursor: pointer;
  color: var(--iah-text-secondary);
}
.collapse-btn:hover {
  color: var(--iah-primary);
}
.header-right {
  display: flex;
  align-items: center;
}
.user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--iah-text);
}
.avatar {
  background: var(--iah-primary);
  color: #fff;
  font-weight: 600;
}
.username {
  font-size: 14px;
}

.main {
  background: var(--iah-bg);
  overflow-y: auto;
  padding: 0;
}
</style>
