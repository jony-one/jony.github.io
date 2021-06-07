package samle;

import java.util.Arrays;

/**
 * 三枚石子放置在数轴上，位置分别为 a，b，c。
 * <p>
 * 每一回合，我们假设这三枚石子当前分别位于位置 x, y, z 且 x < y < z。从位置 x 或者是位置 z 拿起一枚石子，并将该石子移动到某一整数位置 k 处，其中 x < k < z 且 k != y。
 * <p>
 * 当你无法进行任何移动时，即，这些石子的位置连续时，游戏结束。
 * <p>
 * 要使游戏结束，你可以执行的最小和最大移动次数分别是多少？ 以长度为 2 的数组形式返回答案：answer = [minimum_moves, maximum_moves]
 * <p>
 *  
 * <p>
 * 示例 1：
 * <p>
 * 输入：a = 1, b = 2, c = 5
 * 输出：[1, 2]
 * 解释：将石子从 5 移动到 4 再移动到 3，或者我们可以直接将石子移动到 3。
 * 示例 2：
 * <p>
 * 输入：a = 4, b = 3, c = 2
 * 输出：[0, 0]
 * 解释：我们无法进行任何移动。
 *  
 * <p>
 * 提示：
 * <p>
 * 1 <= a <= 100
 * 1 <= b <= 100
 * 1 <= c <= 100
 * a != b, b != c, c != a
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/moving-stones-until-consecutive
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_1033 {

    public static void main(String[] args) {
        System.out.println(Arrays.toString(new L_1033().numMovesStones(1, 2, 5)));
        System.out.println(Arrays.toString(new L_1033().numMovesStones(4, 3, 2)));
        System.out.println(Arrays.toString(new L_1033().numMovesStones(5, 10, 2)));
    }

    public int[] numMovesStones(int a, int b, int c) {
        int t;
        if (a > b) {
            t = a;
            a = b;
            b = t;
        }
        if (a > c) {
            t = a;
            a = c;
            c = t;
        }
        if (b > c) {
            t = b;
            b = c;
            c = t;
        }   //将最小值给a，最大值给c
        if (a == b - 1 && a == c - 2)  //a，b，c相邻无法移动
        {
            return new int[]{0, 0};
        }
        if (b == a + 1 || c == b + 1 || b == a + 2 || b == c - 2)  //有两个数相邻或相近（隔一位）注意我们上面已经排除了3个数相邻情况
        {
            return new int[]{1, c - a - 2};
        }
        return new int[]{2, c - a - 2};  //其他情况
    }
}
