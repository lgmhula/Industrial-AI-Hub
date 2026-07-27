import { createRouter, createWebHashHistory } from 'vue-router'
import DeviceList from '../views/DeviceList.vue'
import DeviceDetail from '../views/DeviceDetail.vue'

const routes = [
  { path: '/', redirect: '/devices' },
  { path: '/devices', name: 'DeviceList', component: DeviceList },
  { path: '/devices/:id', name: 'DeviceDetail', component: DeviceDetail, props: true },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
