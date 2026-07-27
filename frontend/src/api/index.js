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
    const msg = err.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export const deviceApi = {
  list: (params) => api.get('/devices', { params }),
  getById: (id) => api.get(`/devices/${id}`),
  create: (data) => api.post('/devices', data),
  update: (id, data) => api.put(`/devices/${id}`, data),
  delete: (id) => api.delete(`/devices/${id}`),
}

export const deviceDataApi = {
  report: (deviceId, data) => api.post(`/device-data/device/${deviceId}`, data),
  list: (deviceId, params) => api.get(`/device-data/device/${deviceId}`, { params }),
  stats: (deviceId) => api.get(`/device-data/device/${deviceId}/stats`),
  latest: (deviceId) => api.get(`/device-data/device/${deviceId}/latest`),
}

export default api
