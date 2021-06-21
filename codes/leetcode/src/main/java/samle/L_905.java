package samle;

import java.util.ArrayList;
import java.util.List;

/**
 * 给定一个非负整数数组 A，返回一个数组，在该数组中， A 的所有偶数元素之后跟着所有奇数元素。
 *
 * 你可以返回满足此条件的任何数组作为答案。
 *
 *  
 *
 * 示例：
 *
 * 输入：[3,1,2,4]
 * 输出：[2,4,3,1]
 * 输出 [4,2,3,1]，[2,4,1,3] 和 [4,2,1,3] 也会被接受。
 *  
 *
 * 提示：
 *
 * 1 <= A.length <= 5000
 * 0 <= A[i] <= 5000
 * 通过次数40,510提交次数58,561
 *[3363,4833,290,3381,4227,1711,1253,2984,2212,874,2358,2049,2846,2543,1557,1786,4189,1254,2803,62,3708,1679,228,1404,1200,4766,1761,1439,1796,4735,3169,3106,3578,1940,2072,3254,7,961,1672,1197,3187,1893,4377,2841,2072,2011,3509,2091,3311,233]
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/sort-array-by-parity
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_905 {
    public static void main(String[] args) {
        System.out.println(new L_905().sortArrayByParity(null));
    }

    public int[] sortArrayByParity(int[] A) {
        List<Integer> set1 = new ArrayList<>();
        List<Integer> set2 = new ArrayList<>();
        for (int i : A) {
            if (i %2 == 0){
                set1.add(i);
            }else {
                set2.add(i);
            }
        }
        set1.addAll(set2);
        int i = 0;
        for (Integer integer : set1) {
            A[i] = integer;
            i+=1;
        }
        return A;
    }
}
