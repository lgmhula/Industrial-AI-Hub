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
            <el-statistic title="平均值" :value="stats.avg ?? 0" :precision="2" />
          </div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="card stat-card">
            <el-statistic title="最小值" :value="stats.min ?? 0" :precision="2" />
          </div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="card stat-card">
            <el-statistic title="最大值" :value="stats.max ?? 0" :precision="2" />
          </div>
        </el-col>
      </el-row>

      <!-- 趋势图 -->
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <div class="card chart-card">
            <h3 class="section-title">温度趋势 (°C)</h3>
            <v-chart v-if="tempOption" :option="tempOption" autoresize style="height: 300px" />
            <EmptyState v-else icon="🌡️" title="暂无温度数据" />
          </div>
        </el-col>
        <el-col :xs="24" :md="12">
          <div class="card chart-card">
            <h3 class="section-title">压力趋势 (kPa)</h3>
            <v-chart v-if="pressureOption" :option="pressureOption" autoresize style="height: 300px" />
            <EmptyState v-else icon="📈" title="暂无压力数据" />
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
import { ArrowLeft } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { deviceApi, deviceDataApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const route = useRoute()
const deviceId = Number(route.params.id)
const device = ref({})
const stats = ref(null)
const allData = ref([])
const recentData = ref([])
const loading = ref(false)

const tempOption = computed(() => buildChartOption(allData.value, 'TEMPERATURE', '#3b82f6'))
const pressureOption = computed(() => buildChartOption(allData.value, 'PRESSURE', '#ef4444'))

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

// 客户端聚合统计（后端 stats 接口需指定 dataType，此处对全部采集值做通用统计）
const computeStats = (data) => {
  const vals = data.map(d => Number(d.dataValue)).filter(v => !Number.isNaN(v))
  if (!vals.length) return null
  const sum = vals.reduce((a, b) => a + b, 0)
  return {
    count: vals.length,
    avg: sum / vals.length,
    min: Math.min(...vals),
    max: Math.max(...vals),
  }
}

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
</style>
