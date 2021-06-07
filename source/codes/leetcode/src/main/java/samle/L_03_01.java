package samle;


/**
 * 三合一。描述如何只用一个数组来实现三个栈。
 * <p>
 * 你应该实现push(stackNum, value)、pop(stackNum)、isEmpty(stackNum)、peek(stackNum)方法。stackNum表示栈下标，value表示压入的值。
 * <p>
 * 构造函数会传入一个stackSize参数，代表每个栈的大小。
 * <p>
 * 示例1:
 * <p>
 * 输入：
 * ["TripleInOne", "push", "push", "pop", "pop", "pop", "isEmpty"]
 * [[1], [0, 1], [0, 2], [0], [0], [0], [0]]
 * 输出：
 * [null, null, null, 1, -1, -1, true]
 * 说明：当栈为空时`pop, peek`返回-1，当栈满时`push`不压入元素。
 * 示例2:
 * <p>
 * 输入：
 * ["TripleInOne", "push", "push", "push", "pop", "pop", "pop", "peek"]
 * [[2], [0, 1], [0, 2], [0, 3], [0], [0], [0], [0]]
 * 输出：
 * [null, null, null, null, 2, 1, -1, -1]
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/three-in-one-lcci
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_03_01 {

    public static void main(String[] args) {
//        L_03_01 n = new L_03_01(2);
//        n.push(0, 1);
//        n.push(0, 2);
//        n.push(0, 3);

        L_03_01 n = new L_03_01(18);
        n.push(0, 1);
        n.push(0, 2);
        n.pop(0);
        n.pop(0);
        n.pop(0);
    }


    public L_03_01(int i) {
        integers = new Integer[i];
    }

    Integer[] integers;
    int size = 0;

    public void push(int stackNum, int value) {
        if (size >= integers.length) {
            return;
        }
        integers[stackNum] = value;
        size += 1;
    }

    public int pop(int stackNum) {
        size-=1;
        if (size < 0){
            size = 0;
            return -1;
        }
        if (integers[size] == null) {
            return -1;
        }
        int a = integers[size];
        integers[size] = null;
        return a;
    }

    public int peek(int stackNum) {
        if (size < 0){
            size = 0;
            return -1;
        }
        if (integers[stackNum] == null) {
            return -1;
        }

        int a = integers[stackNum];
        return a;
    }

    public boolean isEmpty(int stackNum) {
        return size <= 0;
    }
}
