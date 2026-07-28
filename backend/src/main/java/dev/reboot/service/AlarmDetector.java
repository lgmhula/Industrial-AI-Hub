package dev.reboot.service;

import dev.reboot.dto.AlarmVO;
import dev.reboot.rule.AlarmRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 报警检测引擎 —— 根据规则列表评估数据是否触发告警。
 *
 * <p>每次上报数据时调用 {@link #check(Long, String, BigDecimal)}，返回匹配的告警列表。
 *
 * @author hula0710
 * @since 2026-07-28
 */
@Component
public class AlarmDetector {

    private static final Logger log = LoggerFactory.getLogger(AlarmDetector.class);

    private final List<AlarmRule> rules;
    private final AlarmService alarmService;

    public AlarmDetector(List<AlarmRule> rules, AlarmService alarmService) {
        this.rules = rules;
        this.alarmService = alarmService;
    }

    /**
     * 检测上报数据是否触发报警规则，触发则持久化。
     *
     * @param deviceId 设备 ID
     * @param dataType 数据类型
     * @param value    数据值
     * @return 触发的告警列表（可能为空）
     */
    public List<AlarmVO> check(Long deviceId, String dataType, BigDecimal value) {
        List<AlarmVO> triggered = new ArrayList<>();
        if (value == null || dataType == null) {
            return triggered;
        }

        for (AlarmRule rule : rules) {
            if (!rule.getDataType().equalsIgnoreCase(dataType)) {
                continue;
            }

            boolean matched = false;
            int cmp = value.compareTo(rule.getThreshold());
            if ("GT".equalsIgnoreCase(rule.getOperator()) && cmp > 0) {
                matched = true;
            } else if ("LT".equalsIgnoreCase(rule.getOperator()) && cmp < 0) {
                matched = true;
            }

            if (matched) {
                String message = rule.getMessageTemplate()
                        .replace("{deviceId}", String.valueOf(deviceId))
                        .replace("{value}", value.toPlainString())
                        .replace("{threshold}", rule.getThreshold().toPlainString());

                log.warn("ALARM: device={} type={} value={} → {}", deviceId, dataType, value, rule.getAlarmType());

                AlarmVO vo = alarmService.createAlarm(
                        deviceId,
                        rule.getAlarmType(),
                        rule.getAlarmLevel(),
                        message,
                        LocalDateTime.now()
                );
                triggered.add(vo);
            }
        }
        return triggered;
    }
}
