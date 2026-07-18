package code.day06;

/**
 * 存储设备抽象类 —— 演示抽象类。
 *
 * <p>抽象类 vs 接口：</p>
 * <ul>
 *   <li>抽象类：可以有构造方法、成员变量、具体方法 + 抽象方法，单继承</li>
 *   <li>接口：只能有常量、抽象方法、default/static 方法，多实现</li>
 *   <li>抽象类 ="是什么"(is-a)，接口="能做什么"(can-do)</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public abstract class StorageDevice {

    protected int capacity;
    protected int used;

    public StorageDevice(int capacity) {
        this.capacity = capacity;
        this.used = 0;
    }

    public abstract String speedDescription();

    public boolean write(int size) {
        if (used + size > capacity) {
            System.out.println("写入失败：空间不足！");
            return false;
        }
        used += size;
        System.out.printf("写入 %dGB，剩余 %dGB%n", size, capacity - used);
        return true;
    }

    public int getFree() { return capacity - used; }
    public int getCapacity() { return capacity; }
}
