package code.day10;

import java.util.*;

/**
 * Day 10: 泛型 + Comparable/Comparator + 综合实战。
 *
 * <p>本日聚焦三个紧密关联的概念：
 * <ol>
 *   <li><b>泛型（Generics）</b>：类型参数化，编译期类型安全</li>
 *   <li><b>Comparable</b>：自然排序，类内部定义"默认怎么比"</li>
 *   <li><b>Comparator</b>：外部比较器，灵活定义"这次怎么比"</li>
 * </ol>
 *
 * <p>综合实战：斗地主发牌模拟（集合 + 排序 + 随机 + 泛型）。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-13
 */
public class Day10_Generics {

    public static void main(String[] args) {
        genericDemo();
        comparableDemo();
        comparatorDemo();
        pokerGame();
    }

    // ==================== 泛型 ====================

    /** 泛型类与泛型方法演示 */
    private static void genericDemo() {
        System.out.println("========== 泛型基础 ==========\n");

        // 泛型类：类型参数化
        Box<Integer> intBox = new Box<>(42);
        Box<String> strBox = new Box<>("Hello Generics");
        System.out.println("Integer 箱子: " + intBox.get());
        System.out.println("String 箱子:  " + strBox.get());

        // 泛型方法
        Integer[] nums = {1, 2, 3, 4, 5};
        String[] words = {"A", "B", "C"};
        System.out.println("数组中间元素 (Integer): " + getMiddle(nums));
        System.out.println("数组中间元素 (String):  " + getMiddle(words));

        // 通配符 ? —— 接受任何类型
        System.out.println("\n--- 通配符 ---");
        List<Integer> ints = List.of(1, 2, 3);
        List<Double> dbls = List.of(1.5, 2.5, 3.5);
        printList(ints);
        printList(dbls);

        // 上界通配符 ? extends Number —— 只能读
        System.out.println("数字列表求和: " + sumOfList(ints));
        System.out.println("数字列表求和: " + sumOfList(dbls));

        // 为什么需要泛型
        System.out.println("\n--- 为什么需要泛型？ ---");
        System.out.println("没有泛型时：List list = new ArrayList(); list.add(\"hello\");");
        System.out.println("              String s = (String) list.get(0);  ← 需要强制转型");
        System.out.println("有泛型后：  List<String> list = new ArrayList<>();");
        System.out.println("              String s = list.get(0);  ← 编译期类型安全，不需转型");
    }

    /** 泛型类：一个能装任何类型物品的箱子 */
    static class Box<T> {
        private T item;
        Box(T item) { this.item = item; }
        T get() { return item; }
    }

    /** 泛型方法：返回数组中间元素 */
    static <T> T getMiddle(T[] arr) {
        return arr[arr.length / 2];
    }

    /** 通配符：接受任何类型的 List */
    static void printList(List<?> list) {
        System.out.print("  ");
        list.forEach(e -> System.out.print(e + " "));
        System.out.println();
    }

    /** 上界通配符：只接受 Number 及其子类 */
    static double sumOfList(List<? extends Number> list) {
        double sum = 0;
        for (Number n : list) sum += n.doubleValue();
        return sum;
    }

    // ==================== Comparable ====================

    /** Comparable：自然排序 —— "这个类默认怎么排序" */
    private static void comparableDemo() {
        System.out.println("\n========== Comparable: 自然排序 ==========\n");

        List<Card> cards = new ArrayList<>();
        cards.add(new Card("♠", "K"));
        cards.add(new Card("♥", "5"));
        cards.add(new Card("♦", "A"));
        cards.add(new Card("♣", "10"));
        cards.add(new Card("♠", "2"));

        System.out.println("排序前: " + cards);
        Collections.sort(cards);  // Card 实现了 Comparable，按点数排
        System.out.println("排序后: " + cards + "  (按点数升序)");
    }

    // ==================== Comparator ====================

    /** Comparator：外部比较器 —— "这次我想按什么排" */
    private static void comparatorDemo() {
        System.out.println("\n========== Comparator: 外部比较器 ==========\n");

        List<Card> cards = new ArrayList<>();
        cards.add(new Card("♠", "K"));
        cards.add(new Card("♥", "5"));
        cards.add(new Card("♦", "A"));
        cards.add(new Card("♣", "10"));
        cards.add(new Card("♠", "2"));

        // 按花色排序（自定义顺序：♠ > ♥ > ♦ > ♣）
        Comparator<Card> bySuit = Comparator.comparingInt(c -> SUIT_ORDER.indexOf(c.suit));
        cards.sort(bySuit);
        System.out.println("按花色排序: " + cards);

        // 先花色再点数（多级排序）
        Comparator<Card> bySuitThenRank = bySuit.thenComparing(Card::rankValue);
        cards.sort(bySuitThenRank);
        System.out.println("先花色后点数: " + cards);

        // 降序
        cards.sort(Comparator.comparingInt(Card::rankValue).reversed());
        System.out.println("按点数降序: " + cards);
    }

    // ==================== 斗地主发牌 ====================

    /** 综合实战：斗地主发牌模拟 */
    private static void pokerGame() {
        System.out.println("\n========== 斗地主发牌模拟 ==========\n");

        // 1. 生成一副牌 (54张)
        List<Card> deck = new ArrayList<>();
        String[] suits = {"♠", "♥", "♦", "♣"};
        String[] ranks = {"3","4","5","6","7","8","9","10","J","Q","K","A","2"};
        for (String suit : suits) {
            for (String rank : ranks) {
                deck.add(new Card(suit, rank));
            }
        }
        deck.add(new Card("🃏", "小王"));
        deck.add(new Card("🃏", "大王"));

        System.out.println("新牌: " + deck.size() + " 张");

        // 2. 洗牌
        Collections.shuffle(deck);
        System.out.println("洗牌完成 ✓");

        // 3. 发牌：3个玩家 + 3张底牌
        List<Card> player1 = new ArrayList<>();
        List<Card> player2 = new ArrayList<>();
        List<Card> player3 = new ArrayList<>();
        List<Card> bottom = new ArrayList<>();

        for (int i = 0; i < deck.size(); i++) {
            if (i >= deck.size() - 3) {
                bottom.add(deck.get(i));  // 最后 3 张是底牌
            } else if (i % 3 == 0) {
                player1.add(deck.get(i));
            } else if (i % 3 == 1) {
                player2.add(deck.get(i));
            } else {
                player3.add(deck.get(i));
            }
        }

        // 4. 每个玩家把手牌排序（先花色后点数）
        Comparator<Card> sorter = Comparator
                .comparingInt((Card c) -> SUIT_ORDER.indexOf(c.suit))
                .thenComparing(Card::rankValue);

        player1.sort(sorter);
        player2.sort(sorter);
        player3.sort(sorter);

        // 5. 展示结果
        System.out.println("\n玩家1 (" + player1.size() + " 张): " + player1);
        System.out.println("玩家2 (" + player2.size() + " 张): " + player2);
        System.out.println("玩家3 (" + player3.size() + " 张): " + player3);
        System.out.println("底牌: " + bottom);
    }

    // ---- 辅助 ----
    static final List<String> SUIT_ORDER = List.of("♠", "♥", "♦", "♣");

    /** 扑克牌 —— 实现 Comparable 自然排序（按点数） */
    static class Card implements Comparable<Card> {
        String suit, rank;

        Card(String suit, String rank) { this.suit = suit; this.rank = rank; }

        /** 返回点数的数值，用于排序 */
        int rankValue() {
            return switch (rank) {
                case "3" -> 3;  case "4" -> 4;  case "5" -> 5;
                case "6" -> 6;  case "7" -> 7;  case "8" -> 8;
                case "9" -> 9;  case "10" -> 10; case "J" -> 11;
                case "Q" -> 12; case "K" -> 13; case "A" -> 14;
                case "2" -> 15; case "小王" -> 16; case "大王" -> 17;
                default -> 0;
            };
        }

        @Override
        public int compareTo(Card other) {
            return Integer.compare(this.rankValue(), other.rankValue());
        }

        @Override
        public String toString() { return suit + rank; }
    }
}
