<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title"><el-icon><UserFilled /></el-icon>用户管理</div>
        <div class="page-subtitle">共 {{ total }} 名用户（需 ADMIN 权限）</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增用户</el-button>
    </div>

    <div class="card filter-bar">
      <el-input v-model="keyword" placeholder="搜索用户名 / 邮箱" :prefix-icon="Search"
        clearable style="width: 240px" @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-button :icon="Search" type="primary" plain @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleResetSearch">重置</el-button>
    </div>

    <div class="card">
      <el-table :data="users" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" class-name="mono" />
        <el-table-column prop="username" label="用户名" min-width="140">
          <template #default="{ row }">
            <span class="username">{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <div class="role-tags">
              <el-tag v-for="code in (row.roleCodes || [])" :key="code" size="small"
                :type="roleTagType(code)" effect="light" class="role-tag">
                {{ code }}
              </el-tag>
              <span v-if="!row.roleCodes || row.roleCodes.length === 0" class="no-role">-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机" width="140" class-name="mono">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="isLocked(row)" size="small" type="warning">锁定中</el-tag>
            <el-tag v-else size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" class-name="mono">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleRowAction(cmd, row)">
              <el-button link type="info" size="small">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="roles">分配角色</el-dropdown-item>
                  <el-dropdown-item v-if="!isLocked(row) && row.status === 1" command="lock">锁定</el-dropdown-item>
                  <el-dropdown-item v-if="isLocked(row)" command="unlock">解锁</el-dropdown-item>
                  <el-dropdown-item command="password">重置密码</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除用户</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState icon="👥" title="暂无用户" desc="点击「新增用户」开始添加" />
        </template>
      </el-table>

      <el-pagination v-if="total > 0" class="pager" background
        layout="total, prev, pager, next, jumper"
        :total="total" :page-size="pageSize" :current-page="page"
        @current-change="handlePageChange" />
    </div>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="showCreate" title="新增用户" width="520px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="createForm.email" placeholder="user@example.com" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="createForm.phone" placeholder="13800138000" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roleIds" multiple placeholder="选择角色" style="width: 100%">
            <el-option v-for="r in allRoles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false" :disabled="submitting">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户弹窗 -->
    <el-dialog v-model="showEdit" title="编辑用户信息" width="480px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" label-width="90px">
        <el-form-item label="用户名">
          <el-input :value="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="user@example.com" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="editForm.phone" placeholder="13800138000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false" :disabled="submitting">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 角色分配弹窗 -->
    <el-dialog v-model="showRoles" title="分配角色" width="440px" destroy-on-close>
      <div class="role-assign-body">
        <p class="role-assign-user">用户：<strong>{{ roleTarget.username }}</strong></p>
        <el-checkbox-group v-model="selectedRoleIds" class="role-checkbox-group">
          <el-checkbox v-for="r in allRoles" :key="r.id" :value="r.id" :label="r.roleName"
            :disabled="r.status === 0" class="role-checkbox">
            <span>{{ r.roleName }}</span>
            <el-tag size="small" effect="plain" class="role-code-tag">{{ r.roleCode }}</el-tag>
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="showRoles = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="submitRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="showResetPwd" title="重置密码" width="440px" destroy-on-close>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="用户">
          <el-input :value="pwdTarget.username" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showResetPwd = false" :disabled="pwdSubmitting">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search, Refresh, ArrowDown } from '@element-plus/icons-vue'
import { userApi, roleApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'

const users = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const allRoles = ref([])
const keyword = ref('')

// ---- 新增用户 ----
const showCreate = ref(false)
const submitting = ref(false)
const createFormRef = ref(null)
const createForm = reactive({ username: '', password: '', email: '', phone: '', roleIds: [] })
const createRules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
}

// ---- 编辑用户 ----
const showEdit = ref(false)
const editFormRef = ref(null)
const editForm = reactive({ id: null, username: '', email: '', phone: '' })

// ---- 角色分配 ----
const showRoles = ref(false)
const roleTarget = reactive({ id: null, username: '' })
const selectedRoleIds = ref([])
const roleSubmitting = ref(false)

// ---- 重置密码 ----
const showResetPwd = ref(false)
const pwdTarget = reactive({ id: null, username: '' })
const pwdFormRef = ref(null)
const pwdForm = reactive({ newPassword: '', confirmPassword: '' })
const pwdSubmitting = ref(false)
const pwdRules = {
  newPassword: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    const res = await userApi.list(params)
    users.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { page.value = 1; fetchUsers() }
const handleResetSearch = () => { keyword.value = ''; page.value = 1; fetchUsers() }

const fetchAllRoles = async () => {
  try {
    const res = await roleApi.list()
    allRoles.value = res.data || []
  } catch (e) {
    ElMessage.error('获取角色列表失败：' + (e.message || '未知错误'))
  }
}

const handlePageChange = (p) => { page.value = p; fetchUsers() }

// ---- 行操作调度 ----
const handleRowAction = (cmd, row) => {
  if (cmd === 'roles') openRoleDialog(row)
  else if (cmd === 'lock') handleLock(row)
  else if (cmd === 'unlock') handleUnlock(row)
  else if (cmd === 'password') openResetPwd(row)
  else if (cmd === 'delete') handleDelete(row)
}

// ---- 新增 ----
const openAdd = () => {
  Object.assign(createForm, { username: '', password: '', email: '', phone: '', roleIds: [] })
  showCreate.value = true
}

const submitCreate = async () => {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await userApi.create({ ...createForm })
    ElMessage.success('用户创建成功')
    showCreate.value = false
    await fetchUsers()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

// ---- 编辑 ----
const openEdit = (row) => {
  Object.assign(editForm, { id: row.id, username: row.username, email: row.email || '', phone: row.phone || '' })
  showEdit.value = true
}

const submitEdit = async () => {
  submitting.value = true
  try {
    await userApi.update(editForm.id, { email: editForm.email, phone: editForm.phone })
    ElMessage.success('用户信息已更新')
    showEdit.value = false
    await fetchUsers()
  } catch (e) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    submitting.value = false
  }
}

// ---- 角色分配 ----
const openRoleDialog = async (row) => {
  Object.assign(roleTarget, { id: row.id, username: row.username })
  let userRoleCodes = row.roleCodes || []
  try {
    const res = await userApi.getRoles(row.id)
    userRoleCodes = res.data || []
  } catch { /* use list data as fallback */ }
  selectedRoleIds.value = allRoles.value
    .filter(r => userRoleCodes.includes(r.roleCode))
    .map(r => r.id)
  showRoles.value = true
}

const submitRoles = async () => {
  roleSubmitting.value = true
  try {
    const target = users.value.find(u => u.id === roleTarget.id)
    const oldRoleCodes = target?.roleCodes || []
    const oldRoleIds = allRoles.value
      .filter(r => oldRoleCodes.includes(r.roleCode))
      .map(r => r.id)
    const toAdd = selectedRoleIds.value.filter(id => !oldRoleIds.includes(id))
    const toRemove = oldRoleIds.filter(id => !selectedRoleIds.value.includes(id))
    await Promise.all([
      ...toAdd.map(id => userApi.assignRole(roleTarget.id, id)),
      ...toRemove.map(id => userApi.revokeRole(roleTarget.id, id)),
    ])
    await fetchUsers()
    ElMessage.success('角色分配已更新')
    showRoles.value = false
  } catch (e) {
    ElMessage.error(e.message || '角色分配失败')
  } finally {
    roleSubmitting.value = false
  }
}

// ---- 锁定/解锁 ----
const handleLock = (row) => {
  ElMessageBox.confirm(`确认锁定用户「${row.username}」？锁定后该用户将无法登录（15 分钟）。`, '锁定确认', {
    type: 'warning',
    confirmButtonText: '锁定',
    cancelButtonText: '取消',
  }).then(async () => {
    try {
      await userApi.lock(row.id)
      ElMessage.success('用户已锁定')
      await fetchUsers()
    } catch (e) {
      ElMessage.error(e.message || '锁定失败')
    }
  }).catch(() => {})
}

const handleUnlock = async (row) => {
  try {
    await userApi.unlock(row.id)
    ElMessage.success('用户已解锁')
    await fetchUsers()
  } catch (e) {
    ElMessage.error(e.message || '解锁失败')
  }
}

// ---- 重置密码 ----
const openResetPwd = (row) => {
  Object.assign(pwdTarget, { id: row.id, username: row.username })
  Object.assign(pwdForm, { newPassword: '', confirmPassword: '' })
  showResetPwd.value = true
}

const submitResetPwd = async () => {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  pwdSubmitting.value = true
  try {
    await userApi.resetPassword(pwdTarget.id, pwdForm.newPassword)
    ElMessage.success('密码已重置')
    showResetPwd.value = false
  } catch (e) {
    ElMessage.error(e.message || '重置失败')
  } finally {
    pwdSubmitting.value = false
  }
}

// ---- 删除 ----
const handleDelete = (row) => {
  ElMessageBox.confirm(`确认删除用户「${row.username}」？该操作不可恢复。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger',
  }).then(async () => {
    try {
      await userApi.delete(row.id)
      ElMessage.success('用户已删除')
      if (users.value.length === 1 && page.value > 1) page.value--
      await fetchUsers()
    } catch (e) {
      ElMessage.error(e.message || '删除失败')
    }
  }).catch(() => {})
}

// ---- utils ----
const isLocked = (row) => row.lockedUntil && new Date(row.lockedUntil) > new Date()
const statusType = (s) => ({ 1: 'success', 0: 'danger', 2: 'warning' }[s] || 'info')
const statusLabel = (s) => ({ 1: '正常', 0: '锁定', 2: '禁用' }[s] || '未知')
const roleTagType = (code) => {
  if (code === 'ADMIN') return 'danger'
  if (code === 'OPERATOR') return 'warning'
  return 'info'
}
const fmtTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(async () => {
  await Promise.all([fetchUsers(), fetchAllRoles()])
})
</script>

<style scoped>
.username {
  font-weight: 600;
}
.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
.role-tag {
  margin: 0;
}
.no-role {
  color: var(--iah-text-muted);
  font-size: 13px;
}
.pager {
  margin-top: 18px;
  justify-content: flex-end;
}
.role-assign-body {
  padding: 4px 0;
}
.role-assign-user {
  margin-bottom: 16px;
  color: var(--iah-text-secondary);
  font-size: 14px;
}
.role-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.role-checkbox {
  display: flex;
  align-items: center;
}
.role-code-tag {
  margin-left: 8px;
}
</style>
