package samle;

import java.util.HashMap;
import java.util.Stack;
import java.util.TreeSet;

/**
 * 请设计一个栈，除了常规栈支持的pop与push函数以外，还支持min函数，该函数返回栈元素中的最小值。执行push、pop和min操作的时间复杂度必须为O(1)。
 *
 *
 * 示例：
 *
 * MinStack minStack = new MinStack();
 * minStack.push(-2);
 * minStack.push(0);
 * minStack.push(-3);
 * minStack.getMin();   --> 返回 -3.
 * minStack.pop();
 * minStack.top();      --> 返回 0.
 * minStack.getMin();   --> 返回 -2.
 *
 *
 */
public class L_03_02 {

    public static void main(String[] args) {



    }

    /** initialize your data structure here. */

    Stack<Integer> stack = new Stack<>();
    HashMap<Integer,Integer> map = new HashMap();
    TreeSet<Integer> set = new TreeSet<>();

    public void push(int x) {
        stack.push(x);
        if (map.get(x) != null){
            map.put(x,map.get(x) + 1);
        }else{
            map.put(x,1);
        }
        set.add(x);
    }

    public void pop() {
        Integer i = stack.pop();
        int r = map.get(i);
        if (r == 1){
            map.remove(i);
            set.remove(i);
        }else{
            map.put(i,r-1);
        }
    }

    public int top() {
        int i = stack.pop();
        stack.push(i);
        return i;
    }

    public int getMin() {

        return set.first();
    }
}
