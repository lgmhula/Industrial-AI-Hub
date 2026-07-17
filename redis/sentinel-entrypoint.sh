#!/usr/bin/env sh
REDIS_IP=$(getent hosts redis 2>/dev/null | awk '{print $1}' | head -1)
[ -z "$REDIS_IP" ] && REDIS_IP=$(ping -c 1 redis 2>/dev/null | head -1 | sed 's/.*(\([^)]*\)).*/\1/')
echo "Redis IP: $REDIS_IP"

cat > /tmp/my_sentinel.conf << EOF
sentinel monitor mymaster $REDIS_IP 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
sentinel parallel-syncs mymaster 1
EOF

exec redis-sentinel /tmp/my_sentinel.conf --sentinel
