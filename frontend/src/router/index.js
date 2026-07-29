import { createRouter, createWebHashHistory } from 'vue-router'
import DeviceList from '../views/DeviceList.vue'
import DeviceDetail from '../views/DeviceDetail.vue'
import AlarmList from '../views/AlarmList.vue'
import OperationLogList from '../views/OperationLogList.vue'

const routes = [
  { path: '/', redirect: '/devices' },
  { path: '/devices', name: 'DeviceList', component: DeviceList },
  { path: '/devices/:id', name: 'DeviceDetail', component: DeviceDetail, props: true },
  { path: '/alarms', name: 'AlarmList', component: AlarmList },
  { path: '/logs', name: 'OperationLogList', component: OperationLogList },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
