<template>
  <div id="app-shell">
    <div class="menu-toggle" @click="sidebarOpen = !sidebarOpen">
      <span></span><span></span><span></span>
    </div>
    <nav :class="['sidebar', { open: sidebarOpen }]">
      <div class="logo">Industrial AI Hub</div>
      <router-link to="/devices" class="nav-item" @click="sidebarOpen = false">设备管理</router-link>
      <router-link to="/alarms" class="nav-item" @click="sidebarOpen = false">报警管理</router-link>
      <router-link to="/logs" class="nav-item" @click="sidebarOpen = false">操作日志</router-link>
    </nav>
    <div class="sidebar-overlay" v-if="sidebarOpen" @click="sidebarOpen = false"></div>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const sidebarOpen = ref(false)
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f6fa; color: #1f2937; }
#app-shell { display: flex; min-height: 100vh; }

/* ---- sidebar ---- */
.sidebar {
  width: 200px; background: #1e293b; color: #e2e8f0; padding: 20px 0;
  flex-shrink: 0; transition: transform 0.25s ease; z-index: 50;
}
.logo { padding: 0 20px 24px; font-size: 15px; font-weight: 700; color: #fff; letter-spacing: 0.5px; }
.nav-item { display: block; padding: 10px 20px; color: #94a3b8; text-decoration: none; font-size: 14px; border-left: 3px solid transparent; }
.nav-item:hover, .router-link-active { color: #fff; background: #334155; border-left-color: #3b82f6; }
.content { flex: 1; overflow-x: auto; min-width: 0; }

/* ---- hamburger (hidden on desktop) ---- */
.menu-toggle {
  display: none; position: fixed; top: 12px; left: 12px; z-index: 60;
  width: 36px; height: 36px; padding: 8px; cursor: pointer;
  background: #1e293b; border-radius: 6px;
}
.menu-toggle span { display: block; height: 2px; background: #fff; margin: 5px 0; border-radius: 1px; }
.sidebar-overlay { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.3); z-index: 40; }

/* ---- responsive ---- */
@media (max-width: 1024px) {
  .sidebar {
    position: fixed; top: 0; left: 0; height: 100vh;
    transform: translateX(-100%); width: 200px;
  }
  .sidebar.open { transform: translateX(0); box-shadow: 4px 0 16px rgba(0,0,0,0.15); }
  .sidebar-overlay { display: block; }
  .menu-toggle { display: block; }
  .content { margin-left: 0; }
}
</style>
