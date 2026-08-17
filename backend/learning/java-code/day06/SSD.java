package code.day06;

/**
 * 固态硬盘 —— 继承抽象类 {@link StorageDevice}，同时实现 {@link USB} 接口。
 *
 * <p>演示一个类可以：既继承抽象类，又实现接口（多接口能力）。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class SSD extends StorageDevice implements USB {

    private String brand;

    public SSD(String brand, int capacity) {
        super(capacity);
        this.brand = brand;
    }

    @Override
    public String speedDescription() {
        return brand + " SSD: 读写 ~3500MB/s（NVMe）";
    }

    @Override
    public void plugIn() {
        System.out.println(brand + " 移动固态已连接");
    }

    @Override
    public void plugOut() {
        System.out.println(brand + " 移动固态已安全弹出");
    }

    @Override
    public void transferData(String data) {
        System.out.println(brand + " SSD 高速传输: " + data);
    }
}
