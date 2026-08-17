package code.day06;

/**
 * 机械硬盘 —— 继承 {@link StorageDevice} 抽象类。
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class HardDrive extends StorageDevice {

    public HardDrive(int capacity) {
        super(capacity);
    }

    @Override
    public String speedDescription() {
        return "机械硬盘: 读写 ~150MB/s（磁头寻道）";
    }
}
