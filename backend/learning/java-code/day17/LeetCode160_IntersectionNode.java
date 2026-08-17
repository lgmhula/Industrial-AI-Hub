package code.day17;

/**
 * LeetCode 160. 相交链表。
 *
 * <p>找出两个单链表相交的起始节点。双指针浪漫相遇法 O(m+n) O(1)：
 * pA 走完 A 去走 B，pB 走完 B 去走 A，相遇点即交点（或同时为 null 不相交）。</p>
 *
 * @author Reboot
 * @since 2026-07-17
 */
public class LeetCode160_IntersectionNode {

    static class ListNode {
        int val;
        ListNode next;
        ListNode(int v) { val = v; }
    }

    /** 双指针浪漫相遇 */
    public static ListNode getIntersectionNode(ListNode headA, ListNode headB) {
        ListNode pA = headA, pB = headB;
        while (pA != pB) {
            pA = (pA == null) ? headB : pA.next;
            pB = (pB == null) ? headA : pB.next;
        }
        return pA;
    }

    public static void main(String[] args) {
        System.out.println("========== LeetCode 160. 相交链表 ==========\n");

        // 构建: A=[4,1,8,4,5], B=[5,6,1,8,4,5] 相交于 8
        var c1 = new ListNode(8);
        var c2 = new ListNode(4);
        var c3 = new ListNode(5);
        c1.next = c2; c2.next = c3;

        var a1 = new ListNode(4);
        a1.next = new ListNode(1);
        a1.next.next = c1;

        var b1 = new ListNode(5);
        b1.next = new ListNode(6);
        b1.next.next = new ListNode(1);
        b1.next.next.next = c1;

        ListNode result = getIntersectionNode(a1, b1);
        System.out.println("相交节点: " + (result != null ? result.val : "无交点") + " ✅");

        // 不相交
        var noA = new ListNode(1);
        var noB = new ListNode(2);
        System.out.println("不相交:   " + (getIntersectionNode(noA, noB) != null ? "有交点" : "null ✅"));

        System.out.println("\n双指针：你走过我的路，我走过你的路，终将相遇。");
        System.out.println("时间复杂度 O(m+n)，空间 O(1)。");
    }
}
