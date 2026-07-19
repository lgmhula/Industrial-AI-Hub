package code.day15;

/**
 * LeetCode 21. 合并两个有序链表。
 *
 * <p>将两个升序链表合并为一个新的升序链表。
 * 迭代双指针法：dummy 节点 + 两路归并。O(m+n)。</p>
 *
 * <pre>{@code
 *   l1: 1→2→4     合并: 1→1→2→3→4→4
 *   l2: 1→3→4
 * }</pre>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-15
 */
public class LeetCode21_MergeTwoSortedLists {

    static class ListNode {
        int val;
        ListNode next;
        ListNode(int v) { val = v; }
        ListNode(int v, ListNode n) { val = v; next = n; }
    }

    /** 迭代双指针 */
    public static ListNode mergeTwoLists(ListNode l1, ListNode l2) {
        ListNode dummy = new ListNode(0);
        ListNode curr = dummy;
        while (l1 != null && l2 != null) {
            if (l1.val <= l2.val) { curr.next = l1; l1 = l1.next; }
            else                  { curr.next = l2; l2 = l2.next; }
            curr = curr.next;
        }
        curr.next = (l1 != null) ? l1 : l2;
        return dummy.next;
    }

    static ListNode of(int... vals) {
        ListNode d = new ListNode(0), c = d;
        for (int v : vals) { c.next = new ListNode(v); c = c.next; }
        return d.next;
    }

    static void print(ListNode h) {
        while (h != null) { System.out.print(h.val + (h.next != null ? "→" : "→null")); h = h.next; }
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("========== LeetCode 21. 合并两个有序链表 ==========\n");
        var l1 = of(1, 2, 4);
        var l2 = of(1, 3, 4);
        System.out.print("l1: "); print(l1);
        System.out.print("l2: "); print(l2);
        System.out.print("合并: "); print(mergeTwoLists(l1, l2));
        System.out.println("\n边界: null+[0]");
        System.out.print("合并: "); print(mergeTwoLists(null, new ListNode(0)));
    }
}
