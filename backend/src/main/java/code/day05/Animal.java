package code.day05;

/**
 * 动物基类 —— 演示继承体系中的父类。
 *
 * <p>作为所有动物的抽象基类，定义了共同的属性和行为：
 * <ul>
 *   <li>{@code name}：动物名称（protected，允许子类直接访问）</li>
 *   <li>{@code makeSound()}：发声方法（子类必须重写）</li>
 *   <li>{@code eat()}：进食方法（有默认实现，子类可选重写）</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-10
 */
public class Animal {

    /** 动物名称，protected 允许子类直接访问 */
    protected String name;

    /**
     * 构造方法。
     *
     * @param name 动物名称
     */
    public Animal(String name) {
        this.name = name;
    }

    /**
     * 发声 —— 子类必须重写此方法以提供具体声音。
     * <p>父类提供一个通用实现，但通常子类会覆盖。</p>
     */
    public void makeSound() {
        System.out.println(name + " 发出了一些声音...");
    }

    /**
     * 进食 —— 有默认实现，子类可以重写。
     */
    public void eat() {
        System.out.println(name + " 正在吃东西。");
    }

    /**
     * 获取动物名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }
}
