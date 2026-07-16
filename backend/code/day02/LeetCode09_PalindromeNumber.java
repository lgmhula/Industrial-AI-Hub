package code.day02;

/**
 * LeetCode 9. 回文数
 * <p>
 * 给你一个整数 x ，如果 x 是一个回文整数，返回 {@code true}；否则，返回 {@code false}。
 * 回文数是指正序（从左向右）和倒序（从右向左）读都是一样的整数。
 * <p>
 * 示例：
 * <ul>
 *     <li>{@code 121} → {@code true}</li>
 *     <li>{@code -121} → {@code false}</li>
 *     <li>{@code 10} → {@code false}</li>
 * </ul>
 * <p>
 * 提供两种实现方式：
 * <ol>
 *     <li>{@link #isPalindromeString(int)} - 字符串双指针法</li>
 *     <li>{@link #isPalindromeMath(int)} - 数学反转法</li>
 * </ol>
 *
 * @author hula
 * @version 1.0
 * @see <a href="https://leetcode.cn/problems/palindrome-number/">LeetCode 9. 回文数</a>
 * @since 2026-07-07
 */
public class LeetCode09_PalindromeNumber {

    // ======================================================================
    // 方法一：字符串双指针法
    // ======================================================================

    /**
     * 方法一：将整数转为字符串，使用双指针从两端向中间比较字符
     * <p>
     * 算法步骤：
     * <ol>
     *     <li>负数直接返回 {@code false}</li>
     *     <li>将整数转为字符串</li>
     *     <li>左指针指向首字符，右指针指向尾字符</li>
     *     <li>循环比较，直到左右指针相遇</li>
     *     <li>若所有字符都相等，返回 {@code true}</li>
     * </ol>
     * <p>
     * 时间复杂度：O(n)，其中 n 为数字的位数
     * 空间复杂度：O(n)，需要存储字符串
     *
     * @param x 待判断的整数
     * @return 如果是回文数返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isPalindromeString(int x) {
        // 负数不是回文数
        if (x < 0) {
            return false;
        }

        String s = String.valueOf(x);
        int left = 0;
        int right = s.length() - 1;

        while (left < right) {
            if (s.charAt(left) != s.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }

    // ======================================================================
    // 方法二：数学反转法
    // ======================================================================

    /**
     * 方法二：不转字符串，通过数学运算反转数字的后半部分
     * <p>
     * 算法思路：反转后半部分数字，与前半部分比较。
     * <p>
     * 例如 {@code 1221}：
     * <ul>
     *     <li>原数 1221，反转半部分 12</li>
     *     <li>前半部分 12，反转后 12</li>
     *     <li>相等 → {@code true}</li>
     * </ul>
     * <p>
     * 例如 {@code 12321}：
     * <ul>
     *     <li>原数 12321，反转半部分 123</li>
     *     <li>前半部分 12，反转后 123</li>
     *     <li>{@code x == reversedHalf / 10} → 12 == 123/10(=12) → {@code true}</li>
     * </ul>
     * <p>
     * 算法步骤：
     * <ol>
     *     <li>负数 或 末尾为 0 且不为 0 本身，直接返回 {@code false}</li>
     *     <li>循环取出原数的最后一位，拼接到反转数</li>
     *     <li>当原数 <= 反转数时停止（已处理一半位数）</li>
     *     <li>判断偶数位或奇数位是否满足回文条件</li>
     * </ol>
     * <p>
     * 时间复杂度：O(log₁₀ n)，只处理数字的一半位数
     * 空间复杂度：O(1)，只使用常数个变量
     *
     * @param x 待判断的整数
     * @return 如果是回文数返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isPalindromeMath(int x) {
        // 负数 和 末尾是 0 但不是 0 本身的数，不是回文
        if (x < 0 || (x % 10 == 0 && x != 0)) {
            return false;
        }

        int reversedHalf = 0;
        // 当原数 <= 反转数时，已经处理了一半以上的位
        while (x > reversedHalf) {
            // 取出最后一位加到反转数
            reversedHalf = reversedHalf * 10 + x % 10;
            // 去除原数的最后一位
            x = x / 10;
        }

        // 偶数位: x == reversedHalf (如 1221 → x=12, reversedHalf=12)
        // 奇数位: x == reversedHalf / 10 (如 12321 → x=12, reversedHalf=123)
        return x == reversedHalf || x == reversedHalf / 10;
    }

    // ======================================================================
    // 程序入口
    // ======================================================================

    /**
     * 程序主入口
     * <p>
     * 测试指定测试用例，并验证两种方法在 0~1000 范围内结果是否一致。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        int[] testCases = {121, -121, 10, 1221, 12321, 0, 1001, 123};

        System.out.println("========== LeetCode 9. 回文数 ==========\n");
        System.out.println("测试用例:");
        System.out.println("┌────────┬──────────────┬────────────┐");
        System.out.println("│ 数字    │  字符串法     │  数学法     │");
        System.out.println("├────────┼──────────────┼────────────┤");

        for (int num : testCases) {
            boolean r1 = isPalindromeString(num);
            boolean r2 = isPalindromeMath(num);
            System.out.printf("│ %-6d │ %-12s │ %-10s │%n",
                    num, r1, r2);
        }
        System.out.println("└────────┴──────────────┴────────────┘");

        // 验证两种方法结果一致
        System.out.println("\n--- 全面验证 (0~1000) ---");
        boolean allMatch = true;
        int matchCount = 0;
        for (int i = 0; i <= 1000; i++) {
            if (isPalindromeString(i) == isPalindromeMath(i)) {
                matchCount++;
            } else {
                System.out.println("不一致: " + i);
                allMatch = false;
            }
        }
        if (allMatch) {
            System.out.printf("✅ 0~1000 范围内两种方法结果完全一致 (共 %d 个数字验证通过)%n", matchCount);
        }
    }
}
