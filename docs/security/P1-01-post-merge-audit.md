# P1-01 Post-Merge Security Verification（合并后安全验证）

> 分支：`main` @ `1eac44d48b626ccf2422d074f59e37ed216aa59b`（PR #6 合并后）
> 类型：只读审计（未修改任何既有文件；本报告为新建交付物，未 commit）
> 日期：2026-08-23

---

## 1. Git 状态

- branch：`main`
- HEAD：`1eac44d48b626ccf2422d074f59e37ed216aa59b`（= origin/main）
- workspace：clean（`## main...origin/main`，无未提交/未跟踪）

## 2. 授权架构图（合并后）

```
HTTP Request (Authorization: Bearer <JWT>)
  → JwtAuthFilter: 验签/过期 → request attrs: userId / username / roles
  → RateLimitInterceptor
  → AuthInterceptor: @RequireRole（角色维度，全局 ADMIN 入口）
  → Controller: currentUserId(request) 显式提取 userId，传入 Service
  → Service: SiteAccessService.assertSiteAccess(userId, device.siteId, required)
        ├─ isGlobalAdmin(userId) → 放行（user_role 含 ADMIN）
        └─ user_site(userId, siteId).role → RoleEnum.isAtLeast(required) ? 放行 : 403
  → Mapper: 列表接口 SQL 追加 site_id IN (accessibleSiteIds)；单对象先查 site 再断言
  → MySQL
```

资源层级：`User → user_site → Site → Device → Alarm/DeviceData（经 device_id 继承）`

## 3. Endpoint Coverage Matrix

| 接口 | 方法 | 授权判定 | 结果 |
|---|---|---|---|
| `/api/devices` | GET | Service 取 accessibleSiteIds → SQL `site_id IN` 过滤 | ✅ |
| `/api/devices/{id}` | GET | 先 `findById` 取 `device.site_id` → `assertSiteAccess(VIEWER)` | ✅ |
| `/api/devices` | POST | 解析 siteId → `assertSiteAccess(OPERATOR)` → insert(siteId) | ✅ |
| `/api/devices/{id}` | PUT | 先取 device.siteId → `assertSiteAccess(OPERATOR)`（site 不可变更） | ✅ |
| `/api/devices/{id}` | DELETE | `@RequireRole(ADMIN)`（全局管理员专属，站点管理员不可删） | ✅ |
| `/api/alarms` | GET | SQL 按 `device.site_id IN` 过滤（JOIN device） | ✅ |
| `/api/alarms/device/{deviceId}` | GET | 先取 device → `assertSiteAccess(VIEWER)` → 按 deviceId 查 | ✅ |
| `/api/alarms/status/{status}` | GET | SQL 按 `device.site_id IN` 过滤 | ✅ |
| `/api/alarms/{id}/acknowledge` | PUT | **先 `alarm→device→site` 断言 OPERATOR，再按 id 更新**（非仅凭 alarm id） | ✅ |
| `/api/alarms/{id}/resolve` | PUT | 同上 | ✅ |
| `/api/device-data/device/{deviceId}` | POST report | `requireDeviceAccess(OPERATOR)` 先行 | ✅ |
| `/api/device-data/device/{deviceId}` | GET list | `requireDeviceAccess(VIEWER)` | ✅ |
| `/api/device-data/device/{deviceId}/range` | GET | `requireDeviceAccess(VIEWER)`（缓存 key 含 userId） | ✅ |
| `/api/device-data/device/{deviceId}/latest` | GET | `requireDeviceAccess(VIEWER)` | ✅ |
| `/api/device-data/device/{deviceId}/stats` | GET | `requireDeviceAccess(VIEWER)`（缓存 key 含 userId） | ✅ |
| `/api/sites` | GET | 登录即可；返回当前用户可访问站点（admin=全部） | ✅ |
| `/api/users/**` / `/api/operation-logs/**` | 全部 | 全局 ADMIN（系统级，无站点维度） | ✅（非资源接口） |
| `/api/auth/login` / `register` | POST | 公开；注册默认无站点成员 → 无资源访问权（安全收窄） | ✅ |

**结论：Device / Alarm / DeviceData 无遗漏端点，全部资源接口均经过站点授权。**

## 4. 已验证安全边界

| # | 边界 | 证据 |
|---|---|---|
| 1 | userId 可靠传入 Service | 全部资源 Controller 经 `currentUserId(request)`（JWT request attr）显式传参；无 Controller 内散点判断、无绕过 SiteAccessService 的直查 |
| 2 | ADMIN 全局 bypass 保持 | `SiteAccessService.isGlobalAdmin`（user_role 含 ADMIN）→ `assertSiteAccess` 直接放行；测试 `assertSiteAccess_admin_shouldAlwaysPass`、`accessibleSiteIds_admin_shouldReturnNull` |
| 3 | VIEWER/OPERATOR 必须经 site membership | 无 user_site 成员 → `accessibleSiteIds=[]`（列表空）或 `assertSiteAccess` 抛 403（单对象）；全局 OPERATOR/VIEWER 角色不再隐含资源访问 |
| 4 | 无站点用户不可读设备 | `listAll_noSiteAccess_shouldReturnEmpty` + `getById_noSiteAccess_shouldThrowForbidden` + `SiteAccessServiceTest.assertSiteAccess_nonMember_shouldThrow403` |
| 5 | Alarm 确认/解决不凭 alarm id 直更 | `AlarmService.assertAlarmSiteAccess`：`alarmMapper.findById` → `deviceMapper.findById(alarm.deviceId)` → `assertSiteAccess(OPERATOR)`，之后才执行 UPDATE |
| 6 | 列表防数据量泄露 | Device/Alarm 列表 SQL 动态 `site_id IN (accessibleSiteIds)`；无成员返回空页（不查询） |
| 7 | 缓存不可绕过授权 | `@Cacheable` key 含 `userId`（getById/stats/range）；写操作 `allEntries=true` 全量失效 |
| 8 | 设备创建归属站点 | `DeviceDTO.siteId` 可选：唯一站点/管理员默认站点兜底；创建者需该站点 OPERATOR |

## 5. 测试覆盖（六场景映射）

| 场景 | 覆盖点 | 状态 |
|---|---|---|
| viewer 本站访问 | `SiteAccessServiceTest.assertSiteAccess_memberWithEnoughRole_shouldPass`；`DeviceServiceTest.getById_shouldReturnDeviceVO`（verify VIEWER 断言）；`AlarmServiceTest.listByDevicePaged_shouldFilterByDevice` | ✅ |
| viewer 跨站拒绝 | `SiteAccessServiceTest.assertSiteAccess_nonMember_shouldThrow403`；`DeviceServiceTest.getById_noSiteAccess_shouldThrowForbidden` | ✅ |
| operator 本站修改 | `DeviceServiceTest.update_shouldUpdateFields`（verify OPERATOR 断言）；`AlarmServiceTest.acknowledge_shouldReturnTrue` | ✅ |
| operator 跨站拒绝 | `SiteAccessServiceTest.assertSiteAccess_memberWithInsufficientRole_shouldThrow403`；`DeviceServiceTest.update_noSiteAccess_shouldThrowForbidden`；`AlarmServiceTest.acknowledge_noSiteAccess_shouldThrowForbidden` | ✅ |
| admin 全站访问 | `SiteAccessServiceTest.assertSiteAccess_admin_shouldAlwaysPass`、`listAccessibleSites_admin_shouldReturnAll` | ✅ |
| 无 site membership | `SiteAccessServiceTest.assertSiteAccess_nonMember_shouldThrow403`；`listAll_noSiteAccess_shouldReturnEmpty`（Device/Alarm） | ✅ |

回归：`./mvnw clean verify` 122/122；MySQL IT 6/6（含 seed user_site 幂等 + V4 迁移）。

## 6. 未覆盖风险（记录，未处理）

1. **站点管理面缺失**：无站点 CRUD / `user_site` 分配管理接口——站点成员分配目前仅靠 seed/DB 直接操作（生产需管理员接口）。
2. **无站点用户行为**：注册用户（无 user_site）资源接口一律 403/空列表——前端无站点提示体验待补（非安全缺陷）。
3. **Qodana**：PR 上 fail（独立质量扫描，非 gate，main 历史同样失败）。
4. **已知 P1-02 议题（不属于本次范围）**：登录限流 / Token 吊销 / 账户禁用即时失效——站点角色为运行时查询，天然规避「角色变更不生效」，但 JWT 本身仍无吊销能力。
5. **user_role 全局 OPERATOR/VIEWER 语义变化**：资源访问不再由全局角色授予（历史兼容保留，无删除）；对既有非 demo 真实用户需管理员补 `user_site` 分配。

## 7. 是否建议进入 P1-02

**建议：是（可并行推进）**——P1-01 站点授权已合并并通过本验证（BOLA/IDOR 系统性缺口已闭环）；P1-02（登录限流 / Token 吊销 / 账户禁用即时失效 / 账户锁定）与授权模型无耦合（站点角色运行时查询已使角色变更实时生效），可独立进入。建议下一轮补齐站点成员管理接口（未覆盖风险 #1）后再验收发布基线。

---

*本审计未修改任何既有文件；报告为新建交付物（`docs/security/P1-01-post-merge-audit.md`），未创建 commit/branch。*
