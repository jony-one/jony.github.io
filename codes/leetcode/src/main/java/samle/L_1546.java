package samle;

import java.util.HashSet;
import java.util.Set;

/**
 * 给你一个数组 nums 和一个整数 target 。
 * <p>
 * 请你返回 非空不重叠 子数组的最大数目，且每个子数组中数字和都为 target 。
 * <p>
 *  
 * <p>
 * 示例 1：
 * <p>
 * 输入：nums = [1,1,1,1,1], target = 2
 * 输出：2
 * 解释：总共有 2 个不重叠子数组（加粗数字表示） [1,1,1,1,1] ，它们的和为目标值 2 。
 * 示例 2：
 * <p>
 * 输入：nums = [-1,3,5,1,4,2,-9], target = 6
 * 输出：2
 * 解释：总共有 3 个子数组和为 6 。
 * ([5,1], [4,2], [3,5,1,4,2,-9]) 但只有前 2 个是不重叠的。
 * 示例 3：
 * <p>
 * 输入：nums = [-2,6,6,3,5,4,1,2,8], target = 10
 * 输出：3
 * 示例 4：
 * <p>
 * 输入：nums = [0,0,0], target = 0
 * 输出：3
 *  
 * <p>
 * 提示：
 * <p>
 * 1 <= nums.length <= 10^5
 * -10^4 <= nums[i] <= 10^4
 * 0 <= target <= 10^6
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/maximum-number-of-non-overlapping-subarrays-with-sum-equals-target
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_1546 {

    public static void main(String[] args) {
        System.out.println(new L_1546().maxNonOverlapping(new int[]{1, 1, 1, 1, 1}, 2) ); //2
        System.out.println(new L_1546().maxNonOverlapping(new int[]{-1, 3, 5, 1, 4, 2, -9}, 6) ); // 2
        System.out.println(new L_1546().maxNonOverlapping(new int[]{-2, 6, 6, 3, 5, 4, 1, 2, 8}, 10)); // 3
     }

    //      代码染色
    //
    public int maxNonOverlapping(int[] nums, int target) {

        int len = nums.length;
        //
        int count = 0;
        for (int i = 0; i < len; ) {

            int sum = 0;
            Set<Integer> set = new HashSet<>();
            set.add(0);
            for (; i < len; i++) {
                sum += nums[i];
                if (set.contains(sum - target)){
                    count++;
                    break;
                }else {
                    set.add(sum);
                }
            }
            i++;
        }

        return count;
    }

}
