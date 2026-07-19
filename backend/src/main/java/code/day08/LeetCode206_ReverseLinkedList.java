package code.day08;

/**
 * LeetCode 206. 反转链表。
 *
 * <p>给你单链表的头节点 {@code head}，请你反转链表并返回反转后的链表。</p>
 *
 * <p>示例：
 * <pre>{@code
 *   输入: 1 → 2 → 3 → 4 → 5 → null
 *   输出: 5 → 4 → 3 → 2 → 1 → null
 * }</pre>
 *
 * <p>两种解法：
 * <ul>
 *   <li><b>迭代法</b>：三指针（prev/curr/next），边走边反转</li>
 *   <li><b>递归法</b>：递归到末尾，回溯时反转</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class LeetCode206_ReverseLinkedList {

    /**
     * 链表节点定义。
     */
    static class ListNode {
        int val;
        ListNode next;

        ListNode(int val) { this.val = val; }

        ListNode(int val, ListNode next) { this.val = val; this.next = next; }
    }

    /**
     * 迭代法反转链表 O(n)。
     *
     * <p>三指针思路：
     * <pre>{@code
     *   prev  curr → next
     *   null   1  →  2  →  3  →  null
     *
     *   第一步：curr.next = prev (反转箭头)
     *           prev = curr       (prev 前移)
     *           curr = next       (curr 前移)
     * }</pre>
     *
     * @param head 原链表头节点
     * @return 反转后的新头节点
     */
    public static ListNode reverseList(ListNode head) {
        ListNode prev = null;
        ListNode curr = head;

        while (curr != null) {
            ListNode next = curr.next; // 暂存下一个节点
            curr.next = prev;           // 反转箭头
            prev = curr;                // prev 前移
            curr = next;                // curr 前移
        }

        return prev; // 循环结束时 prev 就是新头节点
    }

    /**
     * 递归法反转链表 O(n)。
     *
     * <p>递归到最后一个节点，回溯时逐层反转指向。</p>
     *
     * @param head 当前节点
     * @return 反转后的新头节点（始终是原链表的尾节点）
     */
    public static ListNode reverseListRecursive(ListNode head) {
        // 基准情况：空链表或只有一个节点
        if (head == null || head.next == null) {
            return head;
        }

        // 递归反转后面的部分，newHead 始终是原尾节点
        ListNode newHead = reverseListRecursive(head.next);

        // 回溯时反转：让下一个节点指向自己
        head.next.next = head;
        head.next = null;

        return newHead;
    }

    // ---- 辅助方法 ----

    /** 从数组创建链表 */
    static ListNode createList(int... vals) {
        ListNode dummy = new ListNode(0);
        ListNode curr = dummy;
        for (int v : vals) {
            curr.next = new ListNode(v);
            curr = curr.next;
        }
        return dummy.next;
    }

    /** 打印链表 */
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" → ");
            head = head.next;
        }
        System.out.println(" → null");
    }

    /**
     * 主方法。
     */
    public static void main(String[] args) {
        System.out.println("========== LeetCode 206. 反转链表 ==========\n");

        int[][] tests = {{1,2,3,4,5}, {1,2}, {1}, {}};

        for (int[] arr : tests) {
            ListNode head1 = createList(arr);
            ListNode head2 = createList(arr);

            System.out.print("输入:  ");
            printList(head1);

            ListNode r1 = reverseList(head1);
            System.out.print("迭代:  ");
            printList(r1);

            ListNode r2 = reverseListRecursive(head2);
            System.out.print("递归:  ");
            printList(r2);
            System.out.println();
        }

        System.out.println("--- 双指针（迭代）是链表题的万能钥匙 ---");
        System.out.println("三句核心代码：");
        System.out.println("  next = curr.next;   // 保存下一个");
        System.out.println("  curr.next = prev;   // 反转箭头");
        System.out.println("  prev = curr; curr = next;  // 双指针前移");
    }
}
