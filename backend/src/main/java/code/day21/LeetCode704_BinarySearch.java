package code.day21;

/**
 * LeetCode 704 —— 二分查找。
 *
 * <p>给定有序数组和目标值，返回下标，不存在返回 -1。</p>
 *
 * <p>核心：while(left &lt;= right) + mid = left + (right - left) / 2 防止溢出。</p>
 *
 * @author hula0710
 * @since 2026-07-19
 */
public class LeetCode704_BinarySearch {

    /**
     * 迭代二分查找。
     *
     * @param nums   升序数组
     * @param target 目标值
     * @return 目标值下标，-1 表示不存在
     */
    public int search(int[] nums, int target) {
        int left = 0;
        int right = nums.length - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (nums[mid] == target) {
                return mid;
            } else if (nums[mid] < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        LeetCode704_BinarySearch sol = new LeetCode704_BinarySearch();
        int[] nums = {-1, 0, 3, 5, 9, 12};

        System.out.println("=== LeetCode 704: Binary Search ===");
        System.out.println("search 9  → " + sol.search(nums, 9));   // 4
        System.out.println("search 2  → " + sol.search(nums, 2));   // -1
        System.out.println("search -1 → " + sol.search(nums, -1));  // 0
        System.out.println("search 12 → " + sol.search(nums, 12));  // 5
    }
}
