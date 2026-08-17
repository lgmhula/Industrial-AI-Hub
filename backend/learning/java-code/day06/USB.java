package code.day06;

/**
 * USB 接口 —— 定义设备规范。
 *
 * <p>接口（interface）是一种"契约"：任何实现了此接口的类，
 * 必须提供接口中声明的方法的具体实现。</p>
 *
 * <p>接口的关键特性：
 * <ul>
 *   <li>所有方法默认是 {@code public abstract}</li>
 *   <li>所有字段默认是 {@code public static final}（常量）</li>
 *   <li>Java 8+ 支持 {@code default} 和 {@code static} 方法</li>
 *   <li>一个类可以实现多个接口（解决单继承的局限）</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public interface USB {

    /** USB 标准电压（常量，public static final） */
    double STANDARD_VOLTAGE = 5.0;

    /**
     * 插入设备 —— 每个 USB 设备必须实现的方法。
     */
    void plugIn();

    /**
     * 拔出设备 —— 每个 USB 设备必须实现的方法。
     */
    void plugOut();

    /**
     * 传输数据。
     *
     * @param data 要传输的数据
     */
    void transferData(String data);

    /**
     * Java 8 default 方法 —— 接口可以提供默认实现。
     * <p>子类可以选择重写，也可以直接用这个默认行为。</p>
     */
    default void deviceInfo() {
        System.out.println("USB 设备（标准电压: " + STANDARD_VOLTAGE + "V）");
    }
}
