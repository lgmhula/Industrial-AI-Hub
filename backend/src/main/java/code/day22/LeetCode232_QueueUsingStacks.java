package code.day22;

import java.util.Stack;

/**
 * LeetCode 232 —— 用栈实现队列。
 *
 * <p>两个栈：pushStack（入队）+ popStack（出队）。
 * 出队时若 popStack 为空，将 pushStack 全部倒入 popStack（逆序变正序）。</p>
 *
 * @author hula0710
 * @since 2026-07-20
 */
public class LeetCode232_QueueUsingStacks {

    static class MyQueue {
        private final Stack<Integer> pushStack = new Stack<>();
        private final Stack<Integer> popStack = new Stack<>();

        /** 入队 O(1) */
        public void push(int x) {
            pushStack.push(x);
        }

        /** 出队 均摊 O(1) */
        public int pop() {
            transferIfNeeded();
            return popStack.pop();
        }

        /** 查看队首 均摊 O(1) */
        public int peek() {
            transferIfNeeded();
            return popStack.peek();
        }

        /** 判空 O(1) */
        public boolean empty() {
            return pushStack.isEmpty() && popStack.isEmpty();
        }

        private void transferIfNeeded() {
            if (popStack.isEmpty()) {
                while (!pushStack.isEmpty()) {
                    popStack.push(pushStack.pop());
                }
            }
        }
    }

    public static void main(String[] args) {
        MyQueue q = new MyQueue();
        q.push(1);
        q.push(2);
        q.push(3);
        System.out.println("peek → " + q.peek());   // 1
        System.out.println("pop  → " + q.pop());     // 1
        System.out.println("pop  → " + q.pop());     // 2
        System.out.println("empty→ " + q.empty());   // false
        System.out.println("pop  → " + q.pop());     // 3
        System.out.println("empty→ " + q.empty());   // true
    }
}