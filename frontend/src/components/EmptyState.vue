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
.empty-state { text-align: center; padding: 40px 20px; color: var(--iah-text-muted); }
.empty-icon {
  width: 44px;
  height: 44px;
  line-height: 44px;
  margin: 0 auto 12px;
  background: var(--iah-panel-hover);
  border: 1px solid var(--iah-border);
  border-radius: var(--radius-lg);
  font-size: 20px;
}
.empty-title { font-size: 14px; font-weight: 600; color: var(--iah-text-secondary); margin: 0 0 4px; }
.empty-desc { font-size: 13px; margin: 0; }
</style>
