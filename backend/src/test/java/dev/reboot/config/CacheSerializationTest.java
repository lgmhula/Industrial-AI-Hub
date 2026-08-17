package dev.reboot.config;

import dev.reboot.dto.DeviceVO;
import dev.reboot.dto.UserVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 缓存序列化往返测试 —— 回归 D1（LocalDateTime + 类型信息双缺陷）。
 *
 * <p>不依赖 Redis 实例：直接使用 {@link CacheConfig#createCacheObjectMapper()} 构造与
 * 生产完全一致的序列化器，验证「写 → 读」往返可还原原类型。
 * 类型信息缺失时读命中会反序列化为 {@code LinkedHashMap} → ClassCastException → 500。</p>
 *
 * @author AI 助手
 * @since 2026-08-18
 */
class CacheSerializationTest {

    private final GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(CacheConfig.createCacheObjectMapper());

    @Test
    void userVoRoundTripRestoresConcreteType() {
        UserVO vo = new UserVO();
        vo.setId(1L);
        vo.setUsername("admin");
        vo.setEmail("admin@industrial.com");
        vo.setPhone("13800138000");
        vo.setStatus(1);
        vo.setCreatedAt(LocalDateTime.of(2026, 8, 17, 10, 30, 0));
        vo.setUpdatedAt(LocalDateTime.of(2026, 8, 17, 11, 0, 0));

        byte[] bytes = serializer.serialize(vo);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat(json).contains("\"@class\":\"" + UserVO.class.getName() + "\"");

        Object back = serializer.deserialize(bytes);
        assertThat(back).isInstanceOf(UserVO.class);
        UserVO restored = (UserVO) back;
        assertThat(restored.getId()).isEqualTo(1L);
        assertThat(restored.getUsername()).isEqualTo("admin");
        assertThat(restored.getCreatedAt()).isEqualTo(vo.getCreatedAt());
        assertThat(restored.getUpdatedAt()).isEqualTo(vo.getUpdatedAt());
    }

    @Test
    void deviceVoRoundTripRestoresConcreteType() {
        DeviceVO vo = new DeviceVO();
        vo.setId(7L);
        vo.setDeviceName("温控传感器-01");
        vo.setDeviceCode("TEMP-001");
        vo.setDeviceType("SENSOR");
        vo.setStatus(1);
        vo.setIpAddress("192.168.1.101");
        vo.setPort(502);
        vo.setLocation("一车间-东区-1号");
        vo.setCreatedAt(LocalDateTime.of(2026, 8, 17, 8, 0, 0));
        vo.setUpdatedAt(LocalDateTime.of(2026, 8, 17, 9, 0, 0));

        byte[] bytes = serializer.serialize(vo);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat(json).contains("\"@class\":\"" + DeviceVO.class.getName() + "\"");

        Object back = serializer.deserialize(bytes);
        assertThat(back).isInstanceOf(DeviceVO.class);
        DeviceVO restored = (DeviceVO) back;
        assertThat(restored.getDeviceName()).isEqualTo("温控传感器-01");
        assertThat(restored.getCreatedAt()).isEqualTo(vo.getCreatedAt());
    }

    @Test
    void listRoundTripRestoresElementType() {
        List<UserVO> list = new ArrayList<>();
        UserVO vo = new UserVO();
        vo.setId(2L);
        vo.setUsername("operator01");
        vo.setCreatedAt(LocalDateTime.of(2026, 8, 17, 12, 0, 0));
        list.add(vo);

        byte[] bytes = serializer.serialize(list);
        Object back = serializer.deserialize(bytes);
        assertThat(back).isInstanceOf(ArrayList.class);
        assertThat((List<?>) back).hasSize(1);
        assertThat(((List<?>) back).get(0)).isInstanceOf(UserVO.class);
    }
}
