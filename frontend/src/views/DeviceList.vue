<template>
  <div class="device-list">
    <div class="toolbar">
      <input v-model="keyword" placeholder="搜索设备名称/编码" @keyup.enter="fetchDevices" class="search-input" />
      <select v-model="statusFilter" @change="fetchDevices" class="filter-select">
        <option value="">全部状态</option>
        <option value="1">在线</option>
        <option value="0">离线</option>
        <option value="2">维护中</option>
      </select>
      <button class="btn-primary" @click="openAdd">+ 新增设备</button>
    </div>

    <table class="data-table" v-if="devices.length">
      <thead>
        <tr>
          <th>ID</th>
          <th>设备名称</th>
          <th>编码</th>
          <th>类型</th>
          <th>状态</th>
          <th>位置</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="d in devices" :key="d.id" @click="$router.push(`/devices/${d.id}`)" class="clickable">
          <td>{{ d.id }}</td>
          <td>{{ d.deviceName }}</td>
          <td><code>{{ d.deviceCode }}</code></td>
          <td>{{ d.deviceType }}</td>
          <td><span :class="statusClass(d.status)">{{ statusLabel(d.status) }}</span></td>
          <td>{{ d.location || '-' }}</td>
          <td>{{ fmtTime(d.updatedAt) }}</td>
          <td @click.stop>
            <button class="btn-sm" @click="openEdit(d)">编辑</button>
            <button class="btn-sm btn-danger" @click="handleDelete(d.id)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">暂无设备数据</p>

    <div class="pager" v-if="total > pageSize">
      <button :disabled="page <= 1" @click="page--; fetchDevices()">上一页</button>
      <span>第 {{ page }} / {{ Math.ceil(total / pageSize) }} 页（共 {{ total }} 条）</span>
      <button :disabled="page * pageSize >= total" @click="page++; fetchDevices()">下一页</button>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <div class="modal-overlay" v-if="showForm" @click.self="showForm = false">
      <div class="modal">
        <h3>{{ isEdit ? '编辑设备' : '新增设备' }}</h3>
        <label>设备名称 <input v-model="form.deviceName" /></label>
        <label>设备编码 <input v-model="form.deviceCode" /></label>
        <label>
          设备类型
          <select v-model="form.deviceType">
            <option value="">请选择</option>
            <option>PLC</option><option>SENSOR</option><option>CAMERA</option><option>ROBOT</option><option>OTHER</option>
          </select>
        </label>
        <label>状态
          <select v-model.number="form.status">
            <option :value="1">在线</option><option :value="0">离线</option><option :value="2">维护中</option>
          </select>
        </label>
        <label>IP 地址 <input v-model="form.ipAddress" /></label>
        <label>端口 <input v-model.number="form.port" type="number" /></label>
        <label>安装位置 <input v-model="form.location" /></label>
        <div class="modal-actions">
          <button class="btn-primary" @click="submitForm">{{ isEdit ? '保存' : '创建' }}</button>
          <button @click="showForm = false">取消</button>
        </div>
      </div>
    </div>

    <div class="msg" v-if="msg">{{ msg }}</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { deviceApi } from '../api/index.js'

const devices = ref([])
const keyword = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)
const msg = ref('')

const showForm = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const form = ref({ deviceName: '', deviceCode: '', deviceType: '', status: 1, ipAddress: '', port: null, location: '' })

const fetchDevices = async () => {
  try {
    const res = await deviceApi.list({ keyword: keyword.value, status: statusFilter.value, page: page.value, pageSize })
    devices.value = res.data?.records || res.data || []
    total.value = res.data?.total || devices.value.length
  } catch (e) { msg.value = e.message }
}

const openAdd = () => {
  isEdit.value = false; editingId.value = null
  form.value = { deviceName: '', deviceCode: '', deviceType: '', status: 1, ipAddress: '', port: null, location: '' }
  showForm.value = true
}

const openEdit = (d) => {
  isEdit.value = true; editingId.value = d.id
  form.value = { ...d }
  showForm.value = true
}

const submitForm = async () => {
  try {
    if (isEdit.value) {
      await deviceApi.update(editingId.value, form.value)
    } else {
      await deviceApi.create(form.value)
    }
    showForm.value = false
    fetchDevices()
    msg.value = isEdit.value ? '更新成功' : '创建成功'
    setTimeout(() => msg.value = '', 2000)
  } catch (e) { msg.value = e.message }
}

const handleDelete = async (id) => {
  if (!confirm('确认删除该设备？')) return
  try {
    await deviceApi.delete(id)
    fetchDevices()
    msg.value = '删除成功'
    setTimeout(() => msg.value = '', 2000)
  } catch (e) { msg.value = e.message }
}

const statusClass = (s) => ({ 1: 'online', 0: 'offline', 2: 'maintenance' }[s] || '')
const statusLabel = (s) => ({ 1: '在线', 0: '离线', 2: '维护中' }[s] || '未知')
const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchDevices)
</script>

<style scoped>
.device-list { max-width: 1100px; margin: 0 auto; padding: 20px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.search-input { flex: 1; min-width: 180px; padding: 8px 12px; border: 1px solid #d0d5dd; border-radius: 6px; font-size: 14px; }
.filter-select { padding: 8px 12px; border: 1px solid #d0d5dd; border-radius: 6px; font-size: 14px; }
.btn-primary { background: #1d4ed8; color: #fff; border: none; padding: 8px 20px; border-radius: 6px; cursor: pointer; font-size: 14px; }
.btn-primary:hover { background: #1e40af; }
.btn-sm { padding: 4px 12px; border: 1px solid #d0d5dd; border-radius: 4px; background: #fff; cursor: pointer; font-size: 13px; margin-right: 6px; }
.btn-sm:hover { background: #f3f4f6; }
.btn-danger { color: #dc2626; border-color: #fca5a5; }
.btn-danger:hover { background: #fef2f2; }
.data-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.data-table th { text-align: left; padding: 10px 8px; border-bottom: 2px solid #e5e7eb; color: #6b7280; font-weight: 600; }
.data-table td { padding: 10px 8px; border-bottom: 1px solid #f3f4f6; }
.clickable { cursor: pointer; }
.clickable:hover { background: #f9fafb; }
.online { color: #16a34a; font-weight: 600; }
.offline { color: #9ca3af; }
.maintenance { color: #f59e0b; font-weight: 600; }
.empty { text-align: center; color: #9ca3af; padding: 40px 0; }
.pager { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 16px; font-size: 14px; }
.pager button { padding: 6px 14px; border: 1px solid #d0d5dd; border-radius: 4px; background: #fff; cursor: pointer; }
.pager button:disabled { opacity: 0.4; cursor: default; }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.35); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #fff; border-radius: 8px; padding: 24px; width: 440px; max-height: 90vh; overflow-y: auto; }
.modal h3 { margin: 0 0 16px; font-size: 18px; }
.modal label { display: block; margin-bottom: 12px; font-size: 14px; color: #374151; }
.modal input, .modal select { width: 100%; padding: 8px 10px; border: 1px solid #d0d5dd; border-radius: 6px; font-size: 14px; margin-top: 4px; box-sizing: border-box; }
.modal-actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 16px; }
.modal-actions button { padding: 8px 18px; border-radius: 6px; font-size: 14px; cursor: pointer; border: 1px solid #d0d5dd; background: #fff; }
.msg { position: fixed; bottom: 24px; right: 24px; background: #1f2937; color: #fff; padding: 10px 20px; border-radius: 6px; font-size: 14px; z-index: 200; }
</style>
