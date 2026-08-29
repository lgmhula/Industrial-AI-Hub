import { createApp } from 'vue'
import {
  Monitor,
  Odometer,
  Cpu,
  Bell,
  Document,
  Fold,
  Expand,
  ArrowDown,
  ArrowLeft,
  SwitchButton,
  User,
  UserFilled,
  Setting,
  Lock,
  Plus,
  Search,
  Refresh,
  ChatDotRound,
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router/index.js'
import './style.css'

const app = createApp(App)

const icons = {
  Monitor,
  Odometer,
  Cpu,
  Bell,
  Document,
  Fold,
  Expand,
  ArrowDown,
  ArrowLeft,
  SwitchButton,
  User,
  UserFilled,
  Setting,
  Lock,
  Plus,
  Search,
  Refresh,
  ChatDotRound,
}
for (const [name, component] of Object.entries(icons)) {
  app.component(name, component)
}

app.use(router)
app.mount('#app')
