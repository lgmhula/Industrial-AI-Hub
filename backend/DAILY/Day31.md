# Day 31 — 2026-07-27

## 今日目标
- [x] 前端项目脚手架：Vue 3 + Vite + vue-router + axios + ECharts
- [x] 设备管理列表页（搜索、状态筛选、分页）
- [x] 设备新增/编辑弹窗
- [x] 设备详情页 + ECharts 温度/压力趋势图
- [x] 统计卡片（avg/min/max/count）
- [x] 最近采集数据表格
- [x] npm run build 通过
- [ ] Git commit

## 技术选型

| 组件 | 版本 | 用途 |
|------|------|------|
| Vue | 3.x | 前端框架 |
| Vite | 8.x | 构建工具 |
| Vue Router | 4.x | 路由 |
| Axios | latest | HTTP 客户端 |
| ECharts | latest | 数据可视化 |
| vue-echarts | latest | Vue 3 ECharts 封装 |

## 新增文件

```
frontend/
├── src/
│   ├── main.js              # 入口，挂载 router
│   ├── App.vue              # 侧边栏布局
│   ├── router/index.js      # 路由：/devices, /devices/:id
│   ├── api/index.js         # axios 实例 + 拦截器 + deviceApi/deviceDataApi
│   ├── views/
│   │   ├── DeviceList.vue   # 设备管理列表页
│   │   └── DeviceDetail.vue # 设备详情 + ECharts 图表
│   └── style.css            # 全局样式
├── index.html
├── vite.config.js           # proxy /api → 127.0.0.1:8080
└── package.json
```

## 页面功能

### 设备列表页 `/devices`
- 搜索：设备名称/编码关键字
- 筛选：状态下拉（全部/在线/离线/维护中）
- 分页：上一页/下一页，显示总数
- 新增按钮 → 弹窗表单（名称、编码、类型、状态、IP、端口、位置）
- 编辑按钮 → 同上，预填数据
- 删除按钮 → 确认后逻辑删除
- 点击行 → 跳转设备详情

### 设备详情页 `/devices/:id`
- 设备基本信息网格
- 统计卡片：数据条数 / 平均值 / 最小值 / 最大值
- ECharts 温度趋势折线图（蓝色）
- ECharts 压力趋势折线图（红色）
- 最近 10 条采集数据表格

## 构建验证

```
npm run build → ✓ 674 modules transformed
dist/index.html            0.45 kB
dist/assets/index.css      9.52 kB
dist/assets/index.js     669.13 kB (含 ECharts)
```

## 注意事项

- ECharts 按需引入（LineChart / Grid / Tooltip / Legend / CanvasRenderer）
- API 层通过 vite proxy 转发到后端 8080 端口
- 前端开发启动：`cd frontend && npm run dev`（端口 5173）

---

## 明日计划
- Day 32: 报警模块 — 规则定义 + 上报检测 + 报警列表 + 处理
