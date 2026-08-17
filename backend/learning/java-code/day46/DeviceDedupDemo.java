package code.day46;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

public class DeviceDedupDemo {
    private static final String HOST = "localhost";
    private static final int PORT = 6379;
    private static final String PASSWORD = System.getenv("REDIS_PASSWORD");

    private static boolean tryReport(Jedis jedis, String dev, String data, long windowMs) {
        String key = "dedup:" + dev + ":" + (System.currentTimeMillis() / windowMs);
        String r = jedis.set(key, data, SetParams.setParams().nx().px(windowMs * 2));
        if ("OK".equals(r)) {
            System.out.println("  [OK] " + dev + " -> " + data);
            return true;
        } else {
            System.out.println("  [DUP] " + dev + " -> " + data + " (blocked)");
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Day 46 - Distributed Lock: Device Dedup\n");
        try (Jedis jedis = new Jedis(HOST, PORT)) {
            if (PASSWORD != null && !PASSWORD.isBlank()) jedis.auth(PASSWORD);
            long window = 2000;
            tryReport(jedis, "DEV-001", "temp=36.5", window);
            tryReport(jedis, "DEV-001", "temp=36.5", window);
            tryReport(jedis, "DEV-001", "temp=36.5", window);
            tryReport(jedis, "DEV-002", "rpm=1500", window);
            System.out.println("\nWaiting window expiry...");
            Thread.sleep(window + 500);
            tryReport(jedis, "DEV-001", "temp=36.7", window);
            jedis.flushDB();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
