package code.day12;

package code.day12;

/** LeetCode 14：最长公共前缀。 */
public class LeetCode14_LongestCommonPrefix {

    public static void main(String[] args) {
        test(new String[] {"flower", "flow", "flight"}, "fl");
        test(new String[] {"dog", "racecar", "car"}, "");
        test(new String[] {"interview", "internet", "internal"}, "inter");
        test(new String[] {}, "");
        test(new String[] {"solo"}, "solo");
    }

    /**
     * 横向扫描：将第一个字符串作为前缀，不断缩短到能匹配所有字符串。
     *
     * <p>时间复杂度 O(S)，S 是所有字符数；额外空间 O(1)。</p>
     */
    public static String longestCommonPrefix(String[] strings) {
        if (strings == null || strings.length == 0) {
            return "";
        }

        String prefix = strings[0];
        for (int i = 1; i < strings.length; i++) {
            while (!strings[i].startsWith(prefix)) {
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) {
                    return "";
                }
            }
        }
        return prefix;
    }

    /**
     * 纵向扫描：逐列比较每个字符串的字符。
     */
    public static String longestCommonPrefixVertical(String[] strings) {
        if (strings == null || strings.length == 0) {
            return "";
        }

        for (int column = 0; column < strings[0].length(); column++) {
            char current = strings[0].charAt(column);
            for (int row = 1; row < strings.length; row++) {
                if (column == strings[row].length() || strings[row].charAt(column) != current) {
                    return strings[0].substring(0, column);
                }
            }
        }
        return strings[0];
    }

    private static void test(String[] input, String expected) {
        String horizontal = longestCommonPrefix(input);
        String vertical = longestCommonPrefixVertical(input);
        boolean passed = expected.equals(horizontal) && expected.equals(vertical);
        System.out.printf("期望: \"%s\" | 横向: \"%s\" | 纵向: \"%s\" | %s%n",
                expected, horizontal, vertical, passed ? "通过" : "失败");
    }
}
