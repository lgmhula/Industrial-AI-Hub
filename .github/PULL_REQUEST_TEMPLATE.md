## 变更类型
<!-- 在适用的行打 x，如 [x] -->

- [ ] feat：新功能
- [ ] fix：缺陷修复
- [ ] docs：文档
- [ ] chore：工程/治理/基础设施

## 变更说明
<!-- 一句话说明本次变更解决什么问题 -->

## 关联
<!-- ADR / TECH-DEBT / runbook 坑位 / Issue 编号（如有） -->

## 自测清单（ADR 0017 §4.4 要求，全过才能合并）
- [ ] 后端：`cd backend && ./mvnw test` → 89 run / 0 fail
- [ ] 前端（如涉及）：`cd frontend && npm ci && npm run build` → 成功
- [ ] 文档同步：AGENTS.md §3 / ADR / TECH-DEBT / runbook 已同步更新
- [ ] 无明文密钥 / 无 AI·IDE 产物 / 无本地 override 混入

## 验证说明
<!-- 简述如何验证（CI 链接 / 本机命令 / 副本部署结果） -->
