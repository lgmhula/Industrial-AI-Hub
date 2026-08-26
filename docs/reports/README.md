# Reports 目录索引

> 最后更新：2026-08-26 | 维护：AI 助手 + hula0710

本目录包含项目所有审计报告、Release Note、部署手册。**历史归档位于 `archive/` 子目录**，新读者优先阅读"当前有效"清单。

---

## 当前有效

### 综合审计（最新权威源）

| 文档 | 日期 | 范围 |
|------|------|------|
| [comprehensive-review-2026-08-22.md](comprehensive-review-2026-08-22.md) | 2026-08-22 | 整合版：全方位审查 + 数据库对比 + RBAC 升级 + Phase 5 硬件 + Phase 4 计划 |
| [phase3-quality-audit-2026-08-23.md](phase3-quality-audit-2026-08-23.md) | 2026-08-23 | Phase 3 工程质量专项审计（8 维度评分 + 4 周修复计划） |

### 历史基线

| 文档 | 日期 | 范围 |
|------|------|------|
| [v2.1.0-release-note.md](v2.1.0-release-note.md) | 2026-08-03 | v2.1.0 Release Note（长期保留，配对 audit-report 已归档） |
| [phase3-a-execution-verification.md](phase3-a-execution-verification.md) | 2026-08 | Phase 3-A 执行验证证据 |

### 专项审计（长期有效）

| 文档 | 范围 |
|------|------|
| [Architecture-Consistency-Report-v1.2.md](Architecture-Consistency-Report-v1.2.md) | 文档-代码一致性检查 |
| [SQL-Audit-Report.md](SQL-Audit-Report.md) | SQL 文件一致性审计 |
| [Code-Security-Scan-2026-08-04.md](Code-Security-Scan-2026-08-04.md) | 代码安全扫描 |
| [phase3-drift-audit.md](phase3-drift-audit.md) | Phase 3 漂移审计 |

### 部署手册（长期有效）

| 文档 | 范围 |
|------|------|
| [deploy-runbook-new-device.md](deploy-runbook-new-device.md) | 新设备部署 Runbook（全量坑位 + 一键部署指令） |

---

## 历史归档

`archive/` 子目录保留以下 10 份历史文档，**仅供追溯，不再维护**：

| 文档 | 归档原因 |
|------|---------|
| `archive/full-project-audit-2026-08-04.md` | 早期基线审计（v2.1.0），已被 comprehensive-review-2026-08-22 替代 |
| `archive/full-project-review-2026-08-21.md` | 已被 2026-08-22 整合版替代 |
| `archive/database-comparison-2026-08-21.md` | 已被 2026-08-22 整合版替代 |
| `archive/phase3-a-plan-audit.md` | Phase 3-A 已收官 |
| `archive/phase3-a-plan-audit-round2.md` | Phase 3-A 已收官 |
| `archive/phase3-a-round2-revision.md` | Phase 3-A 已收官 |
| `archive/v2.1.0-audit-report.md` | v2.1.0 已被 v2.2.0 基线替代 |
| `archive/v2.3.0-functional-test-report-2026-08-17.md` | 版本漂移（无对应 tag，基线为 v2.2.0） |
| `archive/deploy-report-new-device-2026-08-17.md` | 一次性部署记录（已固化到 deploy-runbook） |
| `archive/t1-acceptance-evidence.md` | Phase 3-A T1 已验收，证据归档 |
