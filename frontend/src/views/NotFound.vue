<template>
  <div class="not-found">
    <div class="content">
      <h1 class="code">404</h1>
      <p class="msg">页面不存在</p>
      <p class="desc">您访问的页面可能已被移除或地址有误</p>
      <el-button type="primary" @click="$router.push('/dashboard')">返回首页</el-button>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;
}
.code {
  font-size: 80px;
  font-weight: 800;
  color: var(--iah-primary);
  margin: 0;
  line-height: 1;
}
.msg {
  font-size: 20px;
  font-weight: 600;
  color: var(--iah-text);
  margin: 16px 0 8px;
}
.desc {
  color: var(--iah-text-muted);
  font-size: 14px;
  margin-bottom: 24px;
}
</style>
