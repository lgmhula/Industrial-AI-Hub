package code.day05;

/**
 * 猫类 —— 继承自 {@link Animal}。
 *
 * <p>演示与方法重写的另一种风格，以及子类特有的数据（剩余生命数）。</p>
 *
 * @author Reboot
 * @version 1.0
 * @see Animal
 * @since 2026-07-10
 */
public class Cat extends Animal {

    /** 猫的剩余生命数（传说的九条命） */
    private int lives;

    /**
     * 构造方法。
     *
     * @param name  猫的名字
     * @param lives 剩余生命数
     */
    public Cat(String name, int lives) {
        super(name);
        this.lives = lives;
    }

    /**
     * 重写发声。
     */
    @Override
    public void makeSound() {
        System.out.println(name + "（还剩" + lives + "条命）: 喵喵喵～🐱");
    }

    /**
     * 重写进食 —— 完全覆盖，不调用 super。
     */
    @Override
    public void eat() {
        System.out.println(name + " 优雅地舔着牛奶...🥛");
    }

    /**
     * 子类特有方法。
     */
    public void scratch() {
        System.out.println(name + " 伸了个懒腰，磨了磨爪子。");
    }

    public int getLives() {
        return lives;
    }
}
