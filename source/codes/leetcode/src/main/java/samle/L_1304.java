package samle;

/**
 * 给你一个整数 n，请你返回 任意 一个由 n 个 各不相同 的整数组成的数组，并且这 n 个数相加和为 0 。
 *
 *  
 *
 * 示例 1：
 *
 * 输入：n = 5
 * 输出：[-7,-1,1,3,4]
 * 解释：这些数组也是正确的 [-5,-1,1,2,3]，[-3,-1,2,-2,4]。
 * 示例 2：
 *
 * 输入：n = 3
 * 输出：[-1,0,1]
 * 示例 3：
 *
 * 输入：n = 1
 * 输出：[0]
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/find-n-unique-integers-sum-up-to-zero
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_1304 {
    public static void main(String[] args) {
        int[] i = new L_1304().sumZero(1);
        for (int i1 = 0; i1 < i.length; i1++) {
            System.out.println(i[i1]);
        }
    }

    public int[] sumZero(int n) {
        int[] arr = new int[n];
        for (int i = 0; i < n/2; i++) {
            arr[i] = i+1;
            arr[n/2+i] = -(i+1);
        }
        return arr;
    }
}
