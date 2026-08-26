<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title"><el-icon><Setting /></el-icon>角色管理</div>
        <div class="page-subtitle">共 {{ roles.length }} 个角色</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增角色</el-button>
    </div>

    <div class="card">
      <el-table :data="roles" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleName" label="角色名称" min-width="140">
          <template #default="{ row }">
            <span class="role-name">{{ row.roleName }}</span>
            <el-tag v-if="isBuiltin(row)" size="small" type="warning" effect="dark" class="builtin-tag">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleCode" label="编码" width="140">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.roleCode }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" :loading="row._toggling"
              :disabled="isBuiltin(row)"
              @change="handleToggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!isBuiltin(row)" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="!isBuiltin(row)" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            <span v-if="isBuiltin(row)" class="builtin-text">内置不可改</span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState icon="🏷️" title="暂无角色" desc="点击「新增角色」开始添加" />
        </template>
      </el-table>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="showForm" :title="isEdit ? '编辑角色' : '新增角色'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如：运维工程师" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如：MAINTAINER" :disabled="isEdit"
            class="code-input" />
          <div v-if="isEdit" class="field-hint">编码创建后不可修改</div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="角色职责描述" />
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
import { Plus } from '@element-plus/icons-vue'
import { roleApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'

const BUILTIN_CODES = ['ADMIN', 'OPERATOR', 'VIEWER']

const roles = ref([])
const loading = ref(false)
const showForm = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ roleName: '', roleCode: '', description: '' })

const rules = {
  roleName: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  roleCode: [
    { required: true, message: '角色编码不能为空', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: '大写字母开头，仅含大写字母+数字+下划线', trigger: 'blur' },
  ],
}

const isBuiltin = (row) => BUILTIN_CODES.includes(row.roleCode)

const fetchRoles = async () => {
  loading.value = true
  try {
    const res = await roleApi.list()
    roles.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message || '加载角色列表失败')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  Object.assign(form, { roleName: '', roleCode: '', description: '' })
}

const openAdd = () => {
  isEdit.value = false
  editingId.value = null
  resetForm()
  showForm.value = true
}

const openEdit = (row) => {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    roleName: row.roleName,
    roleCode: row.roleCode,
    description: row.description || '',
  })
  showForm.value = true
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await roleApi.update(editingId.value, { ...form })
      ElMessage.success('角色更新成功')
    } else {
      await roleApi.create({ ...form, status: 1 })
      ElMessage.success('角色创建成功')
    }
    showForm.value = false
    await fetchRoles()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleToggleStatus = async (row) => {
  row._toggling = true
  try {
    await roleApi.toggleStatus(row.id)
    row.status = row.status === 1 ? 0 : 1
    ElMessage.success(row.status === 1 ? '角色已启用' : '角色已禁用')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    row._toggling = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确认删除角色「${row.roleName}」？已分配此角色的用户将失去对应权限。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger',
  }).then(async () => {
    try {
      await roleApi.delete(row.id)
      ElMessage.success('角色已删除')
      await fetchRoles()
    } catch (e) {
      ElMessage.error(e.message || '删除失败')
    }
  }).catch(() => {})
}

const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(fetchRoles)
</script>

<style scoped>
.role-name {
  font-weight: 600;
  margin-right: 6px;
}
.builtin-tag {
  margin-left: 4px;
}
.field-hint {
  font-size: 12px;
  color: var(--iah-text-muted);
  margin-top: 2px;
}
.builtin-text {
  font-size: 12px;
  color: var(--iah-text-muted);
}
.code-input :deep(.el-input__inner:disabled) {
  background-color: var(--iah-bg);
}
</style>
