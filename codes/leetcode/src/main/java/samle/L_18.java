package samle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 给定一个包含 n 个整数的数组 nums 和一个目标值 target，
 * 判断 nums 中是否存在四个元素 a，b，c 和 d ，使得 a + b + c + d 的值与 target 相等？找出所有满足条件且不重复的四元组。
 *
 * 注意：
 *
 * 答案中不可以包含重复的四元组。
 *
 * 示例：
 *
 * 给定数组 nums = [1, 0, -1, 0, -2, 2]，和 target = 0。
 *
 * 满足要求的四元组集合为：
 * [
 *   [-1,  0, 0, 1],
 *   [-2, -1, 1, 2],
 *   [-2,  0, 0, 2]
 * ]
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/4sum
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_18 {
    public static void main(String[] args) {
        new L_18().fourSum(new int[]{-497,-494,-484,-477,-453,-453,-444,-442,-428,-420,-401,-393,-392,-381,-357,-357,-327,-323,-306,-285,-284,-263,-262,-254,-243,-234,-208,-170,-166,-162,-158,-136,-133,-130,-119,-114,-101,-100,-86,-66,-65,-6,1,3,4,11,69,77,78,107,108,108,121,123,136,137,151,153,155,166,170,175,179,211,230,251,255,266,288,306,308,310,314,321,322,331,333,334,347,349,356,357,360,361,361,367,375,378,387,387,408,414,421,435,439,440,441,470,492},1682);
    }

    public List<List<Integer>> fourSum(int[] nums, int target) {

        int[] b = new int[4];
        sum(nums,target,0,-1,b,0);
        System.out.println(lists.size());
        return lists;
    }
    List<List<Integer>> lists = new ArrayList<List<Integer>>();
    public void sum(int[] nums,int target,int sum,int current_index,int[] arr,int deep){
        if (deep > 3){
            return;
        }
        if (deep == 3){
            for (int i = current_index+1; i < nums.length; i++) {
                if (sum + nums[i] == target){

                    Integer[] arrays = new Integer[4];
                    arr[deep] = i;
                    for (int j = 0; j <= deep; j++) {
                        arrays[j] = nums[arr[j]];
                    }
                    Arrays.sort(arrays);
                    boolean exist = false;
                    for (List<Integer> list : lists) {
                        Integer[] listArr = new Integer[4];
                        list.toArray(listArr);
                        Arrays.sort(listArr);
                        if (listArr[0].equals(arrays[0]) && listArr[1].equals(arrays[1]) && listArr[2].equals(arrays[2]) && listArr[3].equals(arrays[3])){
                            exist = true;
                        }
                    }
                    if (!exist){
                        lists.add(Arrays.asList(arrays));
                    }
                }
            }
        }
        for (int i = current_index+1; i < nums.length; i++) {
            arr[deep] = i;
            sum(nums,target,sum + nums[i],i,arr,deep+1);
        }
        return;
    }
}
