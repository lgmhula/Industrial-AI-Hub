<template>
  <div class="dashboard">
    <!-- Header -->
    <div class="dashboard-header">
      <div>
        <h1>工业控制中心</h1>
        <span class="subtitle">Industrial AI Hub — 实时监控面板</span>
      </div>
      <div class="header-actions">
        <el-tag :type="systemStatus === '正常' ? 'success' : 'danger'" effect="dark">
          {{ systemStatus }}
        </el-tag>
        <span class="update-time">{{ updateTime }}</span>
      </div>
    </div>

    <!-- KPI Cards -->
    <el-row :gutter="20" class="kpi-row">
      <el-col :span="6">
        <div class="kpi-card online">
          <div class="kpi-value">{{ stats.onlineDevices }}</div>
          <div class="kpi-label">在线设备</div>
          <div class="kpi-sub">共 {{ stats.totalDevices }} 台</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi-card warning">
          <div class="kpi-value">{{ stats.pendingAlarms }}</div>
          <div class="kpi-label">待处理告警</div>
          <div class="kpi-sub">共 {{ stats.totalAlarms }} 条</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi-card data">
          <div class="kpi-value">{{ stats.todayDataPoints }}</div>
          <div class="kpi-label">今日数据点</div>
          <div class="kpi-sub">采集频率 1/min</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi-card ops">
          <div class="kpi-value">{{ stats.todayOperations }}</div>
          <div class="kpi-label">今日操作</div>
          <div class="kpi-sub">含登录/CRUD</div>
        </div>
      </el-col>
    </el-row>

    <!-- Charts + Alerts -->
    <el-row :gutter="20" class="content-row">
      <el-col :span="16">
        <div class="panel">
          <h3>设备状态分布</h3>
          <div ref="statusChart" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel alerts-panel">
          <h3>最近告警 <el-button text size="small" @click="$router.push('/alarms')">查看全部 →</el-button></h3>
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

    <!-- Quick Actions -->
    <div class="panel quick-actions">
      <h3>快捷操作</h3>
      <div class="actions-row">
        <el-button type="primary" @click="$router.push('/devices')">设备管理</el-button>
        <el-button type="warning" @click="$router.push('/alarms')">告警中心</el-button>
        <el-button type="info" @click="$router.push('/logs')">操作日志</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { deviceApi, alarmApi, operationLogApi } from '../api/index.js'
import * as echarts from 'echarts'

const systemStatus = ref('正常')
const updateTime = ref('')
const stats = reactive({
  totalDevices: 0, onlineDevices: 0,
  totalAlarms: 0, pendingAlarms: 0,
  todayDataPoints: 0, todayOperations: 0
})
const recentAlarms = ref([])
const statusChart = ref(null)

function formatTime(t) {
  return t ? new Date(t).toLocaleString('zh-CN') : ''
}

async function loadStats() {
  try {
    const devices = await deviceApi.list({ page: 1, size: 1 })
    const alarms = await alarmApi.list({ page: 1, size: 1 })
    const logs = await operationLogApi.list({ page: 1, size: 1 })
    const pending = await alarmApi.listByStatus(0, { page: 1, size: 1 })

    stats.totalDevices = devices.data?.total || 0
    stats.totalAlarms = alarms.data?.total || 0
    stats.pendingAlarms = pending.data?.total || 0
    stats.todayOperations = logs.data?.total || 0

    // Online devices
    if (devices.data?.list) {
      stats.onlineDevices = devices.data.list.filter(d => d.status === 1).length
    }
    stats.todayDataPoints = stats.totalDevices * 60 * 24  // estimate

    // Recent alarms
    const recentRes = await alarmApi.list({ page: 1, size: 5 })
    recentAlarms.value = recentRes.data?.list || []

    updateTime.value = new Date().toLocaleString('zh-CN')
    systemStatus.value = stats.pendingAlarms > 3 ? '告警中' : '正常'
  } catch (e) {
    console.error('Dashboard load failed:', e)
    systemStatus.value = '离线'
  }
}

async function renderChart() {
  await nextTick()
  if (!statusChart.value) return
  const chart = echarts.init(statusChart.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      label: { show: true, formatter: '{b}\n{d}%' },
      data: [
        { value: stats.onlineDevices, name: '在线', itemStyle: { color: '#67C23A' } },
        { value: stats.totalDevices - stats.onlineDevices, name: '离线/维护', itemStyle: { color: '#909399' } },
      ]
    }]
  })
}

onMounted(async () => {
  await loadStats()
  renderChart()
})
</script>

<style scoped>
.dashboard {
  padding: 24px;
  background: #f0f2f5;
  min-height: 100vh;
}
.dashboard-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 24px;
}
.dashboard-header h1 { margin: 0; font-size: 24px; color: #1a1a2e; }
.subtitle { color: #909399; font-size: 14px; margin-left: 8px; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.update-time { color: #909399; font-size: 13px; }

.kpi-row { margin-bottom: 20px; }
.kpi-card {
  background: #fff; border-radius: 8px; padding: 20px;
  border-left: 4px solid #409EFF; transition: transform 0.2s;
}
.kpi-card:hover { transform: translateY(-2px); }
.kpi-card.online { border-left-color: #67C23A; }
.kpi-card.warning { border-left-color: #E6A23C; }
.kpi-card.data { border-left-color: #409EFF; }
.kpi-card.ops { border-left-color: #909399; }
.kpi-value { font-size: 32px; font-weight: 700; color: #303133; }
.kpi-label { font-size: 14px; color: #606266; margin-top: 4px; }
.kpi-sub { font-size: 12px; color: #909399; margin-top: 2px; }

.content-row { margin-bottom: 20px; }
.panel {
  background: #fff; border-radius: 8px; padding: 20px;
}
.panel h3 { margin: 0 0 16px 0; font-size: 16px; color: #303133; display: flex; justify-content: space-between; align-items: center; }
.chart { width: 100%; height: 300px; }

.alerts-panel { max-height: 400px; overflow-y: auto; }
.alert-item {
  padding: 10px 12px; border-radius: 6px; margin-bottom: 8px;
  background: #f8f9fa; border-left: 3px solid #909399;
}
.alert-item.level-1 { border-left-color: #E6A23C; background: #fdf6ec; }
.alert-item.level-2 { border-left-color: #F56C6C; background: #fef0f0; }
.alert-item.level-3 { border-left-color: #D32F2F; background: #fde8e8; }
.alert-type { font-weight: 600; font-size: 13px; color: #303133; }
.alert-msg { font-size: 12px; color: #606266; margin: 2px 0; }
.alert-time { font-size: 11px; color: #909399; }
.empty { color: #909399; padding: 20px; text-align: center; }

.quick-actions { margin-bottom: 20px; }
.actions-row { display: flex; gap: 12px; }
</style>
