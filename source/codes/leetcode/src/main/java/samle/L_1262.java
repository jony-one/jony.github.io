package samle;

import java.util.Arrays;

/**
 * 给你一个整数数组 nums，请你找出并返回能被三整除的元素最大和。
 *
 *  
 *
 * 示例 1：
 *
 * 输入：nums = [3,6,5,1,8]
 * 输出：18
 * 解释：选出数字 3, 6, 1 和 8，它们的和是 18（可被 3 整除的最大和）。
 * 示例 2：
 *
 * 输入：nums = [4]
 * 输出：0
 * 解释：4 不能被 3 整除，所以无法选出数字，返回 0。
 * 示例 3：
 *
 * 输入：nums = [1,2,3,4,4]
 * 输出：12
 * 解释：选出数字 1, 3, 4 以及 4，它们的和是 12（可被 3 整除的最大和）。
 *  
 *
 * 提示：
 *
 * 1 <= nums.length <= 4 * 10^4
 * 1 <= nums[i] <= 10^4
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/greatest-sum-divisible-by-three
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_1262 {

    public static void main(String[] args) {
        System.out.println(new L_1262().maxSumDivThree(new int[]{2,3,36,8,32,38,3,30,13,40}));
//        System.out.println(new L_1262().maxSumDivThree(new int[]{3,6,5,1,8}));
//        System.out.println(new L_1262().maxSumDivThree(new int[]{1,2,3,4,4}));
//        System.out.println(new L_1262().maxSumDivThree(new int[]{0,0,0,1}));
//        System.out.println(new L_1262().maxSumDivThree(new int[]{4}));
    }
    // 应该有更快捷的方法
    public int maxSumDivThree(int[] nums) {
        // 回溯算法
        int sum = 0;
        for (int num : nums) {
            sum+=num;
        }
        Arrays.sort(nums);
        int bc = sum % 3;
        if (bc == 0) return sum;
        sum = sum - bc;
        do{
            // 找出 bc
            if (exsit(nums,bc) == 0){
                return sum;
            }

            sum -= 3;
            bc += 3;
        }while (sum-3>0);
        return 0;
    }

    // 判断是否存在 bc。
    private int exsit(int[] nums,int bc){
        int currentIndex = 0;
        int bak = bc;
        for (int i = currentIndex; i < nums.length; i++) {
            if (bc == nums[i]){
                return 0;
            } else if (bc < nums[i]){
                i=currentIndex;
                currentIndex++;
                bc = bak;
            } else {
                // 补偿措施
                for (int j = 0; j < nums.length && nums[j] <= bc; j++) {
                    if (bc == nums[j]){
                        return 0;
                    }
                }
                bc-=nums[i];
            }
        }
        return -1;
    }


}
