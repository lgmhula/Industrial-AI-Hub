package code.day13;

/**
 * LeetCode 125. 验证回文串。
 *
 * <p>如果在将所有大写字符转换为小写字符、并移除所有非字母数字字符之后，
 * 短语正着读和反着读都一样，则认为该短语是一个回文串。</p>
 *
 * <p>示例：
 * <pre>{@code
 *   "A man, a plan, a canal: Panama" → true
 *   "race a car" → false
 *   " " → true
 * }</pre>
 *
 * <p>双指针法 O(n)：左右指针向中间靠拢，跳过非字母数字字符。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-13
 */
public class LeetCode125_ValidPalindrome {

    /**
     * 双指针判断回文串。
     *
     * @param s 输入字符串
     * @return 是否为回文串
     */
    public static boolean isPalindrome(String s) {
        int left = 0, right = s.length() - 1;

        while (left < right) {
            char cl = s.charAt(left);
            char cr = s.charAt(right);

            // 跳过非字母数字
            if (!Character.isLetterOrDigit(cl)) {
                left++;
                continue;
            }
            if (!Character.isLetterOrDigit(cr)) {
                right--;
                continue;
            }

            // 转小写比较
            if (Character.toLowerCase(cl) != Character.toLowerCase(cr)) {
                return false;
            }

            left++;
            right--;
        }

        return true;
    }

    /** 简洁版：先过滤再反转比较 */
    public static boolean isPalindromeSimple(String s) {
        StringBuilder filtered = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                filtered.append(Character.toLowerCase(c));
            }
        }
        String forward = filtered.toString();
        String backward = filtered.reverse().toString();
        return forward.equals(backward);
    }

    public static void main(String[] args) {
        System.out.println("========== LeetCode 125. 验证回文串 ==========\n");

        String[] tests = {
            "A man, a plan, a canal: Panama",
            "race a car",
            " ",
            "0P",
            "ab_a"
        };

        for (String s : tests) {
            boolean r1 = isPalindrome(s);
            boolean r2 = isPalindromeSimple(s);
            System.out.printf("\"%s\" → 双指针:%s  过滤法:%s%n", s, r1, r2);
        }

        System.out.println("\n--- Character 工具方法 ---");
        System.out.println("Character.isLetterOrDigit(c) - 判断字母或数字");
        System.out.println("Character.toLowerCase(c)     - 转小写（处理 Unicode）");
        System.out.println("字符串回文比数字回文多一步：跳过非字母数字字符。");
    }
}
