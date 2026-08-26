<template>
  <div class="dashboard">
    <!-- 头部 -->
    <div class="dashboard-header">
      <div>
        <h1>工业控制中心</h1>
        <span class="subtitle">实时监控面板 · 最后更新 {{ updateTime || '加载中...' }}</span>
      </div>
      <div class="header-actions">
        <el-tag :type="systemStatus === '正常' ? 'success' : 'danger'" effect="dark">
          {{ systemStatus }}
        </el-tag>
        <el-button :icon="Refresh" size="small" :loading="loading" @click="loadStats">刷新</el-button>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card online">
          <div class="kpi-value">{{ stats.onlineDevices }}<span class="kpi-unit">/ {{ stats.totalDevices }}</span></div>
          <div class="kpi-label">在线设备</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card warning">
          <div class="kpi-value">{{ stats.pendingAlarms }}</div>
          <div class="kpi-label">待处理告警</div>
          <div class="kpi-sub">共 {{ stats.totalAlarms }} 条</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card data">
          <div class="kpi-value">{{ stats.totalDevices }}</div>
          <div class="kpi-label">设备总数</div>
          <div class="kpi-sub">含离线/维护</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card ops">
          <div class="kpi-value">{{ stats.todayOperations }}</div>
          <div class="kpi-label">今日操作</div>
          <div class="kpi-sub">含登录/CRUD</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表 + 告警 -->
    <el-row :gutter="16" class="content-row">
      <el-col :xs="24" :md="16">
        <div class="card">
          <div class="card-header">
            <h3>设备状态分布</h3>
          </div>
          <div ref="statusChart" class="chart"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="card alerts-panel">
          <div class="card-header">
            <h3>最近告警</h3>
            <el-button text size="small" @click="$router.push('/alarms')">查看全部 →</el-button>
          </div>
          <div v-if="recentAlarms.length === 0" class="empty">暂无告警</div>
          <div v-for="alarm in recentAlarms" :key="alarm.id" class="alert-item"
               :class="'level-' + alarm.alarmLevel">
            <div class="alert-type">{{ alarm.alarmType }}</div>
            <div class="alert-msg">{{ alarm.alarmMessage }}</div>
            <div class="alert-time">{{ formatTime(alarm.triggeredAt) }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <div class="card quick-actions">
      <h3>快捷操作</h3>
      <div class="actions-row">
        <el-button type="primary" @click="$router.push('/devices')">设备管理</el-button>
        <el-button type="warning" @click="$router.push('/alarms')">告警中心</el-button>
        <el-button type="info" @click="$router.push('/logs')">操作日志</el-button>
        <el-button v-if="isAdmin" @click="$router.push('/users')">用户管理</el-button>
        <el-button v-if="isAdmin" @click="$router.push('/roles')">角色管理</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { deviceApi, alarmApi, operationLogApi } from '../api/index.js'
import { useAuth } from '../composables/useAuth.js'
import * as echarts from 'echarts'

const { isAdmin } = useAuth()

const systemStatus = ref('加载中')
const updateTime = ref('')
const loading = ref(false)
const stats = reactive({
  totalDevices: 0, onlineDevices: 0,
  totalAlarms: 0, pendingAlarms: 0,
  todayOperations: 0
})
const recentAlarms = ref([])
const statusChart = ref(null)
let chartInstance = null
let refreshTimer = null

function formatTime(t) {
  return t ? new Date(t).toLocaleString('zh-CN') : ''
}

async function loadStats() {
  loading.value = true
  try {
    const [devices, alarms, logs, pending] = await Promise.all([
      deviceApi.list({ page: 1, size: 100 }),
      alarmApi.list({ page: 1, size: 1 }),
      operationLogApi.listRecent().catch(() => ({ data: [] })),
      alarmApi.listByStatus(0, { page: 1, size: 1 }),
    ])

    stats.totalDevices = devices.data?.total || 0
    stats.totalAlarms = alarms.data?.total || 0
    stats.pendingAlarms = pending.data?.total || 0
    const todayStr = new Date().toDateString()
    const recentLogs = Array.isArray(logs.data) ? logs.data : []
    stats.todayOperations = recentLogs.filter(l => l.createdAt && new Date(l.createdAt).toDateString() === todayStr).length

    const deviceList = devices.data?.list || []
    stats.onlineDevices = deviceList.filter(d => d.status === 1).length

    const recentRes = await alarmApi.list({ page: 1, size: 5 })
    recentAlarms.value = recentRes.data?.list || []

    updateTime.value = new Date().toLocaleTimeString('zh-CN')
    systemStatus.value = stats.pendingAlarms > 3 ? '告警中' : '正常'

    await renderChart()
  } catch (e) {
    console.error('Dashboard 加载失败:', e)
    systemStatus.value = '离线'
  } finally {
    loading.value = false
  }
}

async function renderChart() {
  await nextTick()
  if (!statusChart.value) return

  if (chartInstance) {
    chartInstance.dispose()
  }
  chartInstance = echarts.init(statusChart.value)
  chartInstance.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center' },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['50%', '45%'],
      label: { show: true, formatter: '{b}\n{d}%' },
      data: [
        { value: stats.onlineDevices, name: '在线', itemStyle: { color: '#16a34a' } },
        { value: Math.max(0, stats.totalDevices - stats.onlineDevices), name: '离线/维护', itemStyle: { color: '#9ca3af' } },
      ]
    }]
  })
}

const handleResize = () => chartInstance?.resize()

const handleVisibility = () => {
  if (document.hidden) {
    if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null }
  } else {
    if (!refreshTimer) {
      loadStats()
      refreshTimer = setInterval(loadStats, 30000)
    }
  }
}

onMounted(async () => {
  await loadStats()
  window.addEventListener('resize', handleResize)
  document.addEventListener('visibilitychange', handleVisibility)
  refreshTimer = setInterval(loadStats, 30000)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('visibilitychange', handleVisibility)
  if (refreshTimer) clearInterval(refreshTimer)
  if (chartInstance) chartInstance.dispose()
})
</script>

<style scoped>
.dashboard {
  padding: 20px 24px;
  max-width: 1440px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .dashboard { padding: 12px 16px; }
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}
.dashboard-header h1 {
  margin: 0;
  font-size: 22px;
  color: var(--iah-text);
}
.subtitle {
  color: var(--iah-text-muted);
  font-size: 13px;
  margin-left: 8px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kpi-row { margin-bottom: 16px; }
.kpi-unit {
  font-size: 16px;
  font-weight: 500;
  color: var(--iah-text-muted);
  margin-left: 4px;
}

.content-row { margin-bottom: 0; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.card-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--iah-text);
}
.chart {
  width: 100%;
  height: 280px;
}

.alerts-panel { max-height: 380px; overflow-y: auto; }
.alert-type {
  font-weight: 600;
  font-size: 13px;
  color: var(--iah-text);
}
.alert-msg {
  font-size: 12px;
  color: var(--iah-text-secondary);
  margin: 2px 0;
}
.alert-time {
  font-size: 11px;
  color: var(--iah-text-muted);
}
.empty {
  color: var(--iah-text-muted);
  padding: 20px;
  text-align: center;
}

.quick-actions h3 {
  margin: 0 0 14px 0;
  font-size: 16px;
  color: var(--iah-text);
}
.actions-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .chart { height: 220px; }
  .alerts-panel { max-height: 300px; }
}
</style>
