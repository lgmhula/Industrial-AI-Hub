package code.day03;
/**
 * Day 03: 数组全面恢复
 * 内容：一维/二维数组、遍历、排序算法（手写）、Arrays 工具类、杨辉三角
 * <p>
 * 运行：javac Day03_Arrays.java && java Day03_Arrays
 */

import java.util.Arrays;

public class Day03_Arrays {

    public static void main(String[] args) {
        arrayBasics();      // 练习1: 基本操作
        bubbleSort();       // 练习2: 冒泡排序
        selectionSort();    // 练习3: 选择排序
        arrayReverse();     // 练习4: 数组反转
        yanghuiTriangle();  // 练习5: 杨辉三角
    }

    // ---- 练习 1：数组基本操作 ----
    static void arrayBasics() {
        System.out.println("========== 练习 1: 数组基本操作 ==========");

        // 三种声明方式
        int[] arr1 = {5, 2, 8, 1, 9, 3};
        int[] arr2 = new int[]{10, 20, 30, 40, 50};
        // 默认全 0
        int[] arr3 = new int[5];

        System.out.println("arr1: " + Arrays.toString(arr1));
        System.out.println("arr2: " + Arrays.toString(arr2));
        System.out.println("arr3 (默认值): " + Arrays.toString(arr3));
        System.out.println("arr1 长度: " + arr1.length);

        // 遍历数组
        System.out.print("\nfor-i 遍历: ");
        for (int i = 0; i < arr1.length; i++) {
            System.out.print(arr1[i] + " ");
        }

        System.out.print("\nfor-each 遍历: ");
        for (int num : arr1) {
            System.out.print(num + " ");
        }

        // 求和、最大值、最小值
        int sum = 0, max = arr1[0], min = arr1[0];
        for (int i = 0; i < arr1.length; i++) {
            sum += arr1[i];
            if (arr1[i] > max) max = arr1[i];
            if (arr1[i] < min) min = arr1[i];
        }
        System.out.printf("\n\n求和: %d | 最大: %d | 最小: %d | 平均: %.2f%n",
                    sum, max, min, (double) sum / arr1.length);

        // 查找指定元素
        int target = 8;
        int index = -1;
        for (int i = 0; i < arr1.length; i++) {
            if (arr1[i] == target) {
                index = i;
                break;
            }
        }
        System.out.println(target + " 的位置: " + (index != -1 ? "索引 " + index : "未找到"));

        // Arrays 工具类常用方法
        System.out.println("\n--- Arrays 工具类 ---");
        int[] copy = Arrays.copyOf(arr1, arr1.length);
        Arrays.sort(copy);
        System.out.println("排序后:   " + Arrays.toString(copy));

        int pos = Arrays.binarySearch(copy, 5);
        System.out.println("二分查找 5: 索引 " + pos);

        int[] filled = new int[5];
        Arrays.fill(filled, 42);
        System.out.println("fill 填充: " + Arrays.toString(filled));

        System.out.println("arr1 和 copy 相等? " + Arrays.equals(arr1, copy));
    }

    // ---- 练习 2：冒泡排序（手写） ----
    static void bubbleSort() {
        System.out.println("\n========== 练习 2: 冒泡排序 ==========");

        int[] arr = {64, 34, 25, 12, 22, 11, 90};
        System.out.println("排序前: " + Arrays.toString(arr));

        // 冒泡排序：每次将最大的元素"冒"到末尾
        // 外层：控制轮数（n-1 轮）
        // 内层：相邻比较，前 > 后则交换
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            // 优化：如果一轮没有交换，说明已经有序
            boolean swapped = false;

            for (int j = 0; j < n - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    // 交换
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                    swapped = true;
                }
            }

            System.out.printf("第 %d 轮: %s%n", i + 1, Arrays.toString(arr));

            if (!swapped) {
                System.out.println("  已有序，提前结束。");
                break;
            }
        }

        System.out.println("排序后: " + Arrays.toString(arr));
        System.out.println("时间复杂度: O(n²) | 空间: O(1) | 稳定排序");
    }

    // ---- 练习 3：选择排序（手写） ----
    static void selectionSort() {
        System.out.println("\n========== 练习 3: 选择排序 ==========");

        int[] arr = {29, 10, 14, 37, 13};
        System.out.println("排序前: " + Arrays.toString(arr));

        // 选择排序：每次从未排序部分选最小的，放到已排序末尾
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            // 假设当前位置是最小的
            int minIndex = i;
            // 在剩余部分找真正的最小值
            for (int j = i + 1; j < n; j++) {
                if (arr[j] < arr[minIndex]) {
                    minIndex = j;
                }
            }
            // 如果找到更小的，交换
            if (minIndex != i) {
                int temp = arr[i];
                arr[i] = arr[minIndex];
                arr[minIndex] = temp;
            }

            System.out.printf("第 %d 轮: %s%n", i + 1, Arrays.toString(arr));
        }

        System.out.println("排序后: " + Arrays.toString(arr));
        System.out.println("时间复杂度: O(n²) | 空间: O(1) | 不稳定排序");
    }

    // ---- 练习 4：数组反转 ----
    static void arrayReverse() {
        System.out.println("\n========== 练习 4: 数组反转 ==========");

        int[] arr = {1, 2, 3, 4, 5, 6, 7};
        System.out.println("反转前: " + Arrays.toString(arr));

        // 双指针：左右交换，向中间靠拢
        int left = 0;
        int right = arr.length - 1;

        while (left < right) {
            // 交换 arr[left] 和 arr[right]
            int temp = arr[left];
            arr[left] = arr[right];
            arr[right] = temp;

            left++;
            right--;
        }

        System.out.println("反转后: " + Arrays.toString(arr));

        // 用 for 循环实现同样效果
        int[] arr2 = {10, 20, 30, 40, 50};
        System.out.println("\nfor 循环版本:");
        System.out.println("反转前: " + Arrays.toString(arr2));
        for (int i = 0; i < arr2.length / 2; i++) {
            int temp = arr2[i];
            arr2[i] = arr2[arr2.length - 1 - i];
            arr2[arr2.length - 1 - i] = temp;
        }
        System.out.println("反转后: " + Arrays.toString(arr2));
    }

    // ---- 练习 5：杨辉三角 ----
    static void yanghuiTriangle() {
        System.out.println("\n========== 练习 5: 杨辉三角 ==========");

        int rows = 10;
        // 二维数组：每行长度递增
        int[][] triangle = new int[rows][];

        for (int i = 0; i < rows; i++) {
            // 第 i 行有 i+1 个元素
            triangle[i] = new int[i + 1];
            // 首尾都是 1
            triangle[i][0] = 1;
            triangle[i][i] = 1;

            // 中间元素 = 左上 + 右上
            for (int j = 1; j < i; j++) {
                triangle[i][j] = triangle[i - 1][j - 1] + triangle[i - 1][j];
            }
        }

        // 打印
        for (int i = 0; i < rows; i++) {
            // 居中对齐：打前置空格
            int spaces = rows - i;
            for (int s = 0; s < spaces; s++) {
                System.out.print("  ");
            }
            for (int j = 0; j < triangle[i].length; j++) {
                System.out.printf("%4d", triangle[i][j]);
            }
            System.out.println();
        }

        // 回顾二维数组的声明方式
        System.out.println("\n--- 二维数组声明方式 ---");
        // 3行4列, 规则矩形
        int[][] grid1 = new int[3][4];
        // 不规则的, 类似杨辉三角
        int[][] grid2 = {{1, 2}, {3, 4, 5}};
        System.out.println("grid1 行数: " + grid1.length + ", 列数: " + grid1[0].length);
        System.out.println("grid2 行数: " + grid2.length + ", 第0行列数: " + grid2[0].length
                + ", 第1行列数: " + grid2[1].length);
        System.out.println("不规则二维数组也叫'锯齿数组'(jagged array)。");
    }
}
