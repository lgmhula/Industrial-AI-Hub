package code.day11;

/**
 * LeetCode 125. 验证回文串。
 *
 * <p>如果在将所有大写字符转换为小写字符、并移除所有非字母数字字符之后，
 * 短语正着读和反着读都一样，则可以认为该短语是一个回文串。</p>
 *
 * <p>本题练习两个重点：
 * <ul>
 *   <li>String 与 Character 常用 API</li>
 *   <li>双指针思想：left/right 从两端向中间移动</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-13
 */
public class LeetCode125_ValidPalindrome {

    /**
     * 解法一：清洗字符串后反转比较。
     *
     * <p>优点：容易理解。
     * 缺点：需要额外字符串空间。</p>
     */
    public static boolean isPalindromeByBuilder(String s) {
        StringBuilder cleaned = new StringBuilder();

        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                cleaned.append(Character.toLowerCase(c));
            }
        }

        String forward = cleaned.toString();
        String backward = cleaned.reverse().toString();
        return forward.equals(backward);
    }

    /**
     * 解法二：双指针。
     *
     * <p>时间复杂度 O(n)，空间复杂度 O(1)。这是面试中更推荐的写法。</p>
     */
    public static boolean isPalindromeTwoPointers(String s) {
        int left = 0;
        int right = s.length() - 1;

        while (left < right) {
            char leftChar = s.charAt(left);
            char rightChar = s.charAt(right);

            if (!Character.isLetterOrDigit(leftChar)) {
                left++;
                continue;
            }
            if (!Character.isLetterOrDigit(rightChar)) {
                right--;
                continue;
            }

            if (Character.toLowerCase(leftChar) != Character.toLowerCase(rightChar)) {
                return false;
            }

            left++;
            right--;
        }

        return true;
    }

    public static void main(String[] args) {
        System.out.println("========== LeetCode 125. 验证回文串 ==========\n");

        String[] tests = {
                "A man, a plan, a canal: Panama",
                "race a car",
                " ",
                "0P",
                "No lemon, no melon"
        };

        for (String test : tests) {
            boolean r1 = isPalindromeByBuilder(test);
            boolean r2 = isPalindromeTwoPointers(test);
            System.out.printf("\"%s\" -> Builder:%s TwoPointers:%s%n", test, r1, r2);
        }

        System.out.println("\n--- 解法对比 ---");
        System.out.println("清洗 + 反转: 写法直观，但需要 O(n) 额外空间");
        System.out.println("双指针: 不创建新字符串，O(1) 额外空间，更适合面试");
    }
}
