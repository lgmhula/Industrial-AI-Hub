<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title"><el-icon><Cpu /></el-icon>设备管理</div>
        <div class="page-subtitle">共 {{ total }} 台设备</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增设备</el-button>
    </div>

    <!-- 筛选工具条 -->
    <div class="card filter-bar">
      <el-input v-model="keyword" placeholder="搜索设备名称 / 编码" :prefix-icon="Search"
        clearable style="width: 260px" @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px"
        @change="handleSearch">
        <el-option label="在线" :value="1" />
        <el-option label="离线" :value="0" />
        <el-option label="维护中" :value="2" />
      </el-select>
      <el-button :icon="Search" type="primary" plain @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <!-- 数据表格 -->
    <div class="card">
      <el-table :data="devices" v-loading="loading" stripe row-key="id"
        @row-click="(row) => $router.push(`/devices/${row.id}`)" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="deviceName" label="设备名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="deviceCode" label="编码" width="140">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.deviceCode }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="deviceType" label="类型" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)" effect="light">
              <span class="dot" :class="`dot-${row.status}`"></span>{{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.location || '-' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState icon="🖥️" title="暂无设备" desc="点击「新增设备」开始添加" />
        </template>
      </el-table>

      <el-pagination v-if="total > 0" class="pager" background
        layout="total, prev, pager, next, jumper"
        :total="total" :page-size="pageSize" :current-page="page"
        @current-change="handlePageChange" />
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="showForm" :title="isEdit ? '编辑设备' : '新增设备'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="form.deviceName" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="设备编码" prop="deviceCode">
          <el-input v-model="form.deviceCode" placeholder="请输入设备编码" />
        </el-form-item>
        <el-form-item label="设备类型" prop="deviceType">
          <el-select v-model="form.deviceType" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in deviceTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="sites.length > 1" label="归属站点">
          <el-select v-model="form.siteId" placeholder="选择站点" style="width: 100%">
            <el-option v-for="s in sites" :key="s.id" :label="s.siteName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在线</el-radio>
            <el-radio :value="0">离线</el-radio>
            <el-radio :value="2">维护中</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="IP 地址">
          <el-input v-model="form.ipAddress" placeholder="例如 192.168.1.10" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="form.location" placeholder="请输入安装位置" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false" :disabled="submitting">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { deviceApi, siteApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()
const deviceTypes = ['PLC', 'SENSOR', 'CAMERA', 'ROBOT', 'OTHER']

const devices = ref([])
const sites = ref([])
const keyword = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)

const showForm = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ deviceName: '', deviceCode: '', deviceType: '', siteId: null, status: 1, ipAddress: '', port: null, location: '' })

const rules = {
  deviceName: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }],
  deviceCode: [{ required: true, message: '设备编码不能为空', trigger: 'blur' }],
  deviceType: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
}

const fetchDevices = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    if (statusFilter.value !== '' && statusFilter.value !== null) params.status = statusFilter.value
    const res = await deviceApi.list(params)
    devices.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { page.value = 1; fetchDevices() }
const handleReset = () => { keyword.value = ''; statusFilter.value = ''; page.value = 1; fetchDevices() }
const handlePageChange = (p) => { page.value = p; fetchDevices() }

const resetForm = () => {
  Object.assign(form, { deviceName: '', deviceCode: '', deviceType: '', siteId: null, status: 1, ipAddress: '', port: null, location: '' })
}

const openAdd = () => {
  isEdit.value = false; editingId.value = null
  resetForm()
  showForm.value = true
}

const openEdit = (d) => {
  isEdit.value = true; editingId.value = d.id
  Object.assign(form, {
    deviceName: d.deviceName, deviceCode: d.deviceCode, deviceType: d.deviceType,
    siteId: d.siteId || null, status: d.status, ipAddress: d.ipAddress, port: d.port, location: d.location,
  })
  showForm.value = true
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await deviceApi.update(editingId.value, { ...form })
      ElMessage.success('设备更新成功')
    } else {
      await deviceApi.create({ ...form })
      ElMessage.success('设备创建成功')
    }
    showForm.value = false
    await fetchDevices()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确认删除设备「${row.deviceName}」？该操作不可恢复。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger',
  }).then(async () => {
    try {
      await deviceApi.delete(row.id)
      ElMessage.success('设备已删除')
      if (devices.value.length === 1 && page.value > 1) page.value--
      await fetchDevices()
    } catch (e) {
      ElMessage.error(e.message)
    }
  }).catch(() => {})
}

const statusType = (s) => ({ 1: 'success', 0: 'info', 2: 'warning' }[s] || 'info')
const statusLabel = (s) => ({ 1: '在线', 0: '离线', 2: '维护中' }[s] || '未知')
const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(async () => {
  try {
    const res = await siteApi.list()
    sites.value = res.data || []
  } catch {}
  fetchDevices()
})
</script>

<style scoped>
.dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 5px;
  vertical-align: middle;
}
.dot-1 { background: var(--iah-success); }
.dot-0 { background: var(--iah-text-muted); }
.dot-2 { background: var(--iah-warning); }
</style>
