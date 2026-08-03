import { createApp } from 'vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router/index.js'
import './style.css'

// Element Plus 组件与样式由 unplugin 按需自动导入（见 vite.config.js），
// 此处仅全局注册图标（Dashboard 等处通过动态组件名使用）
const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(router)
app.mount('#app')
