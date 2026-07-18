/**
 * LeetCode 1. 两数之和
 * <p>
 * 给定一个整数数组 nums 和一个整数目标值 target，
 * 请你在该数组中找出和为目标值的那两个整数，并返回它们的数组下标。
 * <p>
 * 你可以假设每种输入只会对应一个答案，且不能重复使用同一个元素。
 * <p>
 * 运行：javac LeetCode01_TwoSum.java && java LeetCode01_TwoSum
 */

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LeetCode01_TwoSum {

    // 方法一：暴力枚举 O(n²)
    // 双重 for 循环，逐个检查每一对数字
    public static int[] twoSumBruteForce(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                    return new int[]{i, j};
                }
            }
        }
        return new int[]{-1, -1}; // 没找到
    }

    // 方法二：哈希表 O(n)
    // 一边遍历一边用 HashMap 记录已遍历的值和下标
    // 每次检查 (target - 当前值) 是否已经在 map 里
    public static int[] twoSumHash(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[]{map.get(complement), i};
            }
            map.put(nums[i], i);
        }
        return new int[]{-1, -1};
    }

    public static void main(String[] args) {
        int[] nums = {2, 7, 11, 15};
        int target = 9;

        System.out.println("========== LeetCode 1. 两数之和 ==========");
        System.out.println("数组: " + Arrays.toString(nums));
        System.out.println("目标: " + target);
        System.out.println();

        // 暴力法
        int[] result1 = twoSumBruteForce(nums, target);
        System.out.println("暴力法: [" + result1[0] + ", " + result1[1] + "]");
        System.out.println("  验证: nums[" + result1[0] + "] + nums[" + result1[1] + "] = "
                + nums[result1[0]] + " + " + nums[result1[1]] + " = " + target);

        // 哈希表法
        int[] result2 = twoSumHash(nums, target);
        System.out.println("哈希法: [" + result2[0] + ", " + result2[1] + "]");
        System.out.println("  验证: nums[" + result2[0] + "] + nums[" + result2[1] + "] = "
                + nums[result2[0]] + " + " + nums[result2[1]] + " = " + target);

        // 再测试一组
        int[] nums2 = {3, 2, 4};
        int target2 = 6;
        System.out.println("\n--- 额外测试 ---");
        System.out.println("数组: " + Arrays.toString(nums2) + ", 目标: " + target2);
        int[] r = twoSumHash(nums2, target2);
        System.out.println("结果: [" + r[0] + ", " + r[1] + "]");
        System.out.println("  验证: " + nums2[r[0]] + " + " + nums2[r[1]] + " = " + target2);
    }
}
