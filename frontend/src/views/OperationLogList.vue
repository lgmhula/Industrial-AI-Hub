<template>
  <div class="oplog-list">
    <div class="toolbar">
      <button class="btn-sm" @click="fetchLogs">刷新</button>
    </div>

    <LoadingSpinner :visible="loading" text="加载操作日志..." />

    <table class="data-table" v-if="!loading && logs.length">
      <thead>
        <tr>
          <th>ID</th><th>用户ID</th><th>操作类型</th><th>目标类型</th>
          <th>描述</th><th>IP</th><th>时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="l in logs" :key="l.id">
          <td>{{ l.id }}</td>
          <td>{{ l.userId ?? '-' }}</td>
          <td><code>{{ l.operationType }}</code></td>
          <td>{{ l.targetType }}</td>
          <td class="msg-cell">{{ l.description || '-' }}</td>
          <td><code>{{ l.ipAddress }}</code></td>
          <td>{{ fmtTime(l.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!loading && !logs.length" class="empty">暂无操作日志（需 ADMIN 权限）</p>

    <div class="pager" v-if="total > pageSize">
      <button :disabled="page <= 1" @click="page--; fetchLogs()">上一页</button>
      <span>第 {{ page }} / {{ Math.ceil(total / pageSize) }} 页（共 {{ total }} 条）</span>
      <button :disabled="page * pageSize >= total" @click="page++; fetchLogs()">下一页</button>
    </div>

    <ToastMessage ref="toastRef" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { operationLogApi } from '../api/index.js'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ToastMessage from '../components/ToastMessage.vue'

const logs = ref([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const toastRef = ref(null)
const toast = (msg, type = 'error') => toastRef.value?.show(msg, type)

const fetchLogs = async () => {
  loading.value = true
  try {
    const res = await operationLogApi.list({ page: page.value, size: pageSize })
    logs.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    toast(e.message)
  } finally {
    loading.value = false
  }
}

const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchLogs)
</script>

<style scoped>
.oplog-list { max-width: 1100px; margin: 0 auto; padding: 20px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
.btn-sm { padding: 4px 12px; border: 1px solid #d0d5dd; border-radius: 4px; background: #fff; cursor: pointer; font-size: 13px; }
.btn-sm:hover { background: #f3f4f6; }
.data-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.data-table th { text-align: left; padding: 10px 8px; border-bottom: 2px solid #e5e7eb; color: #6b7280; font-weight: 600; white-space: nowrap; }
.data-table td { padding: 10px 8px; border-bottom: 1px solid #f3f4f6; }
.msg-cell { max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty { text-align: center; color: #9ca3af; padding: 40px 0; }
.pager { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 16px; font-size: 14px; flex-wrap: wrap; }
.pager button { padding: 6px 14px; border: 1px solid #d0d5dd; border-radius: 4px; background: #fff; cursor: pointer; }
.pager button:disabled { opacity: 0.4; cursor: default; }
</style>
