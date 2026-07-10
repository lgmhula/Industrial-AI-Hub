package code.day05;

/**
 * 狗类 —— 继承自 {@link Animal}。
 *
 * <p>演示：
 * <ul>
 *   <li>{@code extends} 关键字实现继承</li>
 *   <li>{@code super()} 调用父类构造方法</li>
 *   <li>{@code @Override} 重写父类方法</li>
 *   <li>子类特有的方法 {@code wagTail()}</li>
 * </ul>
 *
 * @author hula
 * @version 1.0
 * @see Animal
 * @since 2026-07-10
 */
public class Dog extends Animal {

    /**
     * 品种
     */
    private String breed;

    /**
     * 构造方法 —— 通过 super() 调用父类构造。
     *
     * @param name  狗的名字
     * @param breed 品种
     */
    public Dog(String name, String breed) {
        super(name);  // 必须放在第一行，调用父类 Animal(name)
        this.breed = breed;
    }

    /**
     * 重写父类的发声方法。
     * <p>使用 {@code @Override} 注解让编译器检查是否正确重写。</p>
     */
    @Override
    public void makeSound() {
        System.out.println(name + "（" + breed + "）: 汪汪汪！🐕");
    }

    /**
     * 重写进食方法，并调用父类实现。
     * <p>{@code super.eat()} 可以复用父类逻辑，再加上子类特有行为。</p>
     */
    @Override
    public void eat() {
        super.eat();  // 先执行父类的吃
        System.out.println("  → " + name + " 摇着尾巴啃骨头！🦴");
    }

    /**
     * 子类特有方法 —— 摇尾巴。
     */
    public void wagTail() {
        System.out.println(name + " 开心地摇着尾巴～");
    }

    /**
     * 获取品种。
     *
     * @return 品种名
     */
    public String getBreed() {
        return breed;
    }
}
