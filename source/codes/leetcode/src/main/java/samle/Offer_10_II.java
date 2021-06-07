package samle;

/**
 * 一只青蛙一次可以跳上1级台阶，也可以跳上2级台阶。求该青蛙跳上一个 n 级的台阶总共有多少种跳法。
 *
 * 答案需要取模 1e9+7（1000000007），如计算初始结果为：1000000008，请返回 1。
 *
 * 示例 1：
 *
 * 输入：n = 2
 * 输出：2
 * 示例 2：
 *
 * 输入：n = 7
 * 输出：21
 * 示例 3：
 *
 * 输入：n = 0
 * 输出：1
 * 提示：
 *
 * 0 <= n <= 100

 */
public class Offer_10_II {

    public static void main(String[] args) {
        System.out.println(new Offer_10_II().numWays(93));
    }

    public int numWays(int n) {
        if (n == 0 || n == 1){
            return 1;
        }
        if (n == 2) {
            return 2;
        }

        long pre1 = 1;
        long pre2 = 2;
        long curr = 0;
        for (int i = 2; i < n; i++) {
            curr = (pre1 + pre2) % 1000000007l;
            pre1 = pre2;
            pre2 = curr;
        }
        return (int) (curr);
    }
}
