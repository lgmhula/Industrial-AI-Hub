<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title"><el-icon><Bell /></el-icon>报警管理</div>
        <div class="page-subtitle">共 {{ total }} 条报警记录</div>
      </div>
      <el-button :icon="Refresh" @click="fetchAlarms" :loading="loading">刷新</el-button>
    </div>

    <div class="card filter-bar">
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 160px"
        @change="handleFilterChange">
        <el-option label="未处理" :value="0" />
        <el-option label="已确认" :value="1" />
        <el-option label="已解决" :value="2" />
      </el-select>
    </div>

    <div class="card">
      <el-table :data="alarms" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="deviceId" label="设备ID" width="90" />
        <el-table-column prop="alarmType" label="告警类型" width="140">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.alarmType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="levelType(row.alarmLevel)">{{ levelLabel(row.alarmLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alarmMessage" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.triggeredAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" link type="primary" size="small" @click="handleAck(row.id)">确认</el-button>
            <el-button v-if="row.status !== 2" link type="success" size="small" @click="handleResolve(row.id)">解决</el-button>
            <span v-if="row.status === 2" class="done-text">已完成</span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState icon="✅" title="暂无报警" desc="所有设备运行正常" />
        </template>
      </el-table>

      <el-pagination v-if="total > 0" class="pager" background
        layout="total, prev, pager, next, jumper"
        :total="total" :page-size="pageSize" :current-page="page"
        @current-change="handlePageChange" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { alarmApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'

const alarms = ref([])
const statusFilter = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)

const fetchAlarms = async () => {
  loading.value = true
  try {
    const api = statusFilter.value !== '' && statusFilter.value !== null
      ? alarmApi.listByStatus(statusFilter.value, { page: page.value, size: pageSize })
      : alarmApi.list({ page: page.value, size: pageSize })
    const res = await api
    alarms.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const handleFilterChange = () => { page.value = 1; fetchAlarms() }
const handlePageChange = (p) => { page.value = p; fetchAlarms() }

const handleAck = async (id) => {
  try { await alarmApi.acknowledge(id); await fetchAlarms(); ElMessage.success('告警已确认') }
  catch (e) { ElMessage.error(e.message) }
}

const handleResolve = async (id) => {
  try { await alarmApi.resolve(id); await fetchAlarms(); ElMessage.success('告警已解决') }
  catch (e) { ElMessage.error(e.message) }
}

const levelType = (l) => ({ 1: 'info', 2: 'warning', 3: 'danger' }[l] || 'info')
const levelLabel = (l) => ({ 1: '一般', 2: '重要', 3: '紧急' }[l] || '-')
const statusType = (s) => ({ 0: 'danger', 1: 'primary', 2: 'success' }[s] || 'info')
const statusLabel = (s) => ({ 0: '未处理', 1: '已确认', 2: '已解决' }[s] || '-')
const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchAlarms)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 18px;
  justify-content: flex-end;
}
.done-text {
  font-size: 13px;
  color: var(--iah-text-muted);
}
</style>
