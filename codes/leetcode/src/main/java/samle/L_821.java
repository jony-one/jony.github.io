package samle;

import java.util.ArrayList;
import java.util.List;

/**
 * 给定一个字符串 S 和一个字符 C。返回一个代表字符串 S 中每个字符到字符串 S 中的字符 C 的最短距离的数组。
 * <p>
 * 示例 1:
 * <p>
 * 输入: S = "loveleetcode", C = 'e'
 * 输出: [3, 2, 1, 0, 1, 0, 0, 1, 2, 2, 1, 0]
 * 说明:
 * <p>
 * 字符串 S 的长度范围为 [1, 10000]。
 * C 是一个单字符，且保证是字符串 S 里的字符。
 * S 和 C 中的所有字母均为小写字母。
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/shortest-distance-to-a-character
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_821 {

    public static void main(String[] args) {

    }

    public int[] shortestToChar(String S, char C) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < S.length(); i++) {
            if (S.charAt(i) == C) {
                list.add(i);
            }
        }
        int[] steps = new int[S.length()];
        for (int i = 0; i < S.length(); i++) {
            int min = Integer.MAX_VALUE;
            for (Integer integer : list) {
                if (Math.abs(i - integer) < min){
                    min = Math.abs(i - integer);
                }
            }
            steps[i] = min;
        }
        return steps;
    }
}
