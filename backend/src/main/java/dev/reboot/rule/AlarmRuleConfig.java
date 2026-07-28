package dev.reboot.rule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 报警规则配置 —— 定义所有默认检测规则。
 *
 * <p>规则在应用启动时加载到内存，后续可扩展为数据库/配置中心动态管理。
 *
 * @author hula0710
 * @since 2026-07-28
 */
@Configuration
public class AlarmRuleConfig {

    /**
     * 报警规则清单。
     *
     * <p>每条规则按序评估，任意一条命中即触发告警。
     */
    @Bean
    public List<AlarmRule> alarmRules() {
        return List.of(
            // ── 温度 ──
            new AlarmRule("TEMPERATURE", "GT",  "40.0",  2, "OVER_TEMP",
                    "设备 {deviceId} 温度过高: {value}°C (阈值 {threshold}°C)"),
            new AlarmRule("TEMPERATURE", "LT",  "0.0",   2, "UNDER_TEMP",
                    "设备 {deviceId} 温度过低: {value}°C (阈值 {threshold}°C)"),

            // ── 压力 ──
            new AlarmRule("PRESSURE", "GT",  "110.0", 1, "OVER_PRESSURE",
                    "设备 {deviceId} 压力过高: {value}kPa (阈值 {threshold}kPa)"),
            new AlarmRule("PRESSURE", "LT",  "90.0",  2, "UNDER_PRESSURE",
                    "设备 {deviceId} 压力过低: {value}kPa (阈值 {threshold}kPa)"),

            // ── 转速 ──
            new AlarmRule("SPEED", "GT",  "3000.0", 3, "OVER_SPEED",
                    "设备 {deviceId} 转速过高: {value}RPM (阈值 {threshold}RPM)"),
            new AlarmRule("SPEED", "LT",  "100.0",  2, "UNDER_SPEED",
                    "设备 {deviceId} 转速异常偏低: {value}RPM (阈值 {threshold}RPM)"),

            // ── 湿度 ──
            new AlarmRule("HUMIDITY", "GT",  "90.0",  1, "OVER_HUMIDITY",
                    "设备 {deviceId} 湿度过高: {value}% (阈值 {threshold}%)"),
            new AlarmRule("HUMIDITY", "LT",  "10.0",  1, "UNDER_HUMIDITY",
                    "设备 {deviceId} 湿度过低: {value}% (阈值 {threshold}%)")
        );
    }
}
