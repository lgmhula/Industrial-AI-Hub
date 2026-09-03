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
          <div class="ai-card-title">
            <el-icon :size="18"><MagicStick /></el-icon>
            <div>
              <h3>AI 健康诊断</h3>
              <div class="ai-card-subtitle">基于设备数据与最近告警生成评估</div>
            </div>
          </div>
          <div class="ai-card-action">
            <el-button v-if="aiDiagnosis" size="small" :icon="RefreshRight" plain
                       :loading="aiLoading" @click="runAiDiagnosis">
              重新生成
            </el-button>
            <el-button type="primary" :icon="MagicStick" :loading="aiLoading" @click="runAiDiagnosis">
              {{ aiDiagnosis ? '刷新诊断' : '生成诊断' }}
            </el-button>
          </div>
        </div>
        <el-alert v-if="aiError" type="error" show-icon :closable="false">
          <template #title>{{ escapeText(aiError) }}</template>
          <template #default>
            <el-button size="small" type="danger" plain :icon="RefreshRight" @click="runAiDiagnosis">重试</el-button>
          </template>
        </el-alert>
        <template v-else-if="aiDiagnosis">
          <div class="ai-diagnosis-head">
            <el-tag :type="healthType(aiDiagnosis.healthLevel)" effect="dark" size="large" class="health-tag">
              <el-icon v-if="aiDiagnosis.healthLevel === '异常'"><WarningFilled /></el-icon>
              <el-icon v-else-if="aiDiagnosis.healthLevel === '关注'"><Warning /></el-icon>
              <el-icon v-else><CircleCheckFilled /></el-icon>
              {{ aiDiagnosis.healthLevel || '未知' }}
            </el-tag>
            <span v-if="aiDiagnosisTs" class="ai-ts">生成于 {{ formatTs(aiDiagnosisTs) }}</span>
          </div>
          <p class="ai-summary">{{ escapeText(aiDiagnosis.summary || '-') }}</p>
          <div v-if="aiDiagnosis.issues?.length" class="ai-section">
            <h4><el-icon size="14" color="var(--iah-danger)"><WarningFilled /></el-icon> 发现的问题（{{ aiDiagnosis.issues.length }}）</h4>
            <ul class="issue-list">
              <li v-for="(item, i) in aiDiagnosis.issues" :key="i">
                <el-tag size="small" type="danger" effect="plain" class="issue-sev">L2 重要</el-tag>
                {{ escapeText(item) }}
              </li>
            </ul>
          </div>
          <div v-if="aiDiagnosis.suggestedActions?.length" class="ai-section">
            <h4><el-icon size="14" color="var(--iah-primary-light)"><Promotion /></el-icon> 建议动作</h4>
            <ul>
              <li v-for="(item, i) in aiDiagnosis.suggestedActions" :key="i">{{ escapeText(item) }}</li>
            </ul>
          </div>
        </template>
        <div v-else class="ai-placeholder">
          <el-icon class="ph-icon" :size="28"><MagicStick /></el-icon>
          <p>点击「生成诊断」，让 AI 基于设备基础信息、最近采集数据和未处理告警给出健康评估。</p>
        </div>
      </div>

      <!-- AI 设备问答（Function Calling 折叠面板） -->
      <div class="card ai-card">
        <el-collapse v-model="qaOpen">
          <el-collapse-item name="qa">
            <template #title>
              <span class="qa-title"><el-icon :size="18"><ChatDotRound /></el-icon>AI 设备问答（自动查询实时数据）</span>
            </template>
            <div class="qa-tip">AI 将自动调用项目工具查询设备基础信息、最近告警与站点未处理告警后再回答（最多 3 轮工具调用）。</div>
            <div class="qa-input-row">
              <el-input v-model="qaQuestion" placeholder="例如：这台设备最近有什么告警？运行是否正常？"
                        :disabled="qaLoading" clearable @keyup.enter="askQuestion" />
              <el-button type="primary" :icon="ChatDotRound" :loading="qaLoading" @click="askQuestion">提问</el-button>
            </div>
            <el-alert v-if="qaError" type="error" show-icon :closable="false" class="qa-error">
              <template #title>{{ escapeText(qaError) }}</template>
              <template #default>
                <el-button size="small" type="danger" plain :icon="RefreshRight" @click="retryQuestion">重试</el-button>
              </template>
            </el-alert>
            <template v-else-if="qaResult">
              <div class="qa-meta">
                <el-tag size="small" :type="qaResult.referencedRealTime ? 'success' : 'warning'" effect="light">
                  {{ qaResult.referencedRealTime ? '已参考实时数据' : '未参考实时数据' }}
                </el-tag>
                <el-tag size="small" type="info" effect="plain">工具调用 {{ qaResult.toolCalls }} 次 / {{ qaResult.toolRounds }} 轮</el-tag>
                <el-tag v-if="qaResult.truncated" size="small" type="warning" effect="dark">已达 3 轮工具上限</el-tag>
                <el-tag v-for="(t, i) in qaResult.toolTrace || []" :key="i" size="small" effect="plain"
                        :type="t.success ? 'success' : 'danger'">
                  {{ t.toolName }}{{ t.success ? '' : ' ✗' }}
                </el-tag>
                <span v-if="qaResultTs" class="ai-ts">回答于 {{ formatTs(qaResultTs) }}</span>
              </div>
              <div class="qa-answer-head">
                <el-button size="small" :icon="RefreshRight" plain :loading="qaLoading" @click="retryQuestion">重新回答</el-button>
                <el-button size="small" :icon="CopyDocument" plain @click="copyQaAnswer">复制回答</el-button>
              </div>
              <p class="ai-summary qa-answer">{{ escapeText(qaResult.answer) }}</p>
            </template>
            <div v-else class="ai-placeholder">
              <el-icon class="ph-icon" :size="26"><ChatDotRound /></el-icon>
              <p>输入问题后，AI 将基于实时数据回答设备状态。</p>
            </div>
          </el-collapse-item>
        </el-collapse>
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
import {
  ArrowLeft, MagicStick, ChatDotRound, RefreshRight, WarningFilled, Warning,
  CircleCheckFilled, Promotion, CopyDocument,
} from '@element-plus/icons-vue'
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
const aiDiagnosisTs = ref(0)

// AI 设备问答（Function Calling）
const qaOpen = ref([])
const qaQuestion = ref('')
const qaLoading = ref(false)
const qaError = ref('')
const qaResult = ref(null)
const qaResultTs = ref(0)
const lastQaText = ref('')

const escapeMap = {
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}
function escapeText(s) {
  if (s == null) return ''
  return String(s).replace(/[&<>"']/g, ch => escapeMap[ch])
}
function formatTs(ts) {
  if (!ts) return ''
  try {
    const d = new Date(ts)
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    const now = new Date()
    if (d.toDateString() === now.toDateString()) return `今天 ${hh}:${mm}`
    const MM = String(d.getMonth() + 1).padStart(2, '0')
    const DD = String(d.getDate()).padStart(2, '0')
    return `${MM}-${DD} ${hh}:${mm}`
  } catch { return '' }
}

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
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#242831',
      borderColor: '#2a2e3a',
      textStyle: { color: '#e8eaed' },
    },
    grid: { left: 50, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: filtered.map(d => d.recordedAt?.slice(11, 16)),
      axisLabel: { fontSize: 11, color: '#9aa0ac' },
      axisLine: { lineStyle: { color: '#2a2e3a' } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: { fontSize: 11, color: '#9aa0ac' },
      splitLine: { lineStyle: { color: '#242831' } },
    },
    series: [{
      data: filtered.map(d => d.dataValue),
      type: 'line',
      smooth: true,
      lineStyle: { color, width: 2 },
      itemStyle: { color },
      areaStyle: { color: `${color}15` },
    }],
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
  try {
    const res = await aiApi.deviceDiagnosis(deviceId)
    aiDiagnosis.value = res.data || res
    aiDiagnosisTs.value = Date.now()
  } catch (e) {
    aiError.value = e.message || 'AI 健康诊断失败'
    aiDiagnosis.value = null
  } finally {
    aiLoading.value = false
  }
}

const askQuestion = async () => {
  const question = qaQuestion.value?.trim()
  if (!question) {
    ElMessage.warning('请输入问题')
    return
  }
  lastQaText.value = question
  await doAskQuestion(question)
}

const retryQuestion = async () => {
  const q = lastQaText.value || qaQuestion.value?.trim()
  if (!q) {
    ElMessage.warning('没有可重试的问题')
    return
  }
  await doAskQuestion(q)
}

const copyQaAnswer = async () => {
  const text = qaResult.value?.answer
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制回答')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

async function doAskQuestion(question) {
  qaLoading.value = true
  qaError.value = ''
  qaResult.value = null
  try {
    const res = await aiApi.deviceStatus(deviceId, question)
    qaResult.value = res.data || res
    qaResultTs.value = Date.now()
  } catch (e) {
    qaError.value = e.message || 'AI 设备问答失败'
  } finally {
    qaLoading.value = false
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
  font-size: 14px;
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
.ai-card-action {
  flex: none;
  display: flex;
  gap: 8px;
}
.ai-card-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ai-card-title h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--iah-text);
}
.ai-card-subtitle {
  font-size: 12px;
  color: var(--iah-text-muted);
  margin-top: 2px;
}
.ai-card-title .el-icon { color: var(--iah-primary-light); }
.ai-diagnosis-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.health-tag :deep(.el-tag__content) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.ai-ts {
  font-size: 12px;
  color: var(--iah-text-muted);
  font-family: var(--font-mono);
}
.ai-summary {
  font-size: 14px;
  color: var(--iah-text);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 10px 0 6px;
  background: var(--iah-panel-hover);
  border-left: 3px solid var(--iah-primary);
  padding: 10px 12px;
  border-radius: var(--radius-md);
}
.qa-answer { background: var(--iah-panel-hover); }
.qa-answer-head {
  margin: 12px 0 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.ai-section { margin-top: 16px; }
.ai-section h4 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--iah-text);
  margin: 0 0 8px;
}
.ai-section ul {
  margin: 0;
  padding-left: 20px;
  color: var(--iah-text-secondary);
  line-height: 1.9;
  font-size: 13px;
}
.issue-list li { display: flex; align-items: flex-start; gap: 6px; }
.issue-sev { margin-top: 3px; flex: none; }
.qa-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}
.qa-title .el-icon { color: var(--iah-primary-light); }
.qa-tip {
  font-size: 12px;
  color: var(--iah-text-muted);
  margin: 4px 0 12px;
  padding: 6px 10px;
  background: var(--iah-panel-hover);
  border-radius: var(--radius-md);
}
.qa-input-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.qa-error { margin-bottom: 12px; }
.qa-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 10px;
  background: var(--iah-panel-hover);
  border-radius: var(--radius-md);
}
.ai-placeholder {
  text-align: center;
  padding: 36px 20px 30px;
  color: var(--iah-text-muted);
  border: 1px dashed var(--iah-border);
  border-radius: var(--radius-md);
  background: var(--iah-panel-hover);
}
.ai-placeholder .ph-icon {
  color: var(--iah-primary-light);
  opacity: 0.8;
  margin-bottom: 10px;
}
.ai-placeholder p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}
</style>
