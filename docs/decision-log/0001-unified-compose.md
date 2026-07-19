# Decision 0001: Unified Docker Compose
**Date:** 2026-07-16 | **Status:** Accepted

## Context
Previously each service had its own compose.yml scattered across
/Users/air/Documents/dockercompose/. This made it hard to manage.

## Decision
All services unified under single compose.yml with industrial-network.
Service names used for inter-service addressing. No hardcoded IPs.

## Consequences
- Single docker compose up/down for all services
- Infrastructure as Code, version-controlled
- Clear config/data separation per service
