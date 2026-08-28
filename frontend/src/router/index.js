import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/Login.vue'

const Dashboard = () => import('../views/Dashboard.vue')
const DeviceList = () => import('../views/DeviceList.vue')
const DeviceDetail = () => import('../views/DeviceDetail.vue')
const AlarmList = () => import('../views/AlarmList.vue')
const OperationLogList = () => import('../views/OperationLogList.vue')
const UserList = () => import('../views/UserList.vue')
const RoleList = () => import('../views/RoleList.vue')
const Register = () => import('../views/Register.vue')

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { guest: true } },
  { path: '/register', name: 'Register', component: Register, meta: { guest: true } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard },
  { path: '/devices', name: 'DeviceList', component: DeviceList },
  { path: '/devices/:id', name: 'DeviceDetail', component: DeviceDetail, props: true },
  { path: '/alarms', name: 'AlarmList', component: AlarmList },
  { path: '/logs', name: 'OperationLogList', component: OperationLogList, meta: { roles: ['ADMIN'] } },
  { path: '/users', name: 'UserList', component: UserList, meta: { roles: ['ADMIN'] } },
  { path: '/roles', name: 'RoleList', component: RoleList, meta: { roles: ['ADMIN'] } },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFound.vue') },
]

const router = createRouter({ history: createWebHashHistory(), routes })

const ADMIN_ONLY = ['/users', '/roles', '/logs']

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login' && to.path !== '/register') {
    next('/login')
  } else if (token && (to.path === '/login' || to.path === '/register')) {
    next('/dashboard')
  } else if (token && ADMIN_ONLY.includes(to.path)) {
    const roles = JSON.parse(localStorage.getItem('roles') || '[]')
    if (!roles.includes('ADMIN')) {
      next('/dashboard')
    } else {
      next()
    }
  } else {
    next()
  }
})

export default router
