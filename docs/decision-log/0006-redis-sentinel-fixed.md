# Decision 0006 (Updated): Redis Sentinel — Fixed
**Date:** 2026-07-17 | **Status:** Resolved

## Root Cause
redis-sentinel binary loads sentinel.conf at startup and resolves
hostnames using the system resolver, which on Docker/OrbStack cannot
resolve service names directly. Container entrypoint with `getent`
+ dynamic config generation bypasses this limitation.

## Fix
- Custom entrypoint script (`redis/sentinel-entrypoint.sh`):
  1. Resolves Redis IP via `getent hosts redis`
  2. Generates `/tmp/my_sentinel.conf` with the resolved IP
  3. Starts redis-sentinel with the generated config
- compose.yml: `entrypoint: ["/entrypoint.sh"]` replaces static command
- All 3 sentinels (26379/26380/26381) running and monitoring mymaster

## Verification
```
docker exec redis-sentinel1 redis-cli -p 26379 SENTINEL MASTER mymaster
```
Returned master info with correct IP, port, and sentinel count.
