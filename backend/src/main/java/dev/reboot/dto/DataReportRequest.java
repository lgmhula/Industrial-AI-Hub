package dev.reboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 设备数据上报请求 DTO。
 *
 * @author hula0710
 * @since 2026-07-27
 */
public class DataReportRequest {

    @NotBlank(message = "数据类型不能为空")
    private String dataType;

    @NotNull(message = "数据值不能为空")
    private BigDecimal dataValue;

    private String unit;

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public BigDecimal getDataValue() { return dataValue; }
    public void setDataValue(BigDecimal dataValue) { this.dataValue = dataValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
