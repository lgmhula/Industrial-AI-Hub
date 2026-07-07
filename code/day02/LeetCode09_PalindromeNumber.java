/**
 * LeetCode 9. 回文数
 *
 * 给你一个整数 x ，如果 x 是一个回文整数，返回 true ；否则，返回 false 。
 * 回文数是指正序（从左向右）和倒序（从右向左）读都是一样的整数。
 * 例如：121 → true，-121 → false，10 → false
 *
 * 运行：javac LeetCode09_PalindromeNumber.java && java LeetCode09_PalindromeNumber
 */
public class LeetCode09_PalindromeNumber {

    // 方法一：转字符串
    // 将整数转为字符串，双指针从两端向中间比较
    public static boolean isPalindromeString(int x) {
        if (x < 0) {
            return false; // 负数不是回文
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

    // 方法二：数学反转（不转字符串）
    // 翻转数字的后半部分，与前半部分比较
    // 例如 1221：后半反转 = 12，前半 = 12，相等
    public static boolean isPalindromeMath(int x) {
        // 负数 和 末尾是 0 但不是 0 本身的数，不是回文
        if (x < 0 || (x % 10 == 0 && x != 0)) {
            return false;
        }

        int reversedHalf = 0;
        // 当原数 <= 反转数时，已经处理了一半以上的位
        while (x > reversedHalf) {
            reversedHalf = reversedHalf * 10 + x % 10; // 取出最后一位加到反转数
            x = x / 10;                                 // 去除原数的最后一位
        }

        // 偶数位: x == reversedHalf (如 1221 → x=12, rev=12)
        // 奇数位: x == reversedHalf / 10 (如 12321 → x=12, rev=123)
        return x == reversedHalf || x == reversedHalf / 10;
    }

    public static void main(String[] args) {
        int[] testCases = {121, -121, 10, 1221, 12321, 0, 1001, 123};

        System.out.println("========== LeetCode 9. 回文数 ==========\n");

        for (int num : testCases) {
            boolean r1 = isPalindromeString(num);
            boolean r2 = isPalindromeMath(num);
            System.out.printf("num=%-6d  字符串法: %-6s  数学法: %-6s%n",
                    num, r1, r2);
        }

        // 验证两种方法结果一致
        System.out.println("\n--- 全面验证 (0~1000) ---");
        boolean allMatch = true;
        for (int i = 0; i <= 1000; i++) {
            if (isPalindromeString(i) != isPalindromeMath(i)) {
                System.out.println("不一致: " + i);
                allMatch = false;
            }
        }
        if (allMatch) {
            System.out.println("0~1000 范围内两种方法结果完全一致 ✓");
        }
    }
}
