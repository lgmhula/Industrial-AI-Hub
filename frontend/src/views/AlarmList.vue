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
      <div class="ai-actions-row">
        <el-button type="primary" plain :icon="MagicStick" :loading="aiLoading" :disabled="!alarms.length"
                   @click="openAiSummaryForScope('page')">
          AI 摘要当前页（{{ alarms.length }}）
        </el-button>
        <el-button type="success" plain :icon="MagicStick" :loading="aiLoading"
                   :disabled="selectedIds.length === 0"
                   @click="openAiSummaryForScope('selected')">
          AI 摘要已选（{{ selectedIds.length }}）
        </el-button>
        <div class="ai-tip">单行操作请使用表格中「AI 摘要」按钮</div>
      </div>
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

    <el-dialog v-model="aiDialogVisible" width="680px" destroy-on-close top="6vh">
      <template #header>
        <div class="dialog-header">
          <el-icon size="18" color="var(--iah-primary-light)"><MagicStick /></el-icon>
          <div>
            <div class="dialog-title">AI 告警摘要</div>
            <div class="dialog-subtitle">
              <template v-if="aiScope === 'single'">单条告警 #{{ aiLastRowId }}</template>
              <template v-else-if="aiScope === 'page'">当前页 {{ alarms.length }} 条</template>
              <template v-else>已选 {{ selectedIds.length }} 条</template>
              <span v-if="aiSummaryTs" class="ai-ts">· 生成于 {{ formatTs(aiSummaryTs) }}</span>
            </div>
          </div>
        </div>
      </template>

      <!-- Loading 骨架 -->
      <div v-if="aiLoading" class="ai-skeleton">
        <div class="sk-row sk-tag"></div>
        <div class="sk-row sk-line" style="width: 96%"></div>
        <div class="sk-row sk-line" style="width: 88%"></div>
        <div class="sk-row sk-line" style="width: 74%"></div>
        <div class="sk-block">
          <div class="sk-row sk-title"></div>
          <div class="sk-row sk-line" style="width: 92%"></div>
          <div class="sk-row sk-line" style="width: 80%"></div>
        </div>
        <div class="sk-block">
          <div class="sk-row sk-title"></div>
          <div class="sk-row sk-line" style="width: 90%"></div>
          <div class="sk-row sk-line" style="width: 70%"></div>
          <div class="sk-row sk-line" style="width: 85%"></div>
        </div>
        <div class="ai-loading-tip"><el-icon><Loading /></el-icon> AI 正在分析告警上下文，可能需要 2-10 秒...</div>
      </div>

      <el-alert v-else-if="aiError" type="error" show-icon :closable="false">
        <template #title>{{ escapeText(aiError) }}</template>
        <template #default>
          <el-button size="small" type="danger" plain :icon="RefreshRight" @click="retryAiSummary">重试</el-button>
        </template>
      </el-alert>

      <template v-else-if="aiSummary">
        <div class="ai-toolbar">
          <el-button size="small" :icon="RefreshRight" plain :loading="aiLoading" @click="retryAiSummary">重新生成</el-button>
          <el-button size="small" :icon="CopyDocument" plain @click="copySummary">复制全文</el-button>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="优先级">
            <el-tag :type="aiPriorityType(aiSummary.priority)" effect="dark" size="large">
              <el-icon v-if="aiSummary.priority === '高'"><WarningFilled /></el-icon>
              <el-icon v-else-if="aiSummary.priority === '中'"><Warning /></el-icon>
              <el-icon v-else><InfoFilled /></el-icon>
              {{ aiSummary.priority || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="摘要">
            <div class="ai-summary-text">{{ escapeText(aiSummary.summary || '-') }}</div>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="aiSummary.possibleCauses?.length" class="ai-section">
          <h4><el-icon size="14" color="var(--iah-warning)"><Warning /></el-icon> 可能原因（{{ aiSummary.possibleCauses.length }}）</h4>
          <ul>
            <li v-for="(item, i) in aiSummary.possibleCauses" :key="i">{{ escapeText(item) }}</li>
          </ul>
        </div>
        <div v-if="aiSummary.suggestedActions?.length" class="ai-section">
          <h4><el-icon size="14" color="var(--iah-primary-light)"><Promotion /></el-icon> 建议动作</h4>
          <ul>
            <li v-for="(item, i) in aiSummary.suggestedActions" :key="i">{{ escapeText(item) }}</li>
          </ul>
        </div>
        <div v-if="!aiSummary.possibleCauses?.length && !aiSummary.suggestedActions?.length && !aiSummary.summary"
             class="ai-empty-body">
          <EmptyState icon="📝" title="AI 返回内容为空" desc="请检查 AI 服务或稍后重试" />
        </div>
      </template>

      <div v-else class="ai-empty-body">
        <EmptyState icon="🤖" title="暂未生成摘要" desc="点击下方重新生成，或从表格选择告警后启动 AI 摘要" />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <span v-if="aiScope !== 'single'" class="scope-tag">
            范围：{{ { page: '当前页', selected: '已选择', single: '单条' }[aiScope] }}
          </span>
          <div class="footer-actions">
            <el-button v-if="aiSummary" size="small" :icon="RefreshRight" plain :loading="aiLoading" @click="retryAiSummary">重新生成</el-button>
            <el-button @click="aiDialogVisible = false">关闭</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import {
  Refresh, MagicStick, RefreshRight, CopyDocument, WarningFilled, Warning,
  InfoFilled, Promotion, Loading,
} from '@element-plus/icons-vue'
import { alarmApi, aiApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'
import { escapeText, safeJoin } from '../utils/escapeHtml.js'
import { useAuth } from '../composables/useAuth.js'

const { isAdmin } = useAuth()

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
const aiScope = ref('single') // 'single' | 'page' | 'selected'
const aiLastRowId = ref(null)
const aiLastParams = ref(null) // { ids } or { alarmIds } or null for single-row alarmId
const aiSummaryTs = ref(0)

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
    // 非 ADMIN 用户若无站点授权，后端返回空列表 — 提示用户联系管理员授权
    if (total.value === 0 && !isAdmin.value) {
      ElMessage.warning('暂无可见报警数据。若您是操作员/查看者，请联系管理员分配站点权限。')
    }
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
  aiScope.value = 'single'
  aiLastRowId.value = row.id
  aiLastParams.value = { kind: 'single', alarmId: row.id }
  aiDialogVisible.value = true
  await runAiSummary(async () => {
    const res = await aiApi.alarmSummary(row.id)
    return res.data || res
  })
}

const openAiSummaryForScope = async (scope) => {
  if (scope === 'page') {
    const ids = alarms.value.map(a => a.id)
    if (!ids.length) { ElMessage.warning('当前页没有数据'); return }
    aiScope.value = 'page'
    aiLastParams.value = { kind: 'batch', ids }
    aiDialogVisible.value = true
    await runAiSummary(async () => {
      // 后端接口：取第一条做摘要，若存在批量接口可替换
      const res = await aiApi.alarmSummary(ids[0])
      return res.data || res
    })
  } else if (scope === 'selected') {
    const ids = [...selectedIds.value]
    if (!ids.length) { ElMessage.warning('请先勾选告警'); return }
    aiScope.value = 'selected'
    aiLastParams.value = { kind: 'batch', ids }
    aiDialogVisible.value = true
    await runAiSummary(async () => {
      const res = await aiApi.alarmSummary(ids[0])
      return res.data || res
    })
  }
}

const retryAiSummary = async () => {
  const p = aiLastParams.value
  if (!p) { ElMessage.warning('没有可重试的请求'); return }
  await runAiSummary(async () => {
    if (p.kind === 'single') {
      const res = await aiApi.alarmSummary(p.alarmId)
      return res.data || res
    } else {
      const res = await aiApi.alarmSummary(p.ids[0])
      return res.data || res
    }
  })
}

const copySummary = async () => {
  const s = aiSummary.value
  if (!s) return
  const lines = []
  if (s.priority) lines.push(`优先级：${s.priority}`)
  if (s.summary) lines.push(`摘要：${s.summary}`)
  if (s.possibleCauses?.length) {
    lines.push('可能原因：')
    s.possibleCauses.forEach((c, i) => lines.push(`  ${i + 1}. ${c}`))
  }
  if (s.suggestedActions?.length) {
    lines.push('建议动作：')
    s.suggestedActions.forEach((a, i) => lines.push(`  ${i + 1}. ${a}`))
  }
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    ElMessage.success('已复制摘要全文')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

async function runAiSummary(fn) {
  aiLoading.value = true
  aiLoadingId.value = aiLastParams.value?.kind === 'single' ? aiLastParams.value.alarmId : 'batch'
  aiSummary.value = null
  aiError.value = ''
  try {
    const data = await fn()
    aiSummary.value = data
    aiSummaryTs.value = Date.now()
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
.ai-actions-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0 14px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--iah-border);
  flex-wrap: wrap;
}
.ai-tip {
  margin-left: auto;
  font-size: 12px;
  color: var(--iah-text-muted);
  font-family: var(--font-mono);
}
.dialog-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dialog-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--iah-text);
}
.dialog-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: var(--iah-text-muted);
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.ai-ts {
  font-family: var(--font-mono);
  color: var(--iah-text-muted);
}
.ai-skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 2px 4px;
}
.sk-row {
  height: 14px;
  background: linear-gradient(90deg, #242831 25%, #2a303c 50%, #242831 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
  border-radius: 6px;
}
.sk-tag { height: 26px; width: 92px; border-radius: 8px; }
.sk-title { height: 16px; width: 96px; }
.sk-line { height: 12px; }
.sk-block {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--iah-border);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.ai-loading-tip {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--iah-panel-hover);
  border: 1px solid var(--iah-border-soft);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--iah-primary-light);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
}
.ai-toolbar {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.ai-summary-text {
  color: var(--iah-text);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13.5px;
}
.ai-empty-body { padding: 24px 0 8px; }
.ai-section {
  margin-top: 16px;
}
.ai-section h4 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
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
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  flex-wrap: wrap;
}
.scope-tag {
  font-size: 12px;
  color: var(--iah-text-muted);
  background: var(--iah-panel-hover);
  border: 1px solid var(--iah-border);
  padding: 3px 10px;
  border-radius: 12px;
  font-family: var(--font-mono);
}
.footer-actions { display: flex; gap: 8px; align-items: center; }
</style>
