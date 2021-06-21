package samle;

import java.util.HashMap;
import java.util.Map;

/**
 * 和谐数组是指一个数组里元素的最大值和最小值之间的差别正好是1。
 * <p>
 * 现在，给定一个整数数组，你需要在所有可能的子序列中找到最长的和谐子序列的长度。
 * <p>
 * 示例 1:
 * <p>
 * 输入: [1,3,2,2,5,2,3,7]
 * 输出: 5
 * 原因: 最长的和谐数组是：[3,2,2,2,3].
 */
public class L_594 {

    public static void main(String[] args) {
        System.out.println(new L_594().findLHS(new int[]{0,3,0,0,1,1,1,3,1,3,2,3,2,3,-1,0,2,1,0,0,0,1,3,3,-3,3,3,1,3}));
    }

    public int findLHS(int[] nums) {
        if (nums.length <= 1) {
            return 0;
        }

        int maxValue = 0;
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int key = nums[i];
            if (map.get(nums[i]) == null) {
                map.put(nums[i], 1);
            } else {
                map.put(nums[i], map.get(nums[i]) + 1);
            }

            if (map.get(key - 1) == null && map.get(key + 1) == null) {
                continue;
            }
            if (map.get(key-1) != null){
                int len = 0;
                len += map.get(key-1);
                len += map.get(key);
                maxValue = Math.max(maxValue,len);
            }
            if (map.get(key+1) != null){
                int len = 0;
                len += map.get(key+1);
                len += map.get(key);
                maxValue = Math.max(maxValue,len);
            }

        }

        return maxValue;
    }
}
