package dev.reboot.dto;

import java.math.BigDecimal;

/**
 * 设备数据聚合统计结果。
 *
 * @author hula0710
 * @since 2026-07-27
 */
public class DeviceDataStats {

    private BigDecimal avg;
    private BigDecimal min;
    private BigDecimal max;
    private long count;

    public BigDecimal getAvg() { return avg; }
    public void setAvg(BigDecimal avg) { this.avg = avg; }
    public BigDecimal getMin() { return min; }
    public void setMin(BigDecimal min) { this.min = min; }
    public BigDecimal getMax() { return max; }
    public void setMax(BigDecimal max) { this.max = max; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
