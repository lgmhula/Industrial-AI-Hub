package code.day10;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * LeetCode 242. 有效的字母异位词。
 *
 * <p>给定两个字符串 {@code s} 和 {@code t}，编写一个函数来判断
 * {@code t} 是否是 {@code s} 的字母异位词。</p>
 *
 * <p>字母异位词：两个字符串包含的字母及数量完全相同，只是排列顺序不同。
 * 如 "anagram" 和 "nagaram"。</p>
 *
 * <p>三种解法：
 * <ul>
 *   <li><b>排序法</b>：排序后比较，O(n log n)</li>
 *   <li><b>HashMap 计数</b>：统计频率后比较，O(n)</li>
 *   <li><b>数组计数</b>（最优）：利用只有 26 个小写字母，int[26]，O(n)</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-13
 */
public class LeetCode242_ValidAnagram {

    /** 排序法 */
    public static boolean isAnagramSort(String s, String t) {
        if (s.length() != t.length()) return false;
        char[] sa = s.toCharArray();
        char[] ta = t.toCharArray();
        Arrays.sort(sa);
        Arrays.sort(ta);
        return Arrays.equals(sa, ta);
    }

    /** HashMap 计数法 */
    public static boolean isAnagramMap(String s, String t) {
        if (s.length() != t.length()) return false;

        Map<Character, Integer> count = new HashMap<>();
        for (char c : s.toCharArray()) {
            count.merge(c, 1, Integer::sum);
        }
        for (char c : t.toCharArray()) {
            int remaining = count.getOrDefault(c, 0);
            if (remaining == 0) return false;
            count.put(c, remaining - 1);
        }
        return true;
    }

    /** 数组计数法 —— 最优（仅限小写字母） */
    public static boolean isAnagramArray(String s, String t) {
        if (s.length() != t.length()) return false;

        int[] count = new int[26];
        for (char c : s.toCharArray()) count[c - 'a']++;
        for (char c : t.toCharArray()) {
            if (--count[c - 'a'] < 0) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println("========== LeetCode 242. 有效的字母异位词 ==========\n");

        String[][] tests = {
            {"anagram", "nagaram"},
            {"rat", "car"},
            {"a", "ab"},
            {"aacc", "ccac"}
        };

        for (String[] pair : tests) {
            boolean r1 = isAnagramSort(pair[0], pair[1]);
            boolean r2 = isAnagramMap(pair[0], pair[1]);
            boolean r3 = isAnagramArray(pair[0], pair[1]);
            System.out.printf("\"%s\" vs \"%s\" → 排序:%s Map:%s Array:%s%n",
                    pair[0], pair[1], r1, r2, r3);
        }

        System.out.println("\n--- 解法对比 ---");
        System.out.println("排序法: O(n log n) - 通用但慢");
        System.out.println("HashMap: O(n) - 通用且直观");
        System.out.println("数组法: O(n) + O(1) 空间 - 最快，但只适用于小写字母");
    }
}
