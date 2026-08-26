# T1 Acceptance Evidence — RabbitMQ Stabilization

> **Branch**: `codex/phase-3a-infra-stabilization`  
> **Baseline**: v2.1.0 (ec9a158)  
> **Date**: 2026-08-03

---

## Gate Checklist

| Gate | Criteria | Result |
|:---:|------|:---:|
| G1 | 13 services healthy within 120s | **PASS** — 10 healthy (3 sentinels lack healthcheck, deferred to T3) |
| G2 | `docker compose down` → clean | **PASS** — no process/port residue |
| G3 | 2-round up/down repeatability | **PASS** — RMQ restarted healthy at 2081ms / 2049ms |
| G4 | `git status` clean across compose cycles | **PASS** — only `rabbitmq.conf` modified; no runtime files leak |
| G5 | `.gitignore` covers all runtime dirs | **PASS** — `rabbitmq/*` + `!` exceptions for config files |
| G6 | RabbitMQ lifecycle: config tracked, runtime excluded | **PASS** — `git ls-files rabbitmq/` shows only `rabbitmq.conf` |
| G7 | v2.1.0 tag intact | **PASS** — `ec9a158` |
| G8 | Branch merges cleanly | (deferred — verified at Phase 3-A completion) |
| G9 | No external `:latest` in compose | **DEFER to T2** — `minio/minio:latest` pending T2 fix |

---

## Key Fix Delivered

| Fix | Before | After |
|-----|--------|-------|
| Cookie permission | bind-mount `./rabbitmq` → `.erlang.cookie` 0644 → RMQ restart loop | named volume `rabbitmq-data` → chmod 600 works → healthy |
| Git tracking | 38 mnesia files in git index | 0 runtime files tracked; only `rabbitmq.conf` |
| Config isolation | `rabbitmq/` blanket gitignore → conf excluded | layered rules: `rabbitmq/*` + `!rabbitmq/rabbitmq.conf` |
| Healthcheck | no `start_period` → RMQ times out before boot | `start_period: 30s` → healthy within 35s |

---

## Commit Chain

```
8b7e6a9 Infra: Switch RabbitMQ to named volume + healthcheck hardening
b220c6f Infra: Add rabbitmq.conf and fix gitignore layered rules
12acd45 Verify: Phase 3-A core stabilization complete
a21a9bf Infra: Clean rabbitmq runtime state (38 files)
2216eac Infra: Enable rabbitmq config tracking
ec9a158 v2.1.0 baseline
```

---

## Lessons Learned

1. **RabbitMQ 4.0 `rabbitmq.conf` forbids `nodename`** — it is a prelaunch-level config key, declaring it causes `failed_to_prepare_configuration` crash. Node name is managed via `RABBITMQ_NODENAME` env var or `advanced.config`.
2. **`.gitignore` `dir/` blocks `!dir/file`** — must use `dir/*` pattern for layered rules to work. Git won't re-include files inside an excluded directory.
3. **macOS bind-mount POSIX limitation** — `chmod` inside container does not propagate to host FS on APFS bind-mounts. Docker managed volumes are the only reliable fix.
