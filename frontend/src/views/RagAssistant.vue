<template>
  <div class="assistant-page">
    <div class="page-header">
      <div class="header-title">
        <el-icon :size="20" class="title-icon"><ChatDotRound /></el-icon>
        <div>
          <div class="page-title">AI 运维助手</div>
          <div class="page-subtitle">基于设备手册与运维知识库回答</div>
        </div>
      </div>
    </div>

    <div class="assistant-body">
      <div ref="conversationEl" class="conversation">
        <EmptyState v-if="!messages.length" icon="🤖" title="AI 运维助手"
                    desc="输入设备运维问题，AI 将从知识库检索相关片段后回答。" />

        <div v-for="msg in messages" :key="msg.id" class="message" :class="msg.role">
          <div class="bubble" :class="{ 'bubble-error': msg.error }">{{ msg.text }}</div>
          <div v-if="msg.sources?.length" class="sources">
            <div class="sources-title">引用片段</div>
            <div v-for="(source, i) in msg.sources" :key="i" class="source-item">
              <div class="source-meta">
                {{ source.source || '未知来源' }}
                <span v-if="source.chunkIndex != null"> · 片段 {{ source.chunkIndex + 1 }}</span>
              </div>
              <div class="source-text">{{ source.content }}</div>
            </div>
          </div>
        </div>

        <LoadingSpinner v-if="loading" :visible="true" text="正在检索知识库..." />
      </div>

      <div class="input-bar">
        <el-input v-model="question" type="textarea" :rows="2" resize="none"
                  placeholder="例如：设备温度过高怎么处理？"
                  :disabled="loading" @keydown.enter.exact.prevent="ask" />
        <el-button type="primary" :icon="Promotion" :loading="loading" @click="ask">
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ChatDotRound, Promotion } from '@element-plus/icons-vue'
import { ragApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const messages = ref([])
const question = ref('')
const loading = ref(false)
const conversationEl = ref(null)
let nextId = 1

async function ask() {
  const text = question.value.trim()
  if (!text || loading.value) return

  messages.value.push({ id: nextId++, role: 'user', text })
  question.value = ''
  loading.value = true
  try {
    const res = await ragApi.ask({ question: text })
    messages.value.push({
      id: nextId++,
      role: 'assistant',
      text: res.data?.answer || '未返回回答',
      sources: res.data?.sources || [],
    })
  } catch (e) {
    messages.value.push({
      id: nextId++,
      role: 'assistant',
      text: e.message || '回答失败',
      error: true,
    })
  } finally {
    loading.value = false
    await nextTick()
    if (conversationEl.value) {
      conversationEl.value.scrollTop = conversationEl.value.scrollHeight
    }
  }
}
</script>

<style scoped>
.assistant-page {
  height: calc(100vh - var(--iah-header-h));
  display: flex;
  flex-direction: column;
}
.page-header {
  height: 64px;
  flex: 0 0 64px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  background: var(--iah-panel);
  border-bottom: 1px solid var(--iah-border);
}
.header-title { display: flex; align-items: center; gap: 12px; }
.title-icon { color: var(--iah-primary-light); }
.page-title { font-size: 16px; font-weight: 700; color: var(--iah-text); }
.page-subtitle { font-size: 12px; color: var(--iah-text-muted); margin-top: 2px; }
.assistant-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  max-width: 920px;
  width: 100%;
  margin: 0 auto;
  padding: 0 20px 16px;
}
.conversation {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.message { display: flex; flex-direction: column; max-width: 78%; }
.message.user { align-self: flex-end; align-items: flex-end; }
.message.assistant { align-self: flex-start; align-items: flex-start; }
.bubble {
  padding: 10px 14px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--iah-border);
  background: var(--iah-panel);
  color: var(--iah-text);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.message.user .bubble {
  background: var(--iah-primary-dark);
  border-color: var(--iah-primary-dark);
  color: var(--iah-text);
}
.bubble-error { border-color: var(--iah-danger); color: var(--iah-danger); }
.sources {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sources-title {
  font-size: 12px;
  color: var(--iah-text-muted);
  letter-spacing: 0.02em;
}
.source-item {
  background: var(--iah-panel-hover);
  border: 1px solid var(--iah-border-soft);
  border-radius: var(--radius-md);
  padding: 8px 10px;
}
.source-meta {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--iah-primary-light);
  margin-bottom: 4px;
}
.source-text {
  font-size: 13px;
  color: var(--iah-text-secondary);
  line-height: 1.6;
}
.input-bar {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--iah-border);
}
:deep(.input-bar .el-textarea__inner) {
  background: var(--iah-panel);
  color: var(--iah-text);
  border-color: var(--iah-border);
}
.input-bar .el-button { height: 40px; }
@media (max-width: 768px) {
  .page-header { padding: 0 14px; }
  .assistant-body { padding: 0 14px 12px; }
  .message { max-width: 92%; }
}
</style>
