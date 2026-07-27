<template>
  <div class="device-detail">
    <div class="header">
      <button class="back-btn" @click="$router.push('/')">&larr; 返回列表</button>
      <h2>{{ device.deviceName || '加载中...' }}</h2>
      <span :class="statusClass(device.status)">{{ statusLabel(device.status) }}</span>
    </div>

    <div class="info-grid" v-if="device.id">
      <div class="info-item"><label>设备编码</label><code>{{ device.deviceCode }}</code></div>
      <div class="info-item"><label>设备类型</label><span>{{ device.deviceType }}</span></div>
      <div class="info-item"><label>IP 地址</label><span>{{ device.ipAddress || '-' }}</span></div>
      <div class="info-item"><label>端口</label><span>{{ device.port || '-' }}</span></div>
      <div class="info-item"><label>安装位置</label><span>{{ device.location || '-' }}</span></div>
      <div class="info-item"><label>更新时间</label><span>{{ fmtTime(device.updatedAt) }}</span></div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row" v-if="stats">
      <div class="stat-card"><span class="stat-val">{{ stats.count }}</span><span class="stat-label">数据条数</span></div>
      <div class="stat-card"><span class="stat-val">{{ stats.avg?.toFixed(2) }}</span><span class="stat-label">平均值</span></div>
      <div class="stat-card"><span class="stat-val">{{ stats.min?.toFixed(2) }}</span><span class="stat-label">最小值</span></div>
      <div class="stat-card"><span class="stat-val">{{ stats.max?.toFixed(2) }}</span><span class="stat-label">最大值</span></div>
    </div>

    <!-- ECharts 温度趋势图 -->
    <div class="chart-section">
      <h3>温度趋势 (°C)</h3>
      <v-chart :option="tempOption" autoresize style="height: 320px" v-if="tempOption" />
      <p v-else class="empty">暂无温度数据</p>
    </div>

    <!-- ECharts 压力趋势图 -->
    <div class="chart-section">
      <h3>压力趋势 (kPa)</h3>
      <v-chart :option="pressureOption" autoresize style="height: 320px" v-if="pressureOption" />
      <p v-else class="empty">暂无压力数据</p>
    </div>

    <!-- 最近数据表格 -->
    <div class="data-section" v-if="recentData.length">
      <h3>最近采集数据</h3>
      <table class="data-table">
        <thead><tr><th>数据类型</th><th>数值</th><th>单位</th><th>采集时间</th></tr></thead>
        <tbody>
          <tr v-for="r in recentData" :key="r.id">
            <td>{{ r.dataType }}</td><td>{{ r.dataValue }}</td><td>{{ r.unit || '-' }}</td><td>{{ fmtTime(r.recordedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="msg" v-if="msg">{{ msg }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { deviceApi, deviceDataApi } from '../api/index.js'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const route = useRoute()
const deviceId = Number(route.params.id)
const device = ref({})
const stats = ref(null)
const allData = ref([])
const recentData = ref([])
const msg = ref('')

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
  try {
    const [dev, statsRes, dataRes] = await Promise.all([
      deviceApi.getById(deviceId),
      deviceDataApi.stats(deviceId).catch(() => null),
      deviceDataApi.list(deviceId, { page: 1, pageSize: 100 }).catch(() => ({ data: { records: [] } })),
    ])
    device.value = dev.data || dev
    stats.value = statsRes?.data || null
    const records = dataRes?.data?.records || dataRes?.data || []
    allData.value = records
    recentData.value = records.slice(-10).reverse()
  } catch (e) { msg.value = e.message }
}

const statusClass = (s) => ({ 1: 'online', 0: 'offline', 2: 'maintenance' }[s] || '')
const statusLabel = (s) => ({ 1: '在线', 0: '离线', 2: '维护中' }[s] || '未知')
const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchDetail)
</script>

<style scoped>
.device-detail { max-width: 1100px; margin: 0 auto; padding: 20px; }
.header { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; }
.back-btn { background: none; border: 1px solid #d0d5dd; padding: 6px 14px; border-radius: 6px; cursor: pointer; font-size: 14px; }
.back-btn:hover { background: #f3f4f6; }
.header h2 { margin: 0; font-size: 22px; }
.online { color: #16a34a; font-weight: 600; }
.offline { color: #9ca3af; }
.maintenance { color: #f59e0b; font-weight: 600; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; margin-bottom: 24px; }
.info-item { padding: 12px; background: #f9fafb; border-radius: 6px; }
.info-item label { display: block; font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.info-item code { font-size: 14px; background: #e5e7eb; padding: 2px 6px; border-radius: 4px; }
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 24px; }
.stat-card { background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 8px; padding: 16px; text-align: center; }
.stat-val { display: block; font-size: 28px; font-weight: 700; color: #0369a1; }
.stat-label { font-size: 12px; color: #6b7280; }
.chart-section { margin-bottom: 28px; }
.chart-section h3 { font-size: 16px; margin-bottom: 12px; color: #1f2937; }
.data-section h3 { font-size: 16px; margin-bottom: 12px; }
.data-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.data-table th { text-align: left; padding: 8px; border-bottom: 2px solid #e5e7eb; color: #6b7280; }
.data-table td { padding: 8px; border-bottom: 1px solid #f3f4f6; }
.empty { text-align: center; color: #9ca3af; padding: 40px 0; }
.msg { position: fixed; bottom: 24px; right: 24px; background: #1f2937; color: #fff; padding: 10px 20px; border-radius: 6px; font-size: 14px; z-index: 200; }
</style>
