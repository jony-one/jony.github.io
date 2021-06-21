package samle;

/**
 * 编写一个方法，找出两个数字a和b中最大的那一个。不得使用if-else或其他比较运算符。
 * <p>
 * 示例：
 * <p>
 * 输入： a = 1, b = 2
 * 输出： 2
 */
public class L_16_07 {
    public static void main(String[] args) {
        System.out.println(new L_16_07().maximum(1,2));
    }

    public int maximum(int a, int b) {
        return a > b ? a : b;
    }
}
