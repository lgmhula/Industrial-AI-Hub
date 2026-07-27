<template>
  <div id="app-shell">
    <nav class="sidebar">
      <div class="logo">Industrial AI Hub</div>
      <router-link to="/devices" class="nav-item">设备管理</router-link>
    </nav>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f6fa; color: #1f2937; }
#app-shell { display: flex; min-height: 100vh; }
.sidebar { width: 200px; background: #1e293b; color: #e2e8f0; padding: 20px 0; flex-shrink: 0; }
.logo { padding: 0 20px 24px; font-size: 15px; font-weight: 700; color: #fff; letter-spacing: 0.5px; }
.nav-item { display: block; padding: 10px 20px; color: #94a3b8; text-decoration: none; font-size: 14px; border-left: 3px solid transparent; }
.nav-item:hover, .router-link-active { color: #fff; background: #334155; border-left-color: #3b82f6; }
.content { flex: 1; overflow-x: auto; }
</style>
