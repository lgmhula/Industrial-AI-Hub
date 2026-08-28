import { reactive, computed } from 'vue'

const authState = reactive({
  username: localStorage.getItem('username') || '',
  roles: (() => { try { return JSON.parse(localStorage.getItem('roles') || '[]') } catch { return [] } })(),
})

export function setAuth(token, username, roles) {
  localStorage.setItem('token', token)
  localStorage.setItem('username', username)
  localStorage.setItem('roles', JSON.stringify(roles || []))
  authState.username = username
  authState.roles = roles || []
}

export function clearAuth() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('roles')
  authState.username = ''
  authState.roles = []
}

export function useAuth() {
  const isAdmin = computed(() => authState.roles.includes('ADMIN'))
  return { authState, isAdmin }
}
