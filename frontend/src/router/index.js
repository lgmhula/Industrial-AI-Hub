import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/Login.vue'

// 路由懒加载：按页面分包，首屏（登录页）不再加载 ECharts 等重型依赖
const Dashboard = () => import('../views/Dashboard.vue')
const DeviceList = () => import('../views/DeviceList.vue')
const DeviceDetail = () => import('../views/DeviceDetail.vue')
const AlarmList = () => import('../views/AlarmList.vue')
const OperationLogList = () => import('../views/OperationLogList.vue')

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { guest: true } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard },
  { path: '/devices', name: 'DeviceList', component: DeviceList },
  { path: '/devices/:id', name: 'DeviceDetail', component: DeviceDetail, props: true },
  { path: '/alarms', name: 'AlarmList', component: AlarmList },
  { path: '/logs', name: 'OperationLogList', component: OperationLogList },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

// 路由守卫：未登录 → 跳转登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') {
    next('/login')
  } else if (token && to.path === '/login') {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
