package code.day04;

import java.util.Arrays;

/**
 * LeetCode 66. 加一。
 *
 * <p>给定一个由整数组成的非空数组所表示的非负整数，在该数的基础上加一。
 * 最高位数字存放在数组的首位，数组中每个元素只存储单个数字。
 * 你可以假设除了整数 0 之外，这个整数不会以零开头。</p>
 *
 * <p>示例：
 * <pre>{@code
 *   输入: [1,2,3]  → 输出: [1,2,4]  (123 + 1 = 124)
 *   输入: [9,9,9]  → 输出: [1,0,0,0]  (999 + 1 = 1000)
 * }</pre>
 *
 * <p>关键点：处理进位，特别是 999→1000 这种需要数组扩容的情况。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-09
 */
public class LeetCode66_PlusOne {

    /**
     * 给数组表示的整数加一并返回结果数组。
     *
     * <p>算法思路：
     * <ol>
     *   <li>从末位开始遍历</li>
     *   <li>如果当前位 &lt; 9，加一并返回</li>
     *   <li>如果是 9，置为 0，进位继续</li>
     *   <li>如果所有位都是 9（如 999），创建新数组，首位置 1</li>
     * </ol>
     *
     * @param digits 表示非负整数的数组，每个元素 0-9
     * @return 加一后的结果数组
     */
    public static int[] plusOne(int[] digits) {
        int n = digits.length;

        // 从末尾向前遍历
        for (int i = n - 1; i >= 0; i--) {
            if (digits[i] < 9) {
                digits[i]++;
                return digits;    // 无进位，直接返回
            }
            digits[i] = 0;        // 当前位是 9，变成 0，继续进位
        }

        // 能走到这里说明所有位都是 9（如 999 → 1000）
        int[] result = new int[n + 1];
        result[0] = 1;
        // 其余位默认是 0，不需要再赋值
        return result;
    }

    /**
     * 主方法 —— 测试多个用例。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        System.out.println("========== LeetCode 66. 加一 ==========\n");

        int[][] testCases = {
            {1, 2, 3},
            {4, 3, 2, 1},
            {9},
            {9, 9, 9},
            {8, 9, 9, 9}
        };

        for (int[] arr : testCases) {
            int[] result = plusOne(arr.clone());
            System.out.printf("输入: %-20s → 输出: %s%n",
                    Arrays.toString(arr), Arrays.toString(result));
        }

        System.out.println("\n--- 思路回顾 ---");
        System.out.println("时间复杂度 O(n)，空间复杂度 O(1)（99%情况原地修改）。");
        System.out.println("只有全 9 时需要创建新数组，空间 O(n)。");
        System.out.println("这道题的核心难点：处理进位链和数组扩容。");
    }
}
