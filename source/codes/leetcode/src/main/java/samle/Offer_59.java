package samle;

/**
 * 给定一个数组 nums 和滑动窗口的大小 k，请找出所有滑动窗口里的最大值。
 * <p>
 * 示例:
 * <p>
 * 输入: nums = [1,3,-1,-3,5,3,6,7], 和 k = 3
 * 输出: [3,3,5,5,6,7]
 * 解释:
 * <p>
 * 滑动窗口的位置                最大值
 * ---------------               -----
 * [1  3  -1] -3  5  3  6  7       3
 * 1 [3  -1  -3] 5  3  6  7       3
 * 1  3 [-1  -3  5] 3  6  7       5
 * 1  3  -1 [-3  5  3] 6  7       5
 * 1  3  -1  -3 [5  3  6] 7       6
 * 1  3  -1  -3  5 [3  6  7]      7
 *  
 * <p>
 * 提示：
 * <p>
 * 你可以假设 k 总是有效的，在输入数组不为空的情况下，1 ≤ k ≤ 输入数组的大小。
 * <p>
 * 注意：本题与主站 239 题相同：https://leetcode-cn.com/problems/sliding-window-maximum/
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/hua-dong-chuang-kou-de-zui-da-zhi-lcof
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class Offer_59 {
    public static void main(String[] args) {
        System.out.println(new Offer_59().maxSlidingWindow(new int[]{1,3,-1,-3,5,3,6,7},3).length);
    }

    public int[] maxSlidingWindow(int[] nums, int k) {
        if (nums == null || nums.length ==0){
            return nums;
        }
        k+=1;
        int[] a = new int[nums.length - k];

        for (int i = 0; i < nums.length - k; i++) {

            int max = Integer.MIN_VALUE;

            for (int j = i; j < i + k; j++) {

                if (max < nums[j]) {
                    max = nums[j];
                }
            }
            a[i] = max;
        }
        return a;
    }
}
