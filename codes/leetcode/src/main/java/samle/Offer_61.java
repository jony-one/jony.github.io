package samle;

import java.util.Iterator;
import java.util.TreeSet;

/**
 * 从扑克牌中随机抽5张牌，判断是不是一个顺子，即这5张牌是不是连续的。2～10为数字本身，A为1，J为11，Q为12，K为13，而大、小王为 0 ，可以看成任意数字。A 不能视为 14。
 *
 *  
 *
 * 示例 1:
 *
 * 输入: [1,2,3,4,5]
 * 输出: True
 *  
 *
 * 示例 2:
 *
 * 输入: [0,0,1,2,5]
 * 输出: True
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/bu-ke-pai-zhong-de-shun-zi-lcof
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class Offer_61 {

    public static void main(String[] args) {
        System.out.println(new Offer_61().isStraight(new int[]{0,0,0,8,13}));
    }

    public boolean isStraight(int[] nums) {
        int zero = 0;
        TreeSet<Integer> set = new TreeSet<>();
        for (int num : nums) {
            if (num == 0){
                zero+=1;
                continue;
            }
            if (!set.add(num)){
                return false;
            }
        }
        if (zero > 4){
            return true;
        }
        Iterator<Integer> iterator = set.iterator();
        int pre = iterator.next();
        int last;
        while (iterator.hasNext()){
            last = iterator.next();
            if (last - (pre+1) <= zero){
                zero -= last - (pre+1);
                pre = last;
            }else {
                return false;
            }
        }

        return true;
    }
}
