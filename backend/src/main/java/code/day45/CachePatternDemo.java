package code.day45;

import redis.clients.jedis.Jedis;
import java.util.concurrent.ThreadLocalRandom;

public class CachePatternDemo {
    private static final String HOST = "localhost";
    private static final int PORT = 6379;
    private static final String PASSWORD = System.getenv("REDIS_PASSWORD");

    public static void main(String[] args) {
        System.out.println("Day 45 - Cache Patterns Demo\n");
        try (Jedis jedis = new Jedis(HOST, PORT)) {
            if (PASSWORD != null && !PASSWORD.isBlank()) jedis.auth(PASSWORD);
            demoPenetration(jedis);
            demoBreakdown(jedis);
            demoAvalanche(jedis);
            jedis.flushDB();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    // 1. Cache Penetration -> Bloom Filter
    private static void demoPenetration(Jedis jedis) {
        System.out.println("=== 1. Penetration -> Bloom Filter ===");
        jedis.del("bloom:device");
        jedis.sendCommand(() -> "BF.RESERVE".getBytes(), "bloom:device", "0.01", "10000");
        for (int i = 1; i <= 100; i++)
            jedis.sendCommand(() -> "BF.ADD".getBytes(), "bloom:device", ("device-" + i));
        Object r1 = jedis.sendCommand(() -> "BF.EXISTS".getBytes(), "bloom:device", "device-50");
        Object r2 = jedis.sendCommand(() -> "BF.EXISTS".getBytes(), "bloom:device", "hacker-9999");
        System.out.println("  device-50: " + (((Number) r1).intValue() == 1 ? "may-exist" : "not-exist"));
        System.out.println("  hacker-9999: " + (((Number) r2).intValue() == 1 ? "may-exist" : "not-exist -> REJECT"));
        System.out.println();
    }

    // 2. Cache Breakdown -> SETNX Mutex
    private static void demoBreakdown(Jedis jedis) {
        System.out.println("=== 2. Breakdown -> SETNX Mutex ===");
        String key = "hot:device:1001";
        jedis.del(key, "lock:" + key);
        Runnable worker = () -> {
            String n = Thread.currentThread().getName();
            String lk = "lock:" + key;
            if (jedis.setnx(lk, "1") == 1) {
                jedis.expire(lk, 10);
                try {
                    if (jedis.get(key) != null) return;
                    System.out.println("  [" + n + "] got lock -> rebuild cache");
                    try { Thread.sleep(300); } catch (InterruptedException e) {}
                    jedis.setex(key, 300, "db-result");
                } finally { jedis.del(lk); }
            } else {
                System.out.println("  [" + n + "] waiting lock...");
                try { Thread.sleep(200); } catch (InterruptedException e) {}
                System.out.println("  [" + n + "] retried cache=" + (jedis.get(key) != null));
            }
        };
        Thread t1 = new Thread(worker, "r1");
        Thread t2 = new Thread(worker, "r2");
        Thread t3 = new Thread(worker, "r3");
        t1.start(); t2.start(); t3.start();
        try { t1.join(); t2.join(); t3.join(); } catch (InterruptedException e) {}
        System.out.println("  Result: " + jedis.get(key) + "\n");
    }

    // 3. Cache Avalanche -> Random TTL
    private static void demoAvalanche(Jedis jedis) {
        System.out.println("=== 3. Avalanche -> Random TTL ===");
        int base = 600;
        System.out.println("  Fixed TTL:");
        for (int i = 1; i <= 3; i++) {
            jedis.setex("f:" + i, base, "v" + i);
            System.out.printf("    f:%d TTL=%ds%n", i, jedis.ttl("f:" + i));
        }
        System.out.println("  Random TTL (+-20%):");
        for (int i = 1; i <= 3; i++) {
            long ttl = jitter(base);
            jedis.setex("j:" + i, ttl, "v" + i);
            System.out.printf("    j:%d TTL=%ds%n", i, ttl);
        }
        System.out.println();
    }

    static long jitter(long b) {
        return Math.max(1, (long) (b * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4)));
    }
}
