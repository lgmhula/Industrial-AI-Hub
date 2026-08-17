package code.day06;

/**
 * 鼠标 —— 实现 {@link USB} 接口。
 *
 * <p>演示 {@code implements} 关键字实现接口。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class Mouse implements USB {

    private String brand;
    private int dpi;

    public Mouse(String brand, int dpi) {
        this.brand = brand;
        this.dpi = dpi;
    }

    /** 实现接口的 plugIn 方法 */
    @Override
    public void plugIn() {
        System.out.println(brand + " 鼠标 (" + dpi + "DPI) 已插入 —— 哒哒");
    }

    /** 实现接口的 plugOut 方法 */
    @Override
    public void plugOut() {
        System.out.println(brand + " 鼠标已安全拔出");
    }

    /** 实现接口的 transferData 方法 */
    @Override
    public void transferData(String data) {
        System.out.println(brand + " 鼠标传输: 坐标 " + data);
    }

    /** 重写 default 方法 */
    @Override
    public void deviceInfo() {
        System.out.println(brand + " 鼠标 | " + dpi + "DPI | USB " + STANDARD_VOLTAGE + "V");
    }

    /** 鼠标特有方法 */
    public void click() {
        System.out.println(brand + " 鼠标: 点击！🖱️");
    }
}
