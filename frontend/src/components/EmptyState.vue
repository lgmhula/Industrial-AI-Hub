<template>
  <div class="empty-state">
    <div class="empty-icon">{{ icon }}</div>
    <p class="empty-title">{{ title }}</p>
    <p class="empty-desc" v-if="desc">{{ desc }}</p>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  icon:  { type: String, default: '📭' },
  title: { type: String, default: '暂无数据' },
  desc:  { type: String, default: '' },
})
</script>

<style scoped>
.empty-state { text-align: center; padding: 48px 20px; color: #9ca3af; }
.empty-icon { font-size: 40px; margin-bottom: 12px; }
.empty-title { font-size: 15px; font-weight: 500; color: #6b7280; margin: 0 0 4px; }
.empty-desc { font-size: 13px; margin: 0; }
</style>
