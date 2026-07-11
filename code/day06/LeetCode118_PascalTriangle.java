package code.day06;

import java.util.ArrayList;
import java.util.List;

/**
 * LeetCode 118. 杨辉三角。
 *
 * <p>给定一个非负整数 {@code numRows}，生成杨辉三角的前 {@code numRows} 行。</p>
 *
 * <p>杨辉三角中，每个数是它左上方和右上方的数的和。</p>
 *
 * <p>示例：{@code numRows=5}
 * <pre>{@code
 *      [1]
 *     [1,1]
 *    [1,2,1]
 *   [1,3,3,1]
 *  [1,4,6,4,1]
 * }</pre>
 *
 * <p>Day 3 我们用二维数组实现过，今天用 List 重新实现，练习集合 API。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class LeetCode118_PascalTriangle {

    /**
     * 生成杨辉三角的前 numRows 行。
     *
     * <p>算法：每一行的首尾都是 1，中间元素 = 上一行[j-1] + 上一行[j]。</p>
     *
     * @param numRows 行数
     * @return 杨辉三角的二维列表
     */
    public static List<List<Integer>> generate(int numRows) {
        List<List<Integer>> triangle = new ArrayList<>();

        for (int i = 0; i < numRows; i++) {
            List<Integer> row = new ArrayList<>();

            for (int j = 0; j <= i; j++) {
                if (j == 0 || j == i) {
                    row.add(1);           // 首尾是 1
                } else {
                    // 上一行的 j-1 和 j 位置之和
                    int val = triangle.get(i - 1).get(j - 1)
                            + triangle.get(i - 1).get(j);
                    row.add(val);
                }
            }

            triangle.add(row);
        }

        return triangle;
    }

    /**
     * 主方法 —— 测试多个用例。
     */
    public static void main(String[] args) {
        System.out.println("========== LeetCode 118. 杨辉三角 ==========\n");

        for (int n : new int[]{1, 3, 5, 10}) {
            System.out.println("numRows = " + n + ":");
            List<List<Integer>> triangle = generate(n);

            for (List<Integer> row : triangle) {
                // 居中打印
                int spaces = n - row.size();
                for (int s = 0; s < spaces; s++) System.out.print("  ");
                for (int val : row) {
                    System.out.printf("%4d", val);
                }
                System.out.println();
            }
            System.out.println();
        }

        System.out.println("--- Day3 vs Day6 对比 ---");
        System.out.println("Day 3: int[][] 二维数组（固定大小，需手动分配）");
        System.out.println("Day 6: List<List<Integer>>（动态大小，更灵活）");
        System.out.println("实际开发中优先使用集合框架（List/Map/Set）。");
    }
}
