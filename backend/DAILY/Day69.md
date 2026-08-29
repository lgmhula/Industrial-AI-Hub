# Day 69 — 前端工业化视觉升级（DESIGN.md 设计系统落地）

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 10
> **分支**：`feat/phase4-function-calling`（本次未直推 main；Day 66-68 日志与 TD-032/033 修复一并随本分支待 PR）
> **配套规范**：[DESIGN.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/DESIGN.md)
> **验收结果**：✅ **GO**（后端 208 tests 0 failures 0 errors 0 skipped + 前端 build 822ms 0 errors + 最大 chunk 459.35kB 消除 >500kB 告警 + 浏览器 8 页面 × 3 视口 0 console ERROR/SEVERE）

> **文档补录说明**：本任务由 codex 在 `feat/phase4-function-calling` 分支完成，交付后按 AGENTS §4.3 同步补写日志。

***

## 一、交付范围

Day 69 将 DESIGN.md 的「工业 AI 控制中心」设计语言落到全局与全部核心页面，**不修改后端 API / 数据结构**，不引入新 UI 框架 / CSS 预处理器 / 动画库。

| 模块 | 交付结果 | 证据 |
| --- | --- | --- |
| 全局设计 Token | 深色主题（`#0f1117` / `#1a1d26` / `#2a2e3a`）、品牌/功能色、字体栈（Inter + JetBrains Mono）、紧凑间距与 4/6/8px 圆角、滚动条统一 | [style.css](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/style.css) `:root` 全量 Token |
| Element Plus 主题覆盖 | 主色/成功/警告/危险/信息 + 背景/填充/文字/边框/阴影/圆角/字号全部经 CSS 变量覆盖，暗色表格、标签、输入框、弹窗、下拉、折叠面板一致 | `style.css` `--el-color-*` / `--el-bg-color-*` / `--el-text-color-*` 覆盖块 |
| 主布局 | Sidebar 展开 200px / 折叠 56px、Header 48px、实时时钟、环境 Tag、告警数徽章、移动端抽屉遮罩；登录页不再触发告警轮询 401 | [App.vue](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/App.vue) |
| 仪表盘 | KPI 卡片 100px 等高 + 状态点、告警流相对时间、ECharts 按需注册（`echarts/core` + PieChart）、暗色 tooltip/legend/label | [Dashboard.vue](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/views/Dashboard.vue) |
| 表格工业化 | AlarmList / DeviceList / OperationLogList / UserList / RoleList 统一条纹表、sticky 表头、暗色 hover、等宽数字列（ID/编码/IP/时间/手机号） | 5 个表格页 `class-name="mono"` 补全 |
| AI 卡片 | AI 健康诊断 + AI 设备问答统一 `ai-card` 样式（MagicStick/ChatDotRound 图标、标题区、placeholder、meta tag 色板），无重复残留 | [DeviceDetail.vue](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/views/DeviceDetail.vue) |
| 认证页 | Login/Register 深色面板 + 网格背景 + 8px 圆角 + 非纯白文本；Logo 色板对齐 DESIGN.md | [Login.vue](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/views/Login.vue) / [Register.vue](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/views/Register.vue) |
| 空状态/加载 | EmptyState 图标容器 + LoadingSpinner 使用设计 Token，暗色适配 | [EmptyState.vue](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/components/EmptyState.vue) / [LoadingSpinner.vue](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/components/LoadingSpinner.vue) |

***

## 二、设计 Token 与 Element Plus 覆盖

```
// 主题基调：近黑冷色 + 品牌蓝，数据优先，非纯白文本
--iah-bg:            #0f1117
--iah-panel:         #1a1d26
--iah-panel-hover:   #242831
--iah-border:        #2a2e3a
--iah-text:          #e8eaed
--iah-text-secondary:#9aa0ac
--iah-text-muted:    #5a6070
--iah-primary:       #3b82f6   --iah-primary-light: #60a5fa
--iah-success:       #22c55e   --iah-warning: #f59e0b
--iah-danger:        #ef4444   --iah-offline: #6b7280
--radius-sm/md/lg:   4px / 6px / 8px   （卡片最大 8px，无高圆角）
--font-mono:         JetBrains Mono → 数值列、编码、IP、时间戳
```

Element Plus 侧通过 CSS 变量覆盖（而非 JS 运行时主题包）完成深色接管：`--el-color-primary-*`、`--el-bg-color*`、`--el-fill-color*`、`--el-text-color*`、`--el-border-color*`、`--el-border-radius-base` 等全部落到 `:root`，组件级不再散落硬编码色值。

***

## 三、关键交互与边界修复

1. **登录页 401 根治**：App.vue 原先 onMounted 即轮询 `/api/alarms/status/0`，登录页无 token 时产生 401 控制台错误；改为 `watch(route.path)` + `localStorage token` 守卫，登录后才启动告警数轮询（30s 一次），登出/登录页停止轮询。
2. **非管理员仪表盘不再请求管理员接口**：`operation-logs/recent` 为 ADMIN-only；Dashboard 改为 `isAdmin.value ? listRecent() : Promise.resolve({data: []})`，operator 登录 0 控制台错误，今日操作数对非管理员显示 0（语义与原 catch 一致，但不再产生 403 资源错误）。
3. **构建瘦身**：Dashboard ECharts 改为按需注册（`echarts/core` + `PieChart` + Tooltip/Legend + CanvasRenderer），最大 chunk 由 Day67/68 的 >500kB 降至 `installCanvasRenderer` **459.35kB**，>500kB 告警消除；Views 保持既有动态 import。

***

## 四、验收结果

### 4.1 后端回归

```
./mvnw test（backend/）
Tests run: 208, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS（8.140s）
```

TD-032 / TD-033 修复随本分支同步回归：OperationLogAspectTest 2/2、DeviceStatusAgentServiceTest 6/6 全绿。

### 4.2 前端构建

```
npm run build（frontend/）
✓ built in 822ms
0 errors，0 warning
最大 chunk：installCanvasRenderer 459.35 kB（gzip 155.24 kB）
```

### 4.3 浏览器验收（Playwright，admin 登录）

| 页面 | 1440×900 | 1280×720 | 390×844 | Console |
| --- | :-: | :-: | :-: | :-: |
| Login | ✅ | ✅ | ✅ | 0 ERROR |
| Dashboard | ✅ | ✅ | ✅ | 0 ERROR |
| AlarmList（含 AI 摘要按钮） | ✅ | ✅ | ✅ | 0 ERROR |
| DeviceList | ✅ | ✅ | ✅ | 0 ERROR |
| DeviceDetail（AI 诊断/问答卡） | ✅ | ✅ | ✅ | 0 ERROR |
| OperationLogList | ✅ | ✅ | ✅ | 0 ERROR |
| UserList | ✅ | ✅ | ✅ | 0 ERROR |
| RoleList | ✅ | ✅ | ✅ | 0 ERROR |

截图产物位于项目根 `.playwright-cli/`（已 gitignore，不随代码提交）。移动端侧栏折叠为图标菜单、表格容器可横向滚动，未发现元素重叠或纯白/高圆角残留。

***

## 五、技术债务状态

| ID | 说明 | Day69 状态 |
| --- | --- | --- |
| TD-028 | AI 端点无操作日志 | ✅ 已解决（Day67，V9 + @OperationLog） |
| TD-031 | 前端 chunk >500kB（Dashboard ECharts） | ✅ 已解决（Day69 ECharts 按需注册，最大 chunk 459.35kB） |
| TD-032 | OperationLogAspect 失败 `{ret}` 输出 "null" | ✅ 已修复（本分支，异常消息替换 "null"） |
| TD-033 | DeviceStatusAgentService 503 掩盖 404/403 | ✅ 已修复（本分支，requireDevice → assertSiteAccess → ensureAvailable） |

***

## 六、明日计划（Day 70）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | Week 10 周复盘 + Phase 4 AI 模块学习笔记（DeepSeek API → Spring AI ChatClient → Function Calling 三段演进） |
| ★★☆ | 整理 `feat/phase4-function-calling` 分支：Day 66-69 日志 + TD-032/033 + 视觉升级合并评审，准备 PR |
| ★☆☆ | AI 功能端到端预演：配置 DeepSeek Key 后验证摘要/诊断/设备问答三条路径 |
