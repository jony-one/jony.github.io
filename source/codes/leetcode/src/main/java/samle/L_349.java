package samle;

import java.util.TreeSet;

/**
 * 给定两个数组，编写一个函数来计算它们的交集。
 *
 *  
 *
 * 示例 1：
 *
 * 输入：nums1 = [1,2,2,1], nums2 = [2,2]
 * 输出：[2]
 * 示例 2：
 *
 * 输入：nums1 = [4,9,5], nums2 = [9,4,9,8,4]
 * 输出：[9,4]
 *  
 *
 * 说明：
 *
 * 输出结果中的每个元素一定是唯一的。
 * 我们可以不考虑输出结果的顺序。
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/intersection-of-two-arrays
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_349 {

    public static void main(String[] args) {
        System.out.println(new L_349().intersection(new int[]{1,2,2,1},new int[]{2,2}));
        System.out.println(new L_349().intersection(new int[]{4,7,9,7,6,7},new int[]{5,0,0,6,1,6,2,2,4}));
    }

    public int[] intersection(int[] nums1, int[] nums2) {
        if (nums1 == null || nums2 == null){
            return null;
        }
        if (nums1.length == 0 || nums2.length == 0){
            return new int[0];
        }
        TreeSet<Integer> integers = new TreeSet<>();
        for (int i : nums1) {
            integers.add(i);
        }
        TreeSet<Integer> list = new TreeSet<>();
        for (int i : nums2) {
            if (!integers.add(i)){
                list.add(i);
            }else {
                integers.remove(i);
            }

        }
        int[] arr = new int[list.size()];
        int i =0;
        for (Integer integer : list) {
            arr[i] = integer;
            i+=1;
        }
        return arr;
    }
}
