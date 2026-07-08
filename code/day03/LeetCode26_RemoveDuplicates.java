/**
 * LeetCode 26. 删除有序数组中的重复项
 *
 * 给你一个非严格递增排列的数组 nums，请你原地删除重复出现的元素，
 * 使每个元素只出现一次，返回删除后数组的新长度。
 * 必须原地修改，不能使用额外数组空间。O(1) 额外空间。
 *
 * 示例：nums = [0,0,1,1,1,2,2,3,3,4]
 * 输出：5, nums 的前五个元素为 [0,1,2,3,4]
 *
 * 运行：javac LeetCode26_RemoveDuplicates.java && java LeetCode26_RemoveDuplicates
 */
import java.util.Arrays;

public class LeetCode26_RemoveDuplicates {

    // 双指针法 O(n)
    // slow 指向"已去重区域"的末尾
    // fast 遍历整个数组
    // 当 nums[fast] != nums[slow] 时，说明发现新元素，slow++ 然后赋值
    public static int removeDuplicates(int[] nums) {
        if (nums.length == 0) return 0;

        int slow = 0; // 已去重区域的最后一个位置

        for (int fast = 1; fast < nums.length; fast++) {
            if (nums[fast] != nums[slow]) {
                slow++;
                nums[slow] = nums[fast];
            }
        }

        return slow + 1; // 长度 = 索引 + 1
    }

    // 另一种写法：更直观的双指针
    public static int removeDuplicatesV2(int[] nums) {
        if (nums.length == 0) return 0;

        int unique = 1; // 下一个唯一元素要放的位置

        for (int i = 1; i < nums.length; i++) {
            // 当前元素 != 前一个元素 → 是新的唯一元素
            if (nums[i] != nums[i - 1]) {
                nums[unique] = nums[i];
                unique++;
            }
        }

        return unique;
    }

    public static void main(String[] args) {
        System.out.println("========== LeetCode 26. 删除有序数组中的重复项 ==========\n");

        // 测试用例
        int[][] testCases = {
            {0, 0, 1, 1, 1, 2, 2, 3, 3, 4},
            {1, 1, 2},
            {1, 2, 3, 4, 5},
            {1, 1, 1, 1, 1},
            {}
        };

        for (int[] arr : testCases) {
            // 复制一份给方法一
            int[] nums1 = Arrays.copyOf(arr, arr.length);
            int[] nums2 = Arrays.copyOf(arr, arr.length);

            int len1 = removeDuplicates(nums1);
            int len2 = removeDuplicatesV2(nums2);

            System.out.println("输入:     " + Arrays.toString(arr));
            System.out.print("  方法一: ");
            printArray(nums1, len1);
            System.out.println("  (长度=" + len1 + ")");
            System.out.print("  方法二: ");
            printArray(nums2, len2);
            System.out.println("  (长度=" + len2 + ")");
            System.out.println();
        }

        // 双指针思路讲解
        System.out.println("--- 双指针思路 ---");
        System.out.println("slow: 指向已去重区域的末尾（实际效果类似\"新数组的写入位置\"）");
        System.out.println("fast: 扫描整个数组，发现新元素就写到 slow 的下一个位置");
        System.out.println("因为是原地修改，空间复杂度 O(1)。");
        System.out.println("双指针是数组/链表题最常见的优化技巧。");
    }

    static void printArray(int[] nums, int len) {
        System.out.print("[");
        for (int i = 0; i < len; i++) {
            System.out.print(nums[i]);
            if (i < len - 1) System.out.print(", ");
        }
        System.out.print("]");
    }
}
