package samle;

/**
 * 一个长度为n-1的递增排序数组中的所有数字都是唯一的，并且每个数字都在范围0～n-1之内。在范围0～n-1内的n个数字中有且只有一个数字不在该数组中，请找出这个数字。
 * <p>
 *  
 * <p>
 * 示例 1:
 * <p>
 * 输入: [0,1,3]
 * 输出: 2
 * 示例 2:
 * <p>
 * 输入: [0,1,2,3,4,5,6,7,9]
 * 输出: 8
 *  
 * <p>
 * 限制：
 * <p>
 * 1 <= 数组长度 <= 10000
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/que-shi-de-shu-zi-lcof
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class Offer_53_II {

    public static void main(String[] args) {
        System.out.println(new Offer_53_II().missingNumber(new int[]{0, 1, 3}));
        System.out.println(new Offer_53_II().missingNumber(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9}));
    }

    public int missingNumber(int[] nums) {
        int i = 0;
        for (; i < nums.length; i++) {
            if (nums[i] != i){
                return i;
            }
        }
        return i;
    }
}
