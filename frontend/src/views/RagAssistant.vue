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
        <div v-if="!messages.length" class="welcome-empty">
          <EmptyState icon="🤖" title="AI 运维助手"
                      desc="输入设备运维问题，AI 将从知识库检索相关片段后回答。">
            <div class="quick-actions">
              <el-button size="small" type="primary" plain v-for="q in quickQuestions" :key="q"
                         @click="askFromQuick(q)">
                {{ q }}
              </el-button>
            </div>
          </EmptyState>
        </div>

        <div v-for="msg in messages" :key="msg.id" class="message" :class="msg.role">
          <div class="msg-meta">
            <span class="role-label">{{ msg.role === 'user' ? '你' : 'AI 助手' }}</span>
            <span class="ts">{{ formatTs(msg.timestamp) }}</span>
          </div>
          <div class="bubble" :class="{ 'bubble-error': msg.error }">
            {{ escapeText(msg.text) }}
          </div>
          <div v-if="msg.sources?.length" class="sources">
            <div class="sources-title">
              <el-icon size="12"><Document /></el-icon>
              引用片段（{{ msg.sources.length }}）
            </div>
            <div v-for="(source, i) in msg.sources" :key="i" class="source-item">
              <div class="source-meta" :title="source.source || '未知来源'">
                <span class="src-badge">[{{ i + 1 }}]</span>
                <span class="src-name">{{ (source.source || '未知来源').slice(0, 60) }}</span>
                <span v-if="source.chunkIndex != null" class="src-chunk">片段 {{ source.chunkIndex + 1 }}</span>
                <span v-if="source.score != null" class="src-score">
                  相似度 {{ (Number(source.score) * 100).toFixed(0) }}%
                </span>
              </div>
              <div class="source-text">{{ escapeText(source.content) }}</div>
            </div>
          </div>
          <div v-if="msg.error" class="msg-actions">
            <el-button size="small" type="danger" plain :icon="RefreshRight" @click="retryMessage(msg)">
              重试
            </el-button>
          </div>
        </div>

        <LoadingSpinner v-if="loading" :visible="true" text="正在检索知识库并生成回答..." />
      </div>

      <div class="input-bar">
        <el-input v-model="question" type="textarea" :rows="2" resize="none"
                  placeholder="例如：设备温度过高怎么处理？按 Enter 发送，Shift+Enter 换行"
                  :disabled="loading"
                  @keydown.enter.exact.prevent="ask" />
        <el-button type="primary" :icon="Promotion" :loading="loading" @click="ask">
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ChatDotRound, Promotion, RefreshRight, Document } from '@element-plus/icons-vue'
import { ragApi } from '../api/index.js'
import EmptyState from '../components/EmptyState.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const messages = ref([])
const question = ref('')
const loading = ref(false)
const conversationEl = ref(null)
let nextId = 1

const quickQuestions = [
  '设备温度过高怎么处理？',
  '传感器读数异常波动如何排查？',
  'PLC 通信故障常见原因？',
]

const escapeMap = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}
function escapeText(s) {
  if (s == null) return ''
  return String(s).replace(/[&<>"']/g, ch => escapeMap[ch])
}

function formatTs(ts) {
  if (!ts) return ''
  try {
    const d = new Date(ts)
    const now = new Date()
    const sameDay = d.toDateString() === now.toDateString()
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    if (sameDay) return `今天 ${hh}:${mm}`
    const MM = String(d.getMonth() + 1).padStart(2, '0')
    const DD = String(d.getDate()).padStart(2, '0')
    return `${MM}-${DD} ${hh}:${mm}`
  } catch { return '' }
}

async function scrollToBottom() {
  await nextTick()
  if (conversationEl.value) {
    conversationEl.value.scrollTop = conversationEl.value.scrollHeight
  }
}

async function ask() {
  const text = question.value.trim()
  if (!text || loading.value) return
  question.value = ''
  await sendQuestion(text)
}

async function askFromQuick(text) {
  if (loading.value) return
  await sendQuestion(text)
}

async function retryMessage(msg) {
  if (loading.value) return
  // 找到该消息之前最后一条 user 消息的文本作为问题
  const idx = messages.value.findIndex(m => m.id === msg.id)
  let userText = ''
  for (let i = idx - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') {
      userText = messages.value[i].text
      break
    }
  }
  // 删除失败的 assistant 消息，重新发送
  messages.value.splice(idx, 1)
  if (userText) {
    await sendQuestion(userText, true)
  } else {
    ElMessage.warning('未找到对应问题，请手动重新输入')
  }
}

async function sendQuestion(text, isRetry = false) {
  if (!isRetry) {
    messages.value.push({ id: nextId++, role: 'user', text, timestamp: Date.now() })
  }
  loading.value = true
  await scrollToBottom()
  try {
    const res = await ragApi.ask({ question: text })
    messages.value.push({
      id: nextId++,
      role: 'assistant',
      text: res.data?.answer || '未返回回答',
      sources: (res.data?.sources || []).map(s => ({
        ...s,
        content: s.content || '',
        source: s.source || '未知来源',
      })),
      timestamp: Date.now(),
    })
  } catch (e) {
    messages.value.push({
      id: nextId++,
      role: 'assistant',
      text: e?.message || '回答失败',
      error: true,
      timestamp: Date.now(),
    })
  } finally {
    loading.value = false
    await scrollToBottom()
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
  max-width: 960px;
  width: 100%;
  margin: 0 auto;
  padding: 0 20px 16px;
}
.welcome-empty { margin: auto 0; }
.quick-actions {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}
.conversation {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.message { display: flex; flex-direction: column; max-width: 82%; }
.message.user { align-self: flex-end; align-items: flex-end; }
.message.assistant { align-self: flex-start; align-items: flex-start; }
.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
  font-size: 12px;
  color: var(--iah-text-muted);
}
.role-label { font-weight: 600; color: var(--iah-text-secondary); }
.message.user .role-label { color: var(--iah-primary-light); }
.ts { font-family: var(--font-mono); opacity: 0.8; }
.bubble {
  padding: 11px 15px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--iah-border);
  background: var(--iah-panel);
  color: var(--iah-text);
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}
.message.user .bubble {
  background: var(--iah-primary-dark);
  border-color: var(--iah-primary-dark);
  color: var(--iah-text);
}
.bubble-error {
  border-color: var(--iah-danger);
  color: var(--iah-danger);
  background: rgba(239, 68, 68, 0.06);
}
.msg-actions { margin-top: 6px; }
.sources {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 360px;
  max-width: 100%;
}
.sources-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--iah-text-muted);
  letter-spacing: 0.02em;
}
.source-item {
  background: var(--iah-panel-hover);
  border: 1px solid var(--iah-border-soft);
  border-left: 3px solid var(--iah-primary);
  border-radius: var(--radius-md);
  padding: 9px 11px;
}
.source-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 12px;
  margin-bottom: 5px;
}
.src-badge {
  display: inline-block;
  background: var(--iah-primary-dark);
  color: var(--iah-primary-light);
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 600;
}
.src-name { color: var(--iah-primary-light); max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.src-chunk { color: var(--iah-text-secondary); }
.src-score {
  margin-left: auto;
  color: var(--iah-text-muted);
  background: rgba(59, 130, 246, 0.1);
  padding: 1px 7px;
  border-radius: 10px;
}
.source-text {
  font-size: 13px;
  color: var(--iah-text-secondary);
  line-height: 1.65;
  max-height: 150px;
  overflow-y: auto;
  padding-right: 4px;
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
  .message { max-width: 94%; }
  .sources { min-width: 0; }
  .src-name { max-width: 180px; }
}
</style>
