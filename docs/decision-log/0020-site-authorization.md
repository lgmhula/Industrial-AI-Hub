# Decision 0020: 站点资源授权模型（Site-Based Resource Authorization）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-23 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | P1-01 水平越权审计 / `V4__add_site_scoping.sql` / `SiteAccessService` / AGENTS.md §7 |

---

## 1. 背景

P1-01 Horizontal Authorization 审计确认：系统为「纯 RBAC、零资源归属」——`device/alarm/device_data`
无 owner/tenant 维度，任何已认证 VIEWER 可通过改 ID 读取任意设备（BOLA/IDOR 系统性缺口）。
「有 JWT ≠ 有授权」：JWT 的 `userId` 仅用于登录与自删守卫，从未进入资源访问判定。

## 2. 决策

| 项 | 决策 |
|----|------|
| 资源作用域 | **Site（站点）**：`User → user_site → Site → Device → Alarm/DeviceData`。设备归属站点，告警/数据经 `device_id` 继承站点 |
| 数据模型 | `site` 表 + `user_site(user_id, site_id, role_id)` + `device.site_id`（V4 迁移；含默认站点 DEFAULT 与设备回填） |
| 授权规则 | 全局 ADMIN（user_role 含 ADMIN）→ 全站点放行；普通用户 → 依 `user_site` 站点内角色按 `RoleEnum.isAtLeast` 判定；无成员 → 403 |
| 角色语义 | 资源访问只认「站点内角色」；全局 OPERATOR/VIEWER 不再赋予资源访问（历史兼容，不删除） |
| 执行位置 | 列表接口 SQL 站点过滤（防数据量泄露）；单对象接口 Service 前置断言（`SiteAccessService.assertSiteAccess`）；Controller 仅显式传 `userId` |
| 缓存安全 | `@Cacheable` key 含 `userId`（避免缓存命中绕过站点授权）；写操作 `allEntries=true` 全量失效（避免他用户读到陈旧数据） |
| 设备创建 | `DeviceDTO.siteId` 可选：缺省取创建者唯一站点；管理员缺省归默认站点；创建者需该站点 OPERATOR+ |
| 站点语义变更 | 设备 `site_id` 不可经 update 变更（create 时确定）；删除设备仍全局 ADMIN 专属 |

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|------------|
| `device.owner_id`（个人归属） | 设备归"单个用户"不符工业共管现实；VIEWER 共享只读无解；扩展性差 |
| Tenant RBAC（多租户） | SaaS 级复杂度，当前单组织/单部署，成本收益比最差 |
| JWT 内嵌 siteRoles | 角色变更需 token 刷新才生效，与 P1「token 吊销」议题耦合；运行时查询（user_site JOIN role，Redis 可缓存）即时生效、token 不变大 |
| 前端隐藏按钮代替后端强制 | 前端不是安全边界（审计 §9） |

## 4. 影响与验证

- **接口**：Device/Alarm/DeviceData 全部资源接口按站点作用域强制；新增 `GET /api/sites`（当前用户可访问站点）；
- **兼容**：`V4` 为增量迁移（不碰 V1/V3，checksum 不变）；既有库设备回填默认站点；dev seed 补充 user_site 分配（幂等）；
- **缓存**：设备详情/数据统计/时间范围缓存 key 含 userId，写操作全量失效；
- **测试**：`SiteAccessServiceTest`（授权核心）、Service 单元测试（断言调用 + 403 传播）、`DevSeedDemoDataTest`/`MySqlSeedIsolationIT`（seed user_site 幂等）、H2 夹具扩展 site/user_site；
- **遗留**：注册用户默认无站点成员 → 无资源访问权（公开注册面收窄）；站点 CRUD 管理接口、前端站点筛选、`GET /api/sites` 前端接入为后续增量。

## 5. 风险

| 风险 | 缓解 |
|---|---|
| 既有 VIEWER/OPERATOR 全局角色用户失去资源访问（无 user_site） | seed 已为全部演示用户分配默认站点；真实用户由管理员经 user_site 分配 |
| 每请求多一次 user_site 查询 | Redis 缓存可后续接入；当前查询为单条 JOIN，成本可忽略 |
| 缓存按用户扩容 | 设备/数据缓存条目有限，写操作全量失效保证正确性 |
