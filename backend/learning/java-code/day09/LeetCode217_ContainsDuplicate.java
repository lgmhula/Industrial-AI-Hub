package code.day09;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * LeetCode 217. 存在重复元素。
 *
 * <p>给你一个整数数组 {@code nums}。如果任一值在数组中出现至少两次，
 * 返回 {@code true}；如果数组中每个元素互不相同，返回 {@code false}。</p>
 *
 * <p>示例：
 * <pre>{@code
 *   [1,2,3,1] → true  (1 重复)
 *   [1,2,3,4] → false (全部唯一)
 * }</pre>
 *
 * <p>三种解法：
 * <ul>
 *   <li>HashSet：O(n) 时间，O(n) 空间 —— 最优</li>
 *   <li>排序后比较相邻：O(n log n)，O(1) 空间</li>
 *   <li>暴力双重循环：O(n²) —— 仅供对比</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class LeetCode217_ContainsDuplicate {

    /**
     * HashSet 解法 O(n)。
     * <p>遍历数组，如果元素已经在 set 中，说明重复。</p>
     */
    public static boolean containsDuplicate(int[] nums) {
        Set<Integer> seen = new HashSet<>();
        for (int num : nums) {
            if (!seen.add(num)) {  // add 返回 false 表示已存在
                return true;
            }
        }
        return false;
    }

    /**
     * 排序解法 O(n log n)。
     * <p>排序后，相同元素必定相邻。</p>
     */
    public static boolean containsDuplicateSort(int[] nums) {
        Arrays.sort(nums);
        for (int i = 1; i < nums.length; i++) {
            if (nums[i] == nums[i - 1]) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        System.out.println("========== LeetCode 217. 存在重复元素 ==========\n");

        int[][] tests = {
            {1, 2, 3, 1},
            {1, 2, 3, 4},
            {1, 1, 1, 3, 3, 4, 3, 2, 4, 2},
            {}
        };

        for (int[] arr : tests) {
            boolean r1 = containsDuplicate(arr.clone());
            boolean r2 = containsDuplicateSort(arr.clone());
            System.out.printf("输入: %-30s  HashSet: %-6s  排序法: %-6s%n",
                    Arrays.toString(arr), r1, r2);
        }

        System.out.println("\n--- 关键技巧 ---");
        System.out.println("Set.add() 返回 false = 元素已存在，一行判断重复。");
        System.out.println("这是 HashSet 在算法中最常见的用法之一。");
    }
}
