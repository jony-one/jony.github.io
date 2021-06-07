package samle;


import java.util.TreeSet;

/**
 * 我们有一个非负整数数组 A。
 *
 * 对于每个（连续的）子数组 B = [A[i], A[i+1], ..., A[j]] （ i <= j），我们对 B 中的每个元素进行按位或操作，获得结果 A[i] | A[i+1] | ... | A[j]。
 *
 * 返回可能结果的数量。 （多次出现的结果在最终答案中仅计算一次。）
 *
 *  
 *
 * 示例 1：
 *
 * 输入：[0]
 * 输出：1
 * 解释：
 * 只有一个可能的结果 0 。
 * 示例 2：
 *
 * 输入：[1,1,2]
 * 输出：3
 * 解释：
 * 可能的子数组为 [1]，[1]，[2]，[1, 1]，[1, 2]，[1, 1, 2]。
 * 产生的结果为 1，1，2，1，3，3 。
 * 有三个唯一值，所以答案是 3 。
 * 示例 3：
 *
 * 输入：[1,2,4]
 * 输出：6
 * 解释：
 * 可能的结果是 1，2，3，4，6，以及 7 。
 *  
 *
 * 提示：
 *
 * 1 <= A.length <= 50000
 * 0 <= A[i] <= 10^9
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/bitwise-ors-of-subarrays
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_898 {

    public int subarrayBitwiseORs(int[] A) {
        if (A.length <= 1){
            return A.length;
        }


        TreeSet<Integer> set = new TreeSet();
        for (int i : A) {
            set.add(i);
        }

        for (int i = 1; i < A.length; i++) {
            for (int j = 0; j < i-1; j++) {
               int b = i+j;
                for (int k = j; k < b-1; k++) {

                }
            }
        }

        for (Integer integer : set) {
            System.out.println(integer);
        }
        return set.size();
    }

    public static void main(String[] args) {
        int re = new L_898().subarrayBitwiseORs(new int[]{1,3,88,9,555,777});
        System.out.println(re);
    }
}
