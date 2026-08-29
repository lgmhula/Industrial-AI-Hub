<template>
  <div class="page dashboard">
    <!-- 头部 -->
    <div class="dashboard-header">
      <div>
        <div class="page-title"><el-icon><Odometer /></el-icon>工业控制中心</div>
        <div class="page-subtitle">实时监控面板 · 最后更新 {{ updateTime || '加载中...' }}</div>
      </div>
      <div class="header-actions">
        <el-tag :type="systemStatus === '正常' ? 'success' : 'danger'" effect="light">
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
          <div class="kpi-label"><span class="status-dot online"></span>在线设备</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card warning">
          <div class="kpi-value">{{ stats.pendingAlarms }}</div>
          <div class="kpi-label"><span class="status-dot maintenance"></span>待处理告警</div>
          <div class="kpi-sub">共 {{ stats.totalAlarms }} 条</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card data">
          <div class="kpi-value">{{ stats.totalDevices }}</div>
          <div class="kpi-label"><span class="status-dot offline"></span>设备总数</div>
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
            <div class="alert-type">{{ alarm.alarmType }}<span v-if="alarm.deviceName" class="alert-source"> · {{ alarm.deviceName }}</span></div>
            <div class="alert-msg">{{ alarm.alarmMessage }}</div>
            <div class="alert-time">{{ relativeTime(alarm.triggeredAt) }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <div class="card quick-actions">
      <div class="card-header"><h3>快捷操作</h3></div>
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
import { init, use } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

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

function relativeTime(t) {
  if (!t) return ''
  const diff = Date.now() - new Date(t).getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return new Date(t).toLocaleDateString('zh-CN')
}

async function loadStats() {
  loading.value = true
  try {
    const [devices, alarms, logs, pending] = await Promise.all([
      deviceApi.list({ page: 1, size: 100 }),
      alarmApi.list({ page: 1, size: 1 }),
      isAdmin.value ? operationLogApi.listRecent().catch(() => ({ data: [] })) : Promise.resolve({ data: [] }),
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
  chartInstance = init(statusChart.value)
  chartInstance.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: '#242831',
      borderColor: '#2a2e3a',
      textStyle: { color: '#e8eaed' },
    },
    legend: {
      bottom: 0,
      left: 'center',
      textStyle: { color: '#9aa0ac' },
      itemWidth: 10,
      itemHeight: 10,
    },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['50%', '45%'],
      label: { show: true, formatter: '{b}\n{d}%', color: '#9aa0ac', fontSize: 11 },
      labelLine: { lineStyle: { color: '#2a2e3a' } },
      data: [
        { value: stats.onlineDevices, name: '在线', itemStyle: { color: '#22c55e' } },
        { value: Math.max(0, stats.totalDevices - stats.onlineDevices), name: '离线/维护', itemStyle: { color: '#6b7280' } },
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
  padding: 16px 20px;
}

@media (max-width: 768px) {
  .dashboard { padding: 12px 14px; }
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kpi-row { margin-bottom: 16px; }
.kpi-unit {
  font-size: 14px;
  font-weight: 500;
  color: var(--iah-text-muted);
  margin-left: 4px;
}

.content-row { margin-bottom: 0; }
.chart {
  width: 100%;
  height: 280px;
}

.alerts-panel { max-height: 380px; overflow-y: auto; }
.empty {
  color: var(--iah-text-muted);
  padding: 20px;
  text-align: center;
}

.alert-source {
  color: var(--iah-text-muted);
  font-weight: 400;
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
