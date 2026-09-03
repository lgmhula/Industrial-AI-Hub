<template>
  <div class="report-page">
    <div class="page-header">
      <div class="header-title">
        <el-icon :size="20" class="title-icon"><Bell /></el-icon>
        <div>
          <div class="page-title">AI 巡检日报</div>
          <div class="page-subtitle">实时推送 · 自动重连 · 按日去重 · AI 自动报警高亮</div>
        </div>
      </div>
      <div class="conn-status" :class="connState">
        <span class="dot" />
        <span class="label">{{ connLabel }}</span>
      </div>
    </div>

    <div class="report-body">
      <div ref="listEl" class="report-list">
        <EmptyState
          v-if="!reports.length && connState === 'connected'"
          icon="📡"
          title="等待推送"
          desc="已连接 SSE 通道，等待首个巡检日报推送……"
        />
        <EmptyState
          v-else-if="!reports.length && connState === 'connecting'"
          icon="🔌"
          title="正在连接"
          desc="正在建立 SSE 通道……"
        />
        <EmptyState
          v-else-if="!reports.length && connState === 'reconnecting'"
          icon="⚠️"
          title="连接断开"
          desc="浏览器正在自动重连，无需手动操作……"
        />

        <div v-for="r in reports" :key="r.reportDate" class="report-card">
          <div class="card-header">
            <div class="card-date">
              <el-icon><Calendar /></el-icon>
              <span>{{ r.reportDate }}</span>
            </div>
            <div class="card-meta">
              <el-tag size="small" type="info" effect="plain" class="meta-tag">
                工具 {{ r.toolRounds }} 轮
              </el-tag>
              <el-tag size="small" type="success" effect="plain" class="meta-tag">
                {{ r.toolCalls }} 次调用
              </el-tag>
              <el-tag size="small" type="warning" effect="plain" class="meta-tag">
                设备 {{ r.deviceCount }}
              </el-tag>
              <el-tag v-if="r.alarmCount" size="small" type="danger" effect="plain" class="meta-tag">
                告警 {{ r.alarmCount }}
              </el-tag>
              <!-- Day 87 新增：AI 结构化异常徽章 + 自动生成报警徽章 -->
              <el-tag
                v-if="r.issueCount"
                size="small"
                :type="issueSeverityTagType(r.issuesMaxSeverity)"
                effect="light"
                class="meta-tag ai-tag"
              >
                <el-icon class="tag-icon"><Warning /></el-icon>
                异常 {{ r.issueCount }}
              </el-tag>
              <el-tag
                v-if="r.autoAlarmCount"
                size="small"
                type="danger"
                effect="dark"
                class="meta-tag ai-tag"
              >
                <el-icon class="tag-icon"><Bell /></el-icon>
                AI 自动报警 {{ r.autoAlarmCount }}
              </el-tag>
              <el-tag v-if="r.truncated" size="small" type="danger" effect="dark" class="meta-tag">
                已截断
              </el-tag>
              <span class="card-time">{{ formatTime(r.generatedAt) }}</span>
            </div>
          </div>

          <!-- Day 87 新增：AI 结构化异常折叠卡（不截断，可展开逐条定位设备/级别/类型/描述） -->
          <el-collapse
            v-if="r.issueCount"
            v-model="r._openIssues"
            class="issue-collapse"
          >
            <el-collapse-item :name="'issues-' + r.reportDate">
              <template #title>
                <span class="issue-title">
                  <el-icon class="issue-icon"><WarningFilled /></el-icon>
                  AI 识别异常（{{ r.issueCount }}）· 点击展开逐条查看
                </span>
              </template>
              <div class="issue-list">
                <div v-for="(it, idx) in r.detectedIssues" :key="idx" class="issue-item">
                  <div class="issue-head">
                    <el-tag
                      size="small"
                      class="issue-severity"
                      :type="severityTagType(it.severity)"
                      effect="dark"
                    >
                      {{ severityLabel(it.severity) }}
                    </el-tag>
                    <span class="issue-device">
                      <el-icon><Cpu /></el-icon>
                      {{ it.deviceCode || ('#设备 ' + it.deviceId) }}
                    </span>
                    <el-tag size="small" effect="plain" type="info" class="issue-type">
                      {{ it.alarmType }}
                    </el-tag>
                    <span class="issue-time" v-if="it.occurredAt">{{ formatTime(it.occurredAt) }}</span>
                  </div>
                  <div class="issue-desc">{{ escapeText(it.description) }}</div>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>

          <div class="card-report">{{ escapeText(r.report) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import {
  Bell, Calendar, Warning, WarningFilled, Cpu,
} from '@element-plus/icons-vue'
import EmptyState from '../components/EmptyState.vue'
import { escapeText } from '../utils/escapeHtml.js'

const reports = ref([])
const connState = ref('connecting') // connecting | connected | reconnecting
const listEl = ref(null)
let source = null

const connLabel = computed(() => ({
  connecting: '连接中',
  connected: '已连接',
  reconnecting: '重连中',
}[connState.value] || '未知'))

// Day 87 工具方法：AI 异常 severity → Element tag type
function severityTagType(severity) {
  const n = Number(severity) || 0
  if (n >= 3) return 'danger'
  if (n === 2) return 'warning'
  if (n === 1) return 'info'
  return 'info'
}
function issueSeverityTagType(maxSev) {
  return severityTagType(maxSev)
}
function severityLabel(severity) {
  const n = Number(severity) || 0
  if (n >= 3) return '紧急'
  if (n === 2) return '重要'
  if (n === 1) return '一般'
  return '一般'
}

function normalizeMessage(msg) {
  const issues = Array.isArray(msg.detectedIssues) ? msg.detectedIssues : []
  // issuesMaxSeverity：取 N 条异常里最高严重性，用于卡片头部异常徽章颜色
  let maxSev = 0
  for (const it of issues) {
    const s = Number(it.severity) || 0
    if (s > maxSev) maxSev = s
  }
  return {
    reportDate: msg.reportDate,
    report: msg.report || '',
    toolRounds: msg.toolRounds || 0,
    toolCalls: msg.toolCalls || 0,
    deviceCount: msg.deviceCount || 0,
    alarmCount: msg.alarmCount || 0,
    truncated: !!msg.truncated,
    generatedAt: msg.generatedAt || null,
    // Day 87 新字段（后端 InspectionReportMessage Day 87 扩）
    autoAlarmCount: Number(msg.autoAlarmCount) || 0,
    detectedIssues: issues,
    issueCount: issues.length,
    issuesMaxSeverity: maxSev,
    // UI 状态：折叠面板展开 key（默认不展开，不滚动时不抢占卡片正文位置）
    _openIssues: [],
  }
}

function connect() {
  const token = localStorage.getItem('token')
  if (!token) {
    connState.value = 'reconnecting'
    return
  }
  connState.value = 'connecting'
  // 浏览器原生 EventSource 不支持自定义 header，JWT 走 ?token= query fallback
  // （后端 JwtAuthFilter 仅在 /api/push/ 路径下支持该 fallback，ADR 0031 §5.2）
  source = new EventSource(`/api/push/inspection?token=${encodeURIComponent(token)}`)

  source.onopen = () => { connState.value = 'connected' }

  // 后端 SseEmitter.event().name("inspection-report").data(message) 推送
  // event 字段为 "inspection-report"，addEventListener 监听该具名事件
  source.addEventListener('inspection-report', (ev) => {
    try {
      const raw = JSON.parse(ev.data)
      const msg = normalizeMessage(raw)
      // ADR 0031 §6 重复推送策略：前端按 reportDate 去重渲染
      // （Consumer 已用 Redis SETNX 跨实例幂等，前端二次去重作兜底）
      if (reports.value.some(r => r.reportDate === msg.reportDate)) {
        return
      }
      reports.value.unshift(msg)
      // 保持最多 50 条，超出按时间倒序丢弃最旧
      if (reports.value.length > 50) {
        reports.value = reports.value.slice(0, 50)
      }
      nextTick(scrollToTop)
    } catch (e) {
      // SSE 数据解析失败 → 静默丢弃，避免 UI 闪退（与 AI JSON 解析失败降级语义一致）
      console.warn('巡检日报 SSE 数据解析失败', e)
    }
  })

  // EventSource 无 onerror 时浏览器原生自动重连（指数退避）；
  // 这里仅切换 UI 状态 + 保留 source 让浏览器自动重试
  source.onerror = () => {
    connState.value = 'reconnecting'
    // 若 readyState=CLOSED（2）则浏览器不会自动重连，需手动清理 + 延迟重试
    if (source.readyState === 2) {
      source.close()
      source = null
      setTimeout(connect, 3000)
    }
  }
}

function scrollToTop() {
  if (listEl.value) listEl.value.scrollTop = 0
}

function formatTime(iso) {
  if (!iso) return ''
  try {
    const d = new Date(String(iso).replace(' ', 'T'))
    if (Number.isNaN(d.getTime())) return iso
    // 带日期：跨天巡检（00:xx）更清晰，不只是 HH:mm:ss
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

onMounted(() => { connect() })
onUnmounted(() => {
  if (source) {
    source.close()
    source = null
  }
})
</script>

<style scoped>
.report-page {
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
  justify-content: space-between;
  background: var(--iah-panel);
  border-bottom: 1px solid var(--iah-border);
}
.header-title { display: flex; align-items: center; gap: 12px; }
.title-icon { color: var(--iah-primary-light); }
.page-title { font-size: 16px; font-weight: 700; color: var(--iah-text); }
.page-subtitle { font-size: 12px; color: var(--iah-text-muted); margin-top: 2px; }
.conn-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--iah-border);
  background: var(--iah-panel-hover);
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--iah-text-secondary);
}
.conn-status .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--iah-text-muted);
}
.conn-status.connecting .dot {
  background: var(--iah-warning, #e6a23c);
  animation: pulse 1.2s ease-in-out infinite;
}
.conn-status.connected .dot { background: var(--iah-success, #67c23a); }
.conn-status.reconnecting .dot {
  background: var(--iah-danger, #f56c6c);
  animation: pulse 0.8s ease-in-out infinite;
}
.conn-status.connecting { color: var(--iah-warning, #e6a23c); }
.conn-status.connected { color: var(--iah-success, #67c23a); }
.conn-status.reconnecting { color: var(--iah-danger, #f56c6c); }
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.8); }
}
.report-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 20px;
}
.report-list {
  max-width: 960px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.report-card {
  background: var(--iah-panel);
  border: 1px solid var(--iah-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.card-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--iah-border-soft);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  background: var(--iah-panel-hover);
}
.card-date {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 700;
  color: var(--iah-primary-light);
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.meta-tag { font-family: var(--font-mono); display: inline-flex; align-items: center; gap: 4px; }
.ai-tag { border-radius: 10px; letter-spacing: 0.2px; }
.tag-icon { font-size: 12px; }
.card-time {
  font-size: 12px;
  color: var(--iah-text-muted);
  margin-left: 4px;
  font-family: var(--font-mono);
}

/* Day 87 新增：AI 异常折叠卡 */
.issue-collapse {
  border: none;
  border-bottom: 1px solid var(--iah-border-soft);
  background: transparent;
  --el-collapse-border-color: transparent;
}
.issue-collapse :deep(.el-collapse-item__header) {
  padding: 10px 16px;
  background: linear-gradient(90deg, rgba(230,162,60,0.06), transparent 70%);
  border-bottom: none;
  color: var(--iah-text);
  height: auto;
  line-height: 1.5;
}
.issue-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}
.issue-title {
  display: inline-flex; align-items: center; gap: 8px;
  font-weight: 600; font-size: 13px;
}
.issue-icon { color: var(--iah-warning, #e6a23c); }
.issue-list {
  display: flex; flex-direction: column; gap: 10px;
  padding: 12px 16px 14px;
  background: var(--iah-panel-hover);
}
.issue-item {
  border: 1px solid var(--iah-border);
  background: var(--iah-panel);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}
.issue-head {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-bottom: 6px;
}
.issue-severity {
  letter-spacing: 0.5px;
  font-weight: 600;
}
.issue-device {
  display: inline-flex; align-items: center; gap: 4px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--iah-text-secondary);
}
.issue-type {
  font-family: var(--font-mono);
}
.issue-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--iah-text-muted);
  font-family: var(--font-mono);
}
.issue-desc {
  font-size: 13px;
  line-height: 1.7;
  color: var(--iah-text);
  white-space: pre-wrap;
  word-break: break-word;
  padding: 4px 2px 0;
  border-top: 1px dashed var(--iah-border-soft);
  margin-top: 4px;
}

.card-report {
  padding: 14px 16px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--iah-text);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono, monospace);
}
@media (max-width: 768px) {
  .page-header { padding: 0 14px; }
  .report-body { padding: 12px 14px; }
  .card-header { padding: 10px 12px; }
  .card-report { padding: 10px 12px; font-size: 12px; }
  .conn-status .label { display: none; }
  .issue-time { margin-left: 0; width: 100%; }
}
</style>
