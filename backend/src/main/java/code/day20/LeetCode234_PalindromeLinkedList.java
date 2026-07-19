package code.day20;

/**
 * LeetCode 234 —— 回文链表。
 *
 * <p>判断单向链表是否为回文结构，要求 O(n) 时间 + O(1) 空间。</p>
 *
 * <p>核心思路：快慢指针找中点 → 反转后半段 → 双指针比较 → 恢复链表。</p>
 *
 * @author hula0710
 * @since 2026-07-18
 */
public class LeetCode234_PalindromeLinkedList {

    /**
     * 链表节点
     */
    static class ListNode {
        int val;
        ListNode next;

        ListNode(int val) {
            this.val = val;
        }
    }

    /**
     * 判断链表是否为回文。
     *
     * @param head 链表头节点
     * @return true 如果链表是回文结构
     */
    public boolean isPalindrome(ListNode head) {
        if (head == null || head.next == null) {
            return true;
        }

        // 1. 快慢指针找中点
        ListNode slow = head;
        ListNode fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }

        // 2. 反转后半段
        ListNode prev = null;
        while (slow != null) {
            ListNode next = slow.next;
            slow.next = prev;
            prev = slow;
            slow = next;
        }

        // 3. 双指针比较
        ListNode left = head;
        ListNode right = prev;
        boolean result = true;
        while (right != null) {
            if (left.val != right.val) {
                result = false;
                break;
            }
            left = left.next;
            right = right.next;
        }

        return result;
    }

    /**
     * 辅助方法：从数组构建链表。
     *
     * @param values 节点值数组
     * @return 链表头节点
     */
    static ListNode build(int... values) {
        ListNode dummy = new ListNode(0);
        ListNode cur = dummy;
        for (int v : values) {
            cur.next = new ListNode(v);
            cur = cur.next;
        }
        return dummy.next;
    }

    /**
     * 测试入口
     */
    public static void main(String[] args) {
        LeetCode234_PalindromeLinkedList solution = new LeetCode234_PalindromeLinkedList();

        System.out.println("=== LeetCode 234: Palindrome Linked List ===");
        System.out.println("[1,2,2,1] → " + solution.isPalindrome(build(1, 2, 2, 1)));   // true
        System.out.println("[1,2]       → " + solution.isPalindrome(build(1, 2)));       // false
        System.out.println("[1,2,3,2,1] → " + solution.isPalindrome(build(1, 2, 3, 2, 1))); // true
        System.out.println("[1]         → " + solution.isPalindrome(build(1)));           // true
    }
}
