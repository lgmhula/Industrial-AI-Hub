import { createRouter, createWebHashHistory } from 'vue-router'
import DeviceList from '../views/DeviceList.vue'
import DeviceDetail from '../views/DeviceDetail.vue'
import AlarmList from '../views/AlarmList.vue'
import OperationLogList from '../views/OperationLogList.vue'
import Login from '../views/Login.vue'

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { guest: true } },
  { path: '/', redirect: '/devices' },
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
    next('/devices')
  } else {
    next()
  }
})

export default router
