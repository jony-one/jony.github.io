package samle;

/**
 * 三步问题。有个小孩正在上楼梯，楼梯有n阶台阶，小孩一次可以上1阶、2阶或3阶。实现一种方法，计算小孩有多少种上楼梯的方式。结果可能很大，你需要对结果模1000000007。
 * <p>
 * 示例1:
 * <p>
 * 输入：n = 3
 * 输出：4
 * 说明: 有四种走法
 * 示例2:
 * <p>
 * 输入：n = 5
 * 输出：13
 * 提示:
 * <p>
 * n范围在[1, 1000000]之间
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/three-steps-problem-lcci
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_08_01 {

    public static void main(String[] args) {
        System.out.println(new L_08_01().waysToStep(5));
    }

    // 1 = 1
    // 2 = 2
    // 3 = 4
    // 4 = 7
    // 5 = 13
    // 6 = 24
    public int waysToStep(int n) {
        if (n == 1) {
            return 1;
        }
        if (n == 2) {
            return 2;
        }
        if (n == 3) {
            return 4;
        }
        long a = 1;
        long b = 2;
        long c = 4;
        long sum = 0;
        for (int i = 4; i <= n; i++) {
            sum = (a + b +c);
            a = b;
            b = c;
            c = sum%1000000007l;
        }


        return (int)(sum);
    }
}
