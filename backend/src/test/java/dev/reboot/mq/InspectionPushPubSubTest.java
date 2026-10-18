package dev.reboot.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionPushPubSubTest {

    @Mock private StringRedisTemplate redis;
    @Mock private InspectionPushGateway gateway;

    private ObjectMapper objectMapper;
    private InspectionPushPubSub pubSub;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        pubSub = new InspectionPushPubSub(redis, objectMapper, gateway);
    }

    @Test
    void publish_withSubscribers_returnsTrue() throws Exception {
        InspectionReportMessage message = message();
        String payload = objectMapper.writeValueAsString(message);
        when(redis.convertAndSend(eq(InspectionPushPubSub.CHANNEL), eq(payload))).thenReturn(2L);

        assertTrue(pubSub.publish(message));
    }

    @Test
    void publish_withoutSubscribers_returnsFalseForLocalFallback() throws Exception {
        InspectionReportMessage message = message();
        String payload = objectMapper.writeValueAsString(message);
        when(redis.convertAndSend(eq(InspectionPushPubSub.CHANNEL), eq(payload))).thenReturn(0L);

        assertFalse(pubSub.publish(message));
    }

    @Test
    void publish_redisFailure_returnsFalseForLocalFallback() throws Exception {
        InspectionReportMessage message = message();
        String payload = objectMapper.writeValueAsString(message);
        when(redis.convertAndSend(eq(InspectionPushPubSub.CHANNEL), eq(payload)))
                .thenThrow(new IllegalStateException("redis down"));

        assertFalse(pubSub.publish(message));
    }

    @Test
    void onMessage_deserializesAndPushesToLocalGateway() throws Exception {
        InspectionReportMessage message = message();
        byte[] payload = objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);

        pubSub.onMessage(new DefaultMessage(InspectionPushPubSub.CHANNEL.getBytes(StandardCharsets.UTF_8), payload),
                null);

        ArgumentCaptor<InspectionReportMessage> captor = ArgumentCaptor.forClass(InspectionReportMessage.class);
        verify(gateway).push(captor.capture());
        assertEquals(LocalDate.of(2026, 10, 18), captor.getValue().getReportDate());
        assertEquals("跨实例 SSE 测试日报", captor.getValue().getReport());
        assertEquals(List.of(1L), captor.getValue().getSiteIds());
    }

    private InspectionReportMessage message() {
        InspectionReportMessage message = new InspectionReportMessage();
        message.setReportDate(LocalDate.of(2026, 10, 18));
        message.setReport("跨实例 SSE 测试日报");
        message.setSiteIds(List.of(1L));
        return message;
    }
}
