package dev.reboot.rule;

import java.math.BigDecimal;

/**
 * 报警规则定义 —— 描述一条检测条件。
 *
 * <p>每条规则包含匹配条件（数据类型 + 比较运算符 + 阈值）和触发后行为（告警类型、等级、消息模板）。
 *
 * @author hula0710
 * @since 2026-07-28
 */
public class AlarmRule {

    /** 匹配的数据类型，如 TEMPERATURE / PRESSURE / SPEED。 */
    private String dataType;

    /** 比较运算符：GT（大于）/ LT（小于）。 */
    private String operator;

    /** 阈值。 */
    private BigDecimal threshold;

    /** 触发后告警等级：1-一般 2-重要 3-紧急。 */
    private Integer alarmLevel;

    /** 告警类型编码，如 OVER_TEMP / UNDER_PRESSURE。 */
    private String alarmType;

    /** 告警消息模板，可使用 {value} {threshold} {deviceId} 占位。 */
    private String messageTemplate;

    public AlarmRule() {}

    public AlarmRule(String dataType, String operator, String threshold,
                     Integer alarmLevel, String alarmType, String messageTemplate) {
        this.dataType = dataType;
        this.operator = operator;
        this.threshold = new BigDecimal(threshold);
        this.alarmLevel = alarmLevel;
        this.alarmType = alarmType;
        this.messageTemplate = messageTemplate;
    }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }
    public Integer getAlarmLevel() { return alarmLevel; }
    public void setAlarmLevel(Integer alarmLevel) { this.alarmLevel = alarmLevel; }
    public String getAlarmType() { return alarmType; }
    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }
    public String getMessageTemplate() { return messageTemplate; }
    public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }
}
