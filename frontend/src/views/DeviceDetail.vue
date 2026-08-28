<template>
  <div class="page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-title">
        <el-button :icon="ArrowLeft" circle @click="$router.push('/devices')" />
        <div>
          <div class="page-title">
            {{ device.deviceName || '设备详情' }}
            <el-tag v-if="device.id" size="small" :type="statusType(device.status)" effect="light">
              {{ statusLabel(device.status) }}
            </el-tag>
          </div>
          <div class="page-subtitle"><el-tag size="small" effect="plain">{{ device.deviceCode }}</el-tag></div>
        </div>
      </div>
    </div>

    <template v-if="!loading && device.id">
      <!-- 基本信息 -->
      <div class="card">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="设备类型">{{ device.deviceType }}</el-descriptions-item>
          <el-descriptions-item label="IP 地址">{{ device.ipAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="端口">{{ device.port || '-' }}</el-descriptions-item>
          <el-descriptions-item label="安装位置">{{ device.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间" :span="2">{{ fmtTime(device.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 统计卡片 -->
      <el-row :gutter="16" class="stats-row" v-if="stats">
        <el-col :xs="12" :md="6">
          <div class="card stat-card">
            <el-statistic title="数据条数" :value="stats.count ?? 0" />
          </div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="card stat-card">
            <el-statistic title="平均值" :value="primaryStats?.avg ?? 0" :precision="2" />
          </div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="card stat-card">
            <el-statistic title="最小值" :value="primaryStats?.min ?? 0" :precision="2" />
          </div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="card stat-card">
            <el-statistic title="最大值" :value="primaryStats?.max ?? 0" :precision="2" />
          </div>
        </el-col>
      </el-row>

      <!-- AI 健康诊断 -->
      <div class="card ai-card">
        <div class="ai-card-header">
          <div>
            <h3 class="section-title">AI 健康诊断</h3>
            <div class="ai-card-subtitle">基于设备数据与最近告警生成评估</div>
          </div>
          <el-button type="primary" :icon="MagicStick" :loading="aiLoading" @click="runAiDiagnosis">
            生成诊断
          </el-button>
        </div>
        <el-alert v-if="aiError" :title="aiError" type="error" show-icon :closable="false" />
        <template v-else-if="aiDiagnosis">
          <div class="ai-diagnosis-head">
            <el-tag :type="healthType(aiDiagnosis.healthLevel)" effect="light" size="large">
              {{ aiDiagnosis.healthLevel || '未知' }}
            </el-tag>
          </div>
          <p class="ai-summary">{{ aiDiagnosis.summary || '-' }}</p>
          <div v-if="aiDiagnosis.issues?.length" class="ai-section">
            <h4>发现的问题</h4>
            <ul>
              <li v-for="(item, i) in aiDiagnosis.issues" :key="i">{{ item }}</li>
            </ul>
          </div>
          <div v-if="aiDiagnosis.suggestedActions?.length" class="ai-section">
            <h4>建议动作</h4>
            <ul>
              <li v-for="(item, i) in aiDiagnosis.suggestedActions" :key="i">{{ item }}</li>
            </ul>
          </div>
        </template>
        <div v-else class="ai-placeholder">点击「生成诊断」，让 AI 基于设备基础信息、最近采集数据和未处理告警给出健康评估。</div>
      </div>

      <!-- 趋势图 -->
      <el-row :gutter="16">
        <el-col v-for="chart in charts" :key="chart.type" :xs="24" :md="12">
          <div class="card chart-card">
            <h3 class="section-title">{{ chart.title }}</h3>
            <v-chart v-if="chart.option" :option="chart.option" autoresize style="height: 300px" />
            <EmptyState v-else :icon="chart.icon" :title="`暂无${chart.label}数据`" />
          </div>
        </el-col>
      </el-row>

      <!-- 最近采集数据 -->
      <div class="card" v-if="recentData.length">
        <h3 class="section-title">最近采集数据</h3>
        <el-table :data="recentData" stripe size="default">
          <el-table-column prop="dataType" label="数据类型" width="140">
            <template #default="{ row }"><el-tag size="small" type="info">{{ row.dataType }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="dataValue" label="数值" width="120" />
          <el-table-column prop="unit" label="单位" width="100">
            <template #default="{ row }">{{ row.unit || '-' }}</template>
          </el-table-column>
          <el-table-column label="采集时间">
            <template #default="{ row }">{{ fmtTime(row.recordedAt) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <EmptyState v-if="!loading && !device.id" icon="🚫" title="设备不存在或已被删除" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, MagicStick } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { deviceApi, deviceDataApi, aiApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const route = useRoute()
const deviceId = Number(route.params.id)
const device = ref({})
const stats = ref(null)
const allData = ref([])
const recentData = ref([])
const loading = ref(false)
const aiLoading = ref(false)
const aiError = ref('')
const aiDiagnosis = ref(null)

const CHART_CONFIGS = [
  { type: 'TEMPERATURE', label: '温度', unit: '°C', color: '#3b82f6', icon: '🌡️' },
  { type: 'PRESSURE', label: '压力', unit: 'kPa', color: '#ef4444', icon: '📈' },
  { type: 'HUMIDITY', label: '湿度', unit: '%', color: '#22c55e', icon: '💧' },
  { type: 'SPEED', label: '转速', unit: 'RPM', color: '#f59e0b', icon: '⚙️' },
]

const charts = computed(() => {
  const types = [...new Set(allData.value.map(d => d.dataType))]
  const configs = types.length > 0
    ? CHART_CONFIGS.filter(c => types.includes(c.type))
    : CHART_CONFIGS.slice(0, 2)
  return configs.map(cfg => ({
    type: cfg.type,
    title: `${cfg.label}趋势 (${cfg.unit})`,
    label: cfg.label,
    icon: cfg.icon,
    option: buildChartOption(allData.value, cfg.type, cfg.color),
  }))
})

const buildChartOption = (data, type, color) => {
  const filtered = data.filter(d => d.dataType === type).sort((a, b) => new Date(a.recordedAt) - new Date(b.recordedAt))
  if (!filtered.length) return null
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: filtered.map(d => d.recordedAt?.slice(11, 16)), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
    series: [{ data: filtered.map(d => d.dataValue), type: 'line', smooth: true, lineStyle: { color, width: 2 }, itemStyle: { color }, areaStyle: { color: `${color}15` } }],
  }
}

const fetchDetail = async () => {
  loading.value = true
  try {
    const [dev, dataRes] = await Promise.all([
      deviceApi.getById(deviceId),
      deviceDataApi.list(deviceId).catch(() => ({ data: [] })),
    ])
    device.value = dev.data || dev
    const records = Array.isArray(dataRes?.data)
      ? dataRes.data
      : (dataRes?.data?.list || [])
    allData.value = records
    recentData.value = records.slice(-10).reverse()
    stats.value = computeStats(records)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const computeStats = (data) => {
  if (!data.length) return null
  const byType = {}
  for (const d of data) {
    const t = d.dataType || 'UNKNOWN'
    if (!byType[t]) byType[t] = []
    byType[t].push(Number(d.dataValue))
  }
  const result = {}
  for (const [type, vals] of Object.entries(byType)) {
    const valid = vals.filter(v => !Number.isNaN(v))
    if (valid.length) {
      result[type] = {
        count: valid.length,
        avg: valid.reduce((a, b) => a + b, 0) / valid.length,
        min: Math.min(...valid),
        max: Math.max(...valid),
      }
    }
  }
  return result
}

const primaryStats = computed(() => {
  if (!stats.value) return null
  const tempKey = Object.keys(stats.value).find(k => k.includes('TEMP'))
  return stats.value[tempKey] || Object.values(stats.value)[0] || null
})

const runAiDiagnosis = async () => {
  aiLoading.value = true
  aiError.value = ''
  aiDiagnosis.value = null
  try {
    const res = await aiApi.deviceDiagnosis(deviceId)
    aiDiagnosis.value = res.data || res
  } catch (e) {
    aiError.value = e.message || 'AI 健康诊断失败'
  } finally {
    aiLoading.value = false
  }
}

const healthType = (level) => ({ 健康: 'success', 关注: 'warning', 异常: 'danger' }[level] || 'info')
const statusType = (s) => ({ 1: 'success', 0: 'info', 2: 'warning' }[s] || 'info')
const statusLabel = (s) => ({ 1: '在线', 0: '离线', 2: '维护中' }[s] || '未知')
const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchDetail)
</script>

<style scoped>
.header-title {
  display: flex;
  align-items: center;
  gap: 14px;
}
.card { margin-bottom: 16px; }
.stats-row { margin-bottom: 0; }
.stat-card { margin-bottom: 16px; }
.chart-card { margin-bottom: 16px; }
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--iah-text);
  margin-bottom: 14px;
}
.ai-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.ai-card-subtitle {
  font-size: 13px;
  color: var(--iah-text-muted);
  margin-top: -8px;
}
.ai-placeholder {
  padding: 18px 0 6px;
  font-size: 13px;
  color: var(--iah-text-muted);
}
.ai-diagnosis-head {
  margin-bottom: 12px;
}
.ai-summary {
  font-size: 14px;
  line-height: 1.8;
  color: var(--iah-text);
}
.ai-section {
  margin-top: 14px;
}
.ai-section h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--iah-text);
  margin-bottom: 8px;
}
.ai-section ul {
  margin: 0;
  padding-left: 20px;
  color: var(--iah-text-secondary);
  line-height: 1.9;
}
</style>
