<template>
  <Teleport to="body">
    <Transition name="toast">
      <div v-if="visible" :class="['toast', type]">{{ message }}</div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'

const visible = ref(false)
const message = ref('')
const type = ref('info') // info | success | error
let timer = null

const show = (msg, t = 'info', duration = 3000) => {
  clearTimeout(timer)
  message.value = msg
  type.value = t
  visible.value = true
  timer = setTimeout(() => { visible.value = false }, duration)
}

defineExpose({ show })
</script>

<style scoped>
.toast {
  position: fixed; bottom: 24px; right: 24px; z-index: 999;
  padding: 12px 24px; border-radius: 8px; font-size: 14px; font-weight: 500;
  color: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  max-width: 360px; word-break: break-word;
}
.toast.info    { background: #1f2937; }
.toast.success { background: #16a34a; }
.toast.error   { background: #dc2626; }

.toast-enter-active, .toast-leave-active { transition: all 0.3s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(12px); }
</style>
