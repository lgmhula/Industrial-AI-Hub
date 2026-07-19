package code.day06;

/**
 * Day 06: 抽象类、接口、static、final。
 *
 * <p>演示：
 * <ol>
 *   <li>接口多态：USB 接口统一管理鼠标、键盘、移动SSD</li>
 *   <li>抽象类 vs 接口对比：StorageDevice 抽象类</li>
 *   <li>一个类既继承抽象类又实现接口（SSD）</li>
 *   <li>接口的 default 方法</li>
 * </ol>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class Day06_Interface {

    public static void main(String[] args) {
        usbDemo();
        abstractVsInterface();
    }

    /**
     * 接口多态演示：统一通过 USB 引用操作不同设备。
     */
    private static void usbDemo() {
        System.out.println("========== USB 接口多态 ==========\n");

        // 接口多态数组：所有实现了 USB 的类都可以放入
        USB[] devices = {
            new Mouse("罗技", 8000),
            new Keyboard("樱桃", true),
            new SSD("三星", 1024)
        };

        // 统一操作：所有 USB 设备都有 plugIn/transferData/plugOut
        for (USB dev : devices) {
            dev.plugIn();
            dev.deviceInfo();    // default 方法
            dev.transferData("test-data");
            dev.plugOut();
            System.out.println();
        }

        // 接口变量也可以引用 null
        System.out.println("--- 接口作为方法参数 ---");
        USB mouse = new Mouse("雷蛇", 16000);
        useDevice(mouse);
        useDevice(new Keyboard("Filco", true));
    }

    /**
     * 接受任何实现了 USB 接口的对象 —— 面向接口编程。
     */
    private static void useDevice(USB device) {
        System.out.print("使用设备: ");
        device.plugIn();
    }

    /**
     * 抽象类 vs 接口对比演示。
     */
    private static void abstractVsInterface() {
        System.out.println("\n========== 抽象类 vs 接口 ==========\n");

        // 抽象类不能直接 new：StorageDevice sd = new StorageDevice(500); ← 错误！
        HardDrive hdd = new HardDrive(2000);
        SSD ssd = new SSD("西数", 512);

        System.out.println(hdd.speedDescription());
        hdd.write(500);
        System.out.println("HDD 剩余: " + hdd.getFree() + "GB");

        System.out.println(ssd.speedDescription());
        ssd.write(200);

        // SSD 既是 StorageDevice，也是 USB
        System.out.println("\n--- SSD 的双重身份 ---");
        System.out.println("SSD is StorageDevice: " + (ssd instanceof StorageDevice));
        System.out.println("SSD is USB: " + (ssd instanceof USB));

        // 通过 USB 接口使用 SSD
        USB usbSSD = ssd;
        usbSSD.plugIn();
        // usbSSD.write(100); ← 不行！USB 接口没有 write 方法
        // 需要向下转型
        ((SSD) usbSSD).write(100);

        System.out.println("\n--- 关键结论 ---");
        System.out.println("抽象类 = '是什么'（is-a）：SSD 是一个 StorageDevice");
        System.out.println("接口   = '能做什么'（can-do）：SSD 是一个 USB 设备");
        System.out.println("一个类只能继承一个抽象类，但可以实现多个接口。");
    }
}
