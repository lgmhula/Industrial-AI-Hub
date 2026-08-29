<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title"><el-icon><Document /></el-icon>操作日志</div>
        <div class="page-subtitle">
          {{ typeFilter || keyword ? `筛选结果 ${total} 条` : `共 ${total} 条操作记录` }}
        </div>
      </div>
      <el-button :icon="Refresh" @click="fetchLogs" :loading="loading">刷新</el-button>
    </div>

    <div class="card filter-bar">
      <el-select v-model="typeFilter" placeholder="全部类型" clearable style="width: 160px"
        @change="handleFilterChange">
        <el-option label="创建" value="CREATE" />
        <el-option label="更新" value="UPDATE" />
        <el-option label="删除" value="DELETE" />
        <el-option label="登录" value="LOGIN" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索描述" clearable style="width: 200px"
        @keyup.enter="handleFilterChange" @clear="handleFilterChange" />
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <div class="card">
      <el-table :data="logs" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" class-name="mono" />
        <el-table-column label="用户" width="120">
          <template #default="{ row }">{{ row.username || `#${row.userId}` }}</template>
        </el-table-column>
        <el-table-column prop="operationType" label="操作类型" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="opType(row.operationType)" effect="light">{{ row.operationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="目标类型" width="120" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP" width="140" class-name="mono">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.ipAddress }}</el-tag></template>
        </el-table-column>
        <el-table-column label="时间" width="170" class-name="mono">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <EmptyState icon="📋" title="暂无操作日志" desc="操作后会自动记录" />
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
import { operationLogApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'

const logs = ref([])
const typeFilter = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)

const fetchLogs = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    if (typeFilter.value) params.operationType = typeFilter.value
    const res = await operationLogApi.list(params)
    logs.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载日志失败')
  } finally {
    loading.value = false
  }
}

const handleFilterChange = () => { page.value = 1; fetchLogs() }
const handleReset = () => { typeFilter.value = ''; keyword.value = ''; page.value = 1; fetchLogs() }
const handlePageChange = (p) => { page.value = p; fetchLogs() }

const opType = (t) => {
  const s = String(t || '').toUpperCase()
  if (s.includes('DELETE')) return 'danger'
  if (s.includes('CREATE') || s.includes('INSERT')) return 'success'
  if (s.includes('UPDATE')) return 'warning'
  if (s.includes('LOGIN')) return 'primary'
  return 'info'
}
const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchLogs)
</script>
