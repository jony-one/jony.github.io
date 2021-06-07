package samle;

/**
 * 国际象棋中的骑士可以按下图所示进行移动：
 *
 *  .           
 *
 *
 * 这一次，我们将 “骑士” 放在电话拨号盘的任意数字键（如上图所示）上，接下来，骑士将会跳 N-1 步。每一步必须是从一个数字键跳到另一个数字键。
 *
 * 每当它落在一个键上（包括骑士的初始位置），都会拨出键所对应的数字，总共按下 N 位数字。
 *
 * 你能用这种方式拨出多少个不同的号码？
 *
 * 因为答案可能很大，所以输出答案模 10^9 + 7。
 *
 *  
 *
 * 示例 1：
 *
 * 输入：1
 * 输出：10
 * 示例 2：
 *
 * 输入：2
 * 输出：20
 * 示例 3：
 *
 * 输入：3
 * 输出：46
 *  
 *
 * 提示：
 *
 * 1 <= N <= 5000
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/knight-dialer
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 *
 * z
 * 题解
 *                                                                   .
 *                                                                   .
 *                   .   .                  .    .                   .
 *                  . .4` ..              .. ````                    .
 *                  .  .. .                 ..9` .                   .
 *                    ...                    ....                    .
 *                                                                   .
 *                                                                   .
 *                                                                   .
 *                                                                   .
 *                                                                   .
 *                                                       .    .      .
 *    . .3` .                                              2`        .
 *                                                       .    .      .
 *       .                       ...                       .         .
 *                               .`. .                               .
 *                             . `0` .                               .
 *                              .   .                                .
 *                                                                   .
 *                                                                   .
 *      ...                                               ...        .
 *    . .`. .                                             .``..      .
 *    . `8` .                                              7`        .
 *     .   .                                             ..  .       .
 *                                                                   .
 *                                                                   .
 *                                                                   .
 *                                            ..                     .
 *                                          .    .                   .
 *                  . .1. ..              .. `6'. .                  .
 *                                          .    .                   .
 *                                                                   .
 *在N>=2时，除数字5以外的9个数字都是可到达的。
 *
 * 每跳一步，数字的变化上图所示。
 *图片表示，当骑士处于“1”处时，下一跳将在“6”或“8”；骑士处于“4”处时，下一跳将在“3”或“0”或"9";骑士处于“0”处时，下一跳将在“4”或“6”…………
 *
 * 我们可以发现，1、3、7、9处于对称位置；2，8处于对称位置;4，6处于对称位置。因此，我们可以将数字分为4个状态，命名为A、B、C、D。其中A:{1,3,7,9}, B:{2,8}, C:{4,6}, D:{0}。
 *
 * 我们用f(X,n)表示：在状态X下，跳跃n步能够得到不同数字的个数。则状态转移方程为：
 * f(A,n)=f(B,n-1)+f(C,n-1)
 * f(B,n)=2*f(A,n-1)
 * f(C,n)=2*f(A,n-1)+f(D,n-1)
 * f(D,n)=2*f(C,n-1)
 * 解释为：
 * 处于状态A中的数字(1,3,7,9)通过一次跳跃要么变成状态B(2,8)，要么变成状态C(4,6)
 * 处于状态B中的数字(2,8)通过一次跳跃有两种方式变成状态A(1,3,7,9)
 * 处于状态C中的数字(4,6)通过一次跳跃有两种方式变成状态A(1,3,7,9)，还有一种方式变成状态D(0)
 * 处于状态D中的数字(0)通过一次跳跃有两种方式变成状态C(4,6)
 *
 * 作者：caticd
 *
 *
 */
public class L_935 {
    public static void main(String[] args) {
        System.out.println(new L_935().knightDialer(3131));
    }
    public int knightDialer(int n) {
        if (n==1){return 10;}
        int mod = 1_000_000_007;
        long[] dialer = new long[]{1,1,1,1};
        for (int i = 0; i < n-1; i++) {
            dialer = new long[]{(dialer[1]+dialer[2])%mod,(2*dialer[0])%mod,(2*dialer[0]+dialer[3])%mod,(2*dialer[2])%mod};
        }
        return (int) (((4*dialer[0]+2*dialer[1]+2*dialer[2]+dialer[3]))%mod);
    }
}
