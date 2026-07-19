# Decision 0003: Nacos Health Check Tuning
**Date:** 2026-07-17 | **Status:** Accepted

## Context
Nacos 2.4.3 on Docker/OrbStack (arm64 macOS) failed to start with
default healthcheck. Two issues found:
1. NACOS_AUTH_TOKEN must be >= 256 bits (32+ chars) for JWT HMAC-SHA
2. JVM startup takes 60-90s on macOS, exceeding default retry window

## Decision
- Token: upgraded to 50-char secure key in .env
- Healthcheck: `start_period: 120s` + `interval: 10s` + `retries: 5`

## Consequences
- Nacos starts reliably on first attempt
- Token meets Nacos 2.4.3 security requirements
- Documented in .env.example for future reference
