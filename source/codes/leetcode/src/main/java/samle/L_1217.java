package samle;

/**
 * 数轴上放置了一些筹码，每个筹码的位置存在数组 chips 当中。
 * <p>
 * 你可以对 任何筹码 执行下面两种操作之一（不限操作次数，0 次也可以）：
 * <p>
 * 将第 i 个筹码向左或者右移动 2 个单位，代价为 0。
 * 将第 i 个筹码向左或者右移动 1 个单位，代价为 1。
 * 最开始的时候，同一位置上也可能放着两个或者更多的筹码。
 * <p>
 * 返回将所有筹码移动到同一位置（任意位置）上所需要的最小代价。
 * <p>
 *  
 * <p>
 * 示例 1：
 * <p>
 * 输入：chips = [1,2,3]
 * 输出：1
 * 解释：第二个筹码移动到位置三的代价是 1，第一个筹码移动到位置三的代价是 0，总代价为 1。
 * 示例 2：
 * <p>
 * 输入：chips = [2,2,2,3,3]
 * 输出：2
 * 解释：第四和第五个筹码移动到位置二的代价都是 1，所以最小总代价为 2。
 *  
 * <p>
 * 提示：
 * <p>
 * 1 <= chips.length <= 100
 * 1 <= chips[i] <= 10^9
 * 先理解题意：有的人可能理解错题意了，
 * 这里的chips数组里存放的是第i个筹码存放的位置，不是第i个位置存放了多少个筹码，
 * 这个概念搞清楚了就简单多了。比如chips = [2,2,2,3,3]]表示第1个筹码放第2个位置，
 * 第2个筹码放第2个位置，第3个筹码放第2个位置，第4个筹码放第3个位置，第5个筹码放第3个位置，
 * 那么这就表示，第2个位置上有3个筹码，第3个位置上有2个筹码，其它位置上没有筹码，
 * 可以把第3个位置上的2个筹码移动到第2个位置上，所以代价是2.
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/minimum-cost-to-move-chips-to-the-same-position
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_1217 {

    public static void main(String[] args) {
        System.out.println(new L_1217().minCostToMoveChips(null));
    }

    public int minCostToMoveChips(int[] position) {
        int odd = 0, even = 0;
        for (int i = 0; i < position.length; i++) {
            if (position[i] % 2 == 0) {
                even++;
            } else if (position[i] % 2 != 0) {
                odd++;
            }
        }
        return Math.min(even, odd);
    }
}
