package code.day44;

import redis.clients.jedis.Jedis;
import java.util.Map;

public class CacheWarmupDemo {
    private static final String HOST = "localhost";
    private static final int PORT = 6379;
    private static final String PASSWORD = System.getenv("REDIS_PASSWORD");

    private static Map<String, String> mockDb() {
        return Map.of(
            "device:info:1", "{\"name\":\"temp-sensor\",\"type\":\"temperature\"}",
            "device:info:2", "{\"name\":\"pressure-sensor\",\"type\":\"pressure\"}",
            "device:info:3", "{\"name\":\"humidity-sensor\",\"type\":\"humidity\"}"
        );
    }

    public static void main(String[] args) {
        System.out.println("Day 44 - Cache Warmup Demo\n");
        try (Jedis jedis = new Jedis(HOST, PORT)) {
            if (PASSWORD != null && !PASSWORD.isBlank()) jedis.auth(PASSWORD);
            // Cold start - all miss
            System.out.println("--- Cold Start ---");
            long t1 = System.nanoTime();
            for (String key : mockDb().keySet()) {
                if (jedis.get(key) == null) {
                    jedis.setex(key, 1800, mockDb().get(key));
                    System.out.println("  MISS " + key + " -> cached");
                }
            }
            System.out.printf("  Time: %.2fms%n%n", (System.nanoTime() - t1) / 1e6);
            // Warm - all hit
            System.out.println("--- After Warmup ---");
            long t2 = System.nanoTime();
            int hits = 0;
            for (String key : mockDb().keySet()) {
                if (jedis.get(key) != null) hits++;
            }
            System.out.printf("  Hits: %d/%d Time: %.2fms%n%n",
                hits, mockDb().size(), (System.nanoTime() - t2) / 1e6);
            jedis.flushDB();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
