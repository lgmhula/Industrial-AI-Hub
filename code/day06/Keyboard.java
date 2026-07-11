package code.day06;

/**
 * 键盘 —— 实现 {@link USB} 接口。
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class Keyboard implements USB {

    private String brand;
    private boolean mechanical;

    public Keyboard(String brand, boolean mechanical) {
        this.brand = brand;
        this.mechanical = mechanical;
    }

    @Override
    public void plugIn() {
        System.out.println(brand + " 键盘（" + (mechanical ? "机械" : "薄膜")
                + "）已插入 —— RGB 灯亮起 ✨");
    }

    @Override
    public void plugOut() {
        System.out.println(brand + " 键盘已安全拔出，RGB 熄灭");
    }

    @Override
    public void transferData(String data) {
        System.out.println(brand + " 键盘输入: 「" + data + "」");
    }

    /** 键盘特有方法 */
    public void type(String text) {
        System.out.println(brand + " 键盘敲击: " + text + " ⌨️");
    }
}
