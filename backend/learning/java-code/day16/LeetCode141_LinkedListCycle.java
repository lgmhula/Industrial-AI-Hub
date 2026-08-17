package code.day16;

/**
 * LeetCode 141. 环形链表。
 *
 * <p>判断链表中是否有环。快慢指针（Floyd 判圈算法）：
 * slow 每次走一步，fast 每次走两步。如果有环，fast 最终会追上 slow。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-16
 */
public class LeetCode141_LinkedListCycle {

    static class ListNode {
        int val;
        ListNode next;
        ListNode(int v) { val = v; }
    }

    /** 快慢指针 O(n) */
    public static boolean hasCycle(ListNode head) {
        if (head == null) return false;
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) return true;
        }
        return false;
    }

    public static void main(String[] args) {
        System.out.println("========== LeetCode 141. 环形链表 ==========\n");

        // 有环链表: 1→2→3→4→2(环)
        var n1 = new ListNode(1); var n2 = new ListNode(2);
        var n3 = new ListNode(3); var n4 = new ListNode(4);
        n1.next = n2; n2.next = n3; n3.next = n4; n4.next = n2;
        System.out.println("有环链表: " + hasCycle(n1) + " ✅");

        // 无环: 1→2→3→null
        var a1 = new ListNode(1);
        var a2 = new ListNode(2);
        var a3 = new ListNode(3);
        a1.next = a2; a2.next = a3;
        System.out.println("无环链表: " + hasCycle(a1) + " ✅");

        System.out.println("单节点:  " + hasCycle(new ListNode(1)));

        System.out.println("\n快慢指针 = 链表题的屠龙刀。");
        System.out.println("slow 一步, fast 两步, 有环必相遇。O(n)+O(1)。");
    }
}
