<template>
  <div class="alarm-list">
    <div class="toolbar">
      <select v-model="statusFilter" @change="fetchAlarms" class="filter-select">
        <option value="">全部状态</option>
        <option value="0">未处理</option>
        <option value="1">已确认</option>
        <option value="2">已解决</option>
      </select>
      <button class="btn-sm" @click="fetchAlarms">刷新</button>
    </div>

    <table class="data-table" v-if="alarms.length">
      <thead>
        <tr>
          <th>ID</th>
          <th>设备</th>
          <th>告警类型</th>
          <th>等级</th>
          <th>描述</th>
          <th>状态</th>
          <th>触发时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="a in alarms" :key="a.id">
          <td>{{ a.id }}</td>
          <td>{{ a.deviceId }}</td>
          <td><code>{{ a.alarmType }}</code></td>
          <td><span :class="levelClass(a.alarmLevel)">{{ levelLabel(a.alarmLevel) }}</span></td>
          <td class="msg-cell">{{ a.alarmMessage }}</td>
          <td><span :class="statusClass(a.status)">{{ statusLabel(a.status) }}</span></td>
          <td>{{ fmtTime(a.triggeredAt) }}</td>
          <td>
            <button v-if="a.status === 0" class="btn-sm" @click="handleAck(a.id)">确认</button>
            <button v-if="a.status !== 2" class="btn-sm btn-primary" @click="handleResolve(a.id)">解决</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">暂无报警记录</p>

    <div class="pager" v-if="total > pageSize">
      <button :disabled="page <= 1" @click="page--; fetchAlarms()">上一页</button>
      <span>第 {{ page }} / {{ Math.ceil(total / pageSize) }} 页（共 {{ total }} 条）</span>
      <button :disabled="page * pageSize >= total" @click="page++; fetchAlarms()">下一页</button>
    </div>

    <div class="msg" v-if="msg">{{ msg }}</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { alarmApi } from '../api/index.js'

const alarms = ref([])
const statusFilter = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)
const msg = ref('')

const fetchAlarms = async () => {
  try {
    const api = statusFilter.value !== ''
      ? alarmApi.listByStatus(statusFilter.value, { page: page.value, size: pageSize })
      : alarmApi.list({ page: page.value, size: pageSize })
    const res = await api
    alarms.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) { msg.value = e.message }
}

const handleAck = async (id) => {
  try { await alarmApi.acknowledge(id); fetchAlarms(); msg.value = '已确认' }
  catch (e) { msg.value = e.message }
}

const handleResolve = async (id) => {
  try { await alarmApi.resolve(id); fetchAlarms(); msg.value = '已解决' }
  catch (e) { msg.value = e.message }
}

const levelClass = (l) => ({ 1: 'level-info', 2: 'level-warn', 3: 'level-urgent' }[l] || '')
const levelLabel = (l) => ({ 1: '一般', 2: '重要', 3: '紧急' }[l] || '-')
const statusClass = (s) => ({ 0: 'st-pending', 1: 'st-acked', 2: 'st-resolved' }[s] || '')
const statusLabel = (s) => ({ 0: '未处理', 1: '已确认', 2: '已解决' }[s] || '-')
const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchAlarms)
</script>

<style scoped>
.alarm-list { max-width: 1100px; margin: 0 auto; padding: 20px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
.filter-select { padding: 8px 12px; border: 1px solid #d0d5dd; border-radius: 6px; font-size: 14px; }
.btn-sm { padding: 4px 12px; border: 1px solid #d0d5dd; border-radius: 4px; background: #fff; cursor: pointer; font-size: 13px; margin-right: 6px; }
.btn-sm:hover { background: #f3f4f6; }
.btn-primary { background: #1d4ed8; color: #fff; border-color: #1d4ed8; }
.btn-primary:hover { background: #1e40af; }
.data-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.data-table th { text-align: left; padding: 10px 8px; border-bottom: 2px solid #e5e7eb; color: #6b7280; font-weight: 600; }
.data-table td { padding: 10px 8px; border-bottom: 1px solid #f3f4f6; }
.msg-cell { max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.level-info { color: #6b7280; }
.level-warn { color: #f59e0b; font-weight: 600; }
.level-urgent { color: #dc2626; font-weight: 700; }
.st-pending { color: #dc2626; font-weight: 600; }
.st-acked { color: #2563eb; }
.st-resolved { color: #16a34a; }
.empty { text-align: center; color: #9ca3af; padding: 40px 0; }
.pager { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 16px; font-size: 14px; }
.pager button { padding: 6px 14px; border: 1px solid #d0d5dd; border-radius: 4px; background: #fff; cursor: pointer; }
.pager button:disabled { opacity: 0.4; cursor: default; }
.msg { position: fixed; bottom: 24px; right: 24px; background: #1f2937; color: #fff; padding: 10px 20px; border-radius: 6px; font-size: 14px; z-index: 200; }
</style>
