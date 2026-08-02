import axios from 'axios'

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
  (res) => res.data,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.hash = '#/login'
    }
    const msg = err.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

// ---------- 认证 ----------
export const authApi = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
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
  stats: (deviceId) => api.get(`/device-data/device/${deviceId}/stats`),
  latest: (deviceId) => api.get(`/device-data/device/${deviceId}/latest`),
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
}

export default api
