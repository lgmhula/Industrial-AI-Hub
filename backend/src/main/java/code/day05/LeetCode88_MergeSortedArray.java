package code.day05;

import java.util.Arrays;

/**
 * LeetCode 88. 合并两个有序数组。
 *
 * <p>给你两个按非递减顺序排列的整数数组 {@code nums1} 和 {@code nums2}，
 * 另有两个整数 {@code m} 和 {@code n}，分别表示 {@code nums1} 和 {@code nums2}
 * 中的有效元素数目。请你合并 {@code nums2} 到 {@code nums1} 中，使结果依然有序。</p>
 *
 * <p>{@code nums1} 的长度为 {@code m + n}，其中前 {@code m} 个元素是有效的，
 * 后 {@code n} 个元素为 0，应被忽略。{@code nums2} 的长度为 {@code n}。</p>
 *
 * <p>要求：<b>原地修改</b>，不使用额外数组。</p>
 *
 * <p>示例：
 * <pre>{@code
 *   nums1 = [1,2,3,0,0,0], m=3
 *   nums2 = [2,5,6],       n=3
 *   输出: [1,2,2,3,5,6]
 * }</pre>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-10
 */
public class LeetCode88_MergeSortedArray {

    /**
     * 从后往前填充 —— 避免覆盖 nums1 未处理的元素。
     *
     * <p><b>为什么从后往前？</b>
     * 因为 nums1 后半部分是空闲的（值为 0），从后往前填充不会
     * 覆盖还没处理的 nums1 有效元素。</p>
     *
     * <p><b>算法步骤：</b>
     * <ol>
     *   <li>p1 = m - 1，指向 nums1 有效部分的末尾</li>
     *   <li>p2 = n - 1，指向 nums2 的末尾</li>
     *   <li>p = m + n - 1，指向填充位置</li>
     *   <li>比较 nums1[p1] 和 nums2[p2]，大的放 p 位置</li>
     *   <li>如果 p2 还有剩余，直接拷贝</li>
     * </ol>
     *
     * @param nums1 第一个数组（长度 m+n）
     * @param m     nums1 有效元素个数
     * @param nums2 第二个数组
     * @param n     nums2 有效元素个数
     */
    public static void merge(int[] nums1, int m, int[] nums2, int n) {
        int p1 = m - 1;       // nums1 有效部分的末尾
        int p2 = n - 1;       // nums2 的末尾
        int p = m + n - 1;    // 填充位置（从末尾开始）

        // 从后往前，每次放较大的那个
        while (p1 >= 0 && p2 >= 0) {
            if (nums1[p1] > nums2[p2]) {
                nums1[p] = nums1[p1];
                p1--;
            } else {
                nums1[p] = nums2[p2];
                p2--;
            }
            p--;
        }

        // 如果 nums2 还有剩余（nums1 剩下了没关系，本来就在正确位置）
        while (p2 >= 0) {
            nums1[p] = nums2[p2];
            p2--;
            p--;
        }
    }

    /**
     * 主方法 —— 测试多个用例。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        System.out.println("========== LeetCode 88. 合并两个有序数组 ==========\n");

        // 测试用例：(nums1, m, nums2, n, 期望结果)
        Object[][] tests = {
            {new int[]{1,2,3,0,0,0}, 3, new int[]{2,5,6}, 3, "[1, 2, 2, 3, 5, 6]"},
            {new int[]{1}, 1, new int[]{}, 0, "[1]"},
            {new int[]{0}, 0, new int[]{1}, 1, "[1]"},
            {new int[]{4,5,6,0,0,0}, 3, new int[]{1,2,3}, 3, "[1, 2, 3, 4, 5, 6]"}
        };

        for (Object[] t : tests) {
            int[] nums1 = (int[]) t[0];
            int m = (int) t[1];
            int[] nums2 = (int[]) t[2];
            int n = (int) t[3];
            String expected = (String) t[4];

            int[] copy = nums1.clone();
            merge(copy, m, nums2, n);
            String result = Arrays.toString(copy);

            System.out.printf("nums1=%s(m=%d) + nums2=%s(n=%d)%n",
                    Arrays.toString(nums1), m, Arrays.toString(nums2), n);
            System.out.printf("  → %s %s%n%n", result,
                    result.equals(expected) ? "✅" : "❌ 期望 " + expected);
        }

        System.out.println("--- 思路回顾 ---");
        System.out.println("关键：从后往前填，避免覆盖未处理的元素。");
        System.out.println("时间 O(m+n)，空间 O(1) 原地操作。");
    }
}
