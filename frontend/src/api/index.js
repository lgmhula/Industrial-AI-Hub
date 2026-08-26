import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuth } from '../composables/useAuth.js'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code === 'number' && body.code !== 200) {
      if (body.code === 401) {
        clearAuth()
        ElMessage.warning('登录已过期，请重新登录')
        setTimeout(() => { window.location.hash = '#/login' }, 500)
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => {
    if (err.response?.status === 401) {
      clearAuth()
      ElMessage.warning('登录已过期，请重新登录')
      setTimeout(() => { window.location.hash = '#/login' }, 500)
    }
    const msg = err.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

// ---------- 认证 ----------
export const authApi = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
  logout: () => api.post('/auth/logout'),
}

// ---------- 设备 ----------
export const deviceApi = {
  list: (params) => api.get('/devices', { params }),
  getById: (id) => api.get(`/devices/${id}`),
  create: (data) => api.post('/devices', data),
  update: (id, data) => api.put(`/devices/${id}`, data),
  delete: (id) => api.delete(`/devices/${id}`),
}

// ---------- 设备数据 ----------
export const deviceDataApi = {
  report: (deviceId, data) => api.post(`/device-data/device/${deviceId}`, data),
  list: (deviceId, params) => api.get(`/device-data/device/${deviceId}`, { params }),
  listByTimeRange: (deviceId, params) => api.get(`/device-data/device/${deviceId}/range`, { params }),
  stats: (deviceId, dataType, params) => api.get(`/device-data/device/${deviceId}/stats`, { params: { dataType, ...params } }),
  latest: (deviceId, dataType) => api.get(`/device-data/device/${deviceId}/latest`, { params: { dataType } }),
}

// ---------- 报警 ----------
export const alarmApi = {
  list: (params) => api.get('/alarms', { params }),
  listByDevice: (deviceId, params) => api.get(`/alarms/device/${deviceId}`, { params }),
  listByStatus: (status, params) => api.get(`/alarms/status/${status}`, { params }),
  acknowledge: (id) => api.put(`/alarms/${id}/acknowledge`),
  resolve: (id) => api.put(`/alarms/${id}/resolve`),
}

// ---------- 操作日志 ----------
export const operationLogApi = {
  list: (params) => api.get('/operation-logs', { params }),
  listByUser: (userId, params) => api.get(`/operation-logs/user/${userId}`, { params }),
  listRecent: () => api.get('/operation-logs/recent'),
}

// ---------- 角色 ----------
export const roleApi = {
  list: () => api.get('/roles'),
  getById: (id) => api.get(`/roles/${id}`),
  create: (data) => api.post('/roles', data),
  update: (id, data) => api.put(`/roles/${id}`, data),
  delete: (id) => api.delete(`/roles/${id}`),
  toggleStatus: (id) => api.put(`/roles/${id}/status`),
}

// ---------- 用户 ----------
export const userApi = {
  list: (params) => api.get('/users', { params }),
  getById: (id) => api.get(`/users/${id}`),
  create: (data) => api.post('/users', data),
  update: (id, data) => api.put(`/users/${id}`, data),
  delete: (id) => api.delete(`/users/${id}`),
  toggleStatus: (id) => api.put(`/users/${id}/status`),
  lock: (id) => api.put(`/users/${id}/lock`),
  unlock: (id) => api.put(`/users/${id}/unlock`),
  resetPassword: (id, newPassword) => api.put(`/users/${id}/password`, { newPassword }),
  assignRole: (id, roleId) => api.post(`/users/${id}/roles/${roleId}`),
  revokeRole: (id, roleId) => api.delete(`/users/${id}/roles/${roleId}`),
  getRoles: (id) => api.get(`/users/${id}/roles`),
}

// ---------- 站点 ----------
export const siteApi = {
  list: () => api.get('/sites'),
}

export default api
