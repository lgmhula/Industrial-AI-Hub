# Decision 0002: Redis Stack with All Modules
**Date:** 2026-07-16 | **Status:** Accepted

## Context
Industrial AI Hub needs: Bloom filters, JSON docs, full-text search,
time-series data. Multiple specialized DBs add operational complexity.

## Decision
Use Redis Stack 7.4 with all five modules loaded via explicit
loadmodule directives. AOF+RDB persistence, appendfsync everysec.

## Consequences
- Single Redis instance covers all advanced use cases
- Modules must be explicitly loaded in redis.conf
- Password via .env
