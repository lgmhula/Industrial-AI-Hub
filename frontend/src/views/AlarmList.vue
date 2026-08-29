<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title"><el-icon><Bell /></el-icon>报警管理</div>
        <div class="page-subtitle">
          {{ levelFilter || keyword ? `筛选结果 ${total} 条` : `共 ${total} 条报警记录` }}
        </div>
      </div>
      <el-button :icon="Refresh" @click="fetchAlarms" :loading="loading">刷新</el-button>
    </div>

    <div v-if="selectedIds.length > 0" class="card batch-bar">
      <span class="batch-info">已选 {{ selectedIds.length }} 条</span>
      <el-button type="primary" size="small" @click="handleBatchAck">批量确认</el-button>
      <el-button type="success" size="small" @click="handleBatchResolve">批量解决</el-button>
      <el-button size="small" @click="clearSelection">取消选择</el-button>
    </div>

    <div class="card filter-bar">
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px"
        @change="handleFilterChange">
        <el-option label="未处理" :value="0" />
        <el-option label="已确认" :value="1" />
        <el-option label="已解决" :value="2" />
      </el-select>
      <el-select v-model="levelFilter" placeholder="全部等级" clearable style="width: 140px"
        @change="handleFilterChange">
        <el-option label="一般" :value="1" />
        <el-option label="重要" :value="2" />
        <el-option label="紧急" :value="3" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索告警描述" clearable style="width: 200px"
        @keyup.enter="handleFilterChange" @clear="handleFilterChange" />
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <div class="card">
      <el-table ref="tableRef" :data="alarms" v-loading="loading" stripe style="width: 100%"
        @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="id" label="ID" width="70" class-name="mono" />
        <el-table-column prop="deviceId" label="设备ID" width="90" class-name="mono" />
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
        <el-table-column label="触发时间" width="170" class-name="mono">
          <template #default="{ row }">{{ fmtTime(row.triggeredAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" size="small" :icon="MagicStick" :loading="aiLoadingId === row.id"
              @click="openAiSummary(row)">AI 摘要</el-button>
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

    <el-dialog v-model="aiDialogVisible" title="AI 告警摘要" width="620px" destroy-on-close>
      <div v-if="aiLoading" v-loading="true" class="ai-dialog-loading">AI 正在分析告警上下文...</div>
      <el-alert v-else-if="aiError" :title="aiError" type="error" show-icon :closable="false" />
      <template v-else-if="aiSummary">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="优先级">
            <el-tag :type="aiPriorityType(aiSummary.priority)" effect="light">{{ aiSummary.priority || '-' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="摘要">{{ aiSummary.summary || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="aiSummary.possibleCauses?.length" class="ai-section">
          <h4>可能原因</h4>
          <ul>
            <li v-for="(item, i) in aiSummary.possibleCauses" :key="i">{{ item }}</li>
          </ul>
        </div>
        <div v-if="aiSummary.suggestedActions?.length" class="ai-section">
          <h4>建议动作</h4>
          <ul>
            <li v-for="(item, i) in aiSummary.suggestedActions" :key="i">{{ item }}</li>
          </ul>
        </div>
      </template>
      <template #footer>
        <el-button @click="aiDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh, MagicStick } from '@element-plus/icons-vue'
import { alarmApi, aiApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'

const alarms = ref([])
const statusFilter = ref('')
const levelFilter = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const tableRef = ref(null)
const selectedIds = ref([])
const aiDialogVisible = ref(false)
const aiLoading = ref(false)
const aiLoadingId = ref(null)
const aiSummary = ref(null)
const aiError = ref('')

const handleSelectionChange = (rows) => { selectedIds.value = rows.map(r => r.id) }
const clearSelection = () => { tableRef.value?.clearSelection() }

const fetchAlarms = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    if (levelFilter.value !== '' && levelFilter.value !== null) params.alarmLevel = levelFilter.value
    const api = statusFilter.value !== '' && statusFilter.value !== null
      ? alarmApi.listByStatus(statusFilter.value, params)
      : alarmApi.list(params)
    const res = await api
    alarms.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载报警列表失败')
  } finally {
    loading.value = false
  }
}

const handleFilterChange = () => { page.value = 1; fetchAlarms() }
const handleReset = () => {
  statusFilter.value = ''
  levelFilter.value = ''
  keyword.value = ''
  page.value = 1
  fetchAlarms()
}
const handlePageChange = (p) => { page.value = p; fetchAlarms() }

const handleAck = async (id) => {
  try {
    await alarmApi.acknowledge(id)
    await fetchAlarms()
    ElMessage.success('告警已确认')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

const handleResolve = async (id) => {
  try {
    await alarmApi.resolve(id)
    await fetchAlarms()
    ElMessage.success('告警已解决')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

const handleBatchAck = async () => {
  const ids = [...selectedIds.value]
  let ok = 0, fail = 0
  for (const id of ids) {
    try { await alarmApi.acknowledge(id); ok++ } catch { fail++ }
  }
  clearSelection()
  await fetchAlarms()
  ElMessage.success(`批量确认完成：成功 ${ok}${fail > 0 ? `，失败 ${fail}` : ''}`)
}

const handleBatchResolve = async () => {
  const ids = [...selectedIds.value]
  let ok = 0, fail = 0
  for (const id of ids) {
    try { await alarmApi.resolve(id); ok++ } catch { fail++ }
  }
  clearSelection()
  await fetchAlarms()
  ElMessage.success(`批量解决完成：成功 ${ok}${fail > 0 ? `，失败 ${fail}` : ''}`)
}

const openAiSummary = async (row) => {
  aiDialogVisible.value = true
  aiLoading.value = true
  aiLoadingId.value = row.id
  aiSummary.value = null
  aiError.value = ''
  try {
    const res = await aiApi.alarmSummary(row.id)
    aiSummary.value = res.data || res
  } catch (e) {
    aiError.value = e.message || 'AI 摘要生成失败'
  } finally {
    aiLoading.value = false
    aiLoadingId.value = null
  }
}

const aiPriorityType = (p) => ({ 高: 'danger', 中: 'warning', 低: 'info' }[p] || 'info')
const levelType = (l) => ({ 1: 'info', 2: 'warning', 3: 'danger' }[l] || 'info')
const levelLabel = (l) => ({ 1: '一般', 2: '重要', 3: '紧急' }[l] || '-')
const statusType = (s) => ({ 0: 'danger', 1: 'primary', 2: 'success' }[s] || 'info')
const statusLabel = (s) => ({ 0: '未处理', 1: '已确认', 2: '已解决' }[s] || '-')
const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchAlarms)
</script>

<style scoped>
.done-text {
  font-size: 13px;
  color: var(--iah-text-muted);
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  margin-bottom: 12px;
}
.batch-info {
  font-size: 14px;
  color: var(--iah-text);
  font-weight: 500;
}
.ai-dialog-loading {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--iah-text-muted);
}
.ai-section {
  margin-top: 16px;
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
