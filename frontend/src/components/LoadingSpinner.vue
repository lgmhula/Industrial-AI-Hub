<template>
  <div class="spinner-overlay" v-if="visible">
    <div class="spinner"></div>
    <p v-if="text" class="spinner-text">{{ text }}</p>
  </div>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  text:    { type: String, default: '' },
})
</script>

<style scoped>
.spinner-overlay {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 48px 0; gap: 12px;
}
.spinner {
  width: 36px; height: 36px;
  border: 3px solid #e5e7eb; border-top-color: #3b82f6;
  border-radius: 50%; animation: spin 0.7s linear infinite;
}
.spinner-text { font-size: 14px; color: #6b7280; margin: 0; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
