package samle;

import java.util.*;

/**
 * 给定一个数组 candidates 和一个目标数 target ，找出 candidates 中所有可以使数字和为 target 的组合。
 *
 * candidates 中的每个数字在每个组合中只能使用一次。
 *
 * 说明：
 *
 * 所有数字（包括目标数）都是正整数。
 * 解集不能包含重复的组合。 
 * 示例 1:
 *
 * 输入: candidates = [10,1,2,7,6,1,5], target = 8,
 * 所求解集为:
 * [
 *   [1, 7],
 *   [1, 2, 5],
 *   [2, 6],
 *   [1, 1, 6]
 * ]
 * 示例 2:
 *
 * 输入: candidates = [2,5,2,1,2], target = 5,
 * 所求解集为:
 * [
 *   [1,2,2],
 *   [5]
 * ]
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/combination-sum-ii
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_40 {
    public static void main(String[] args) {
//        System.out.println(new L_40().combinationSum2(new int[]{1,1,2,5,6,7,10},8));
//        System.out.println(new L_40().combinationSum2(new int[]{1},2));
//        System.out.println(new L_40().combinationSum2(new int[]{4,1,1,4,4,4,4,2,3,5},10));
        System.out.println(new L_40().combinationSum2(new int[]{1,1,4,4,3},10));
        long now = System.currentTimeMillis();
        System.out.println(new L_40().combinationSum2(new int[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,},27));
        System.out.println(System.currentTimeMillis() - now);
    }

    List<List<Integer>>  result = new ArrayList<>();
    Set<String> sets = new HashSet<>();
    public List<List<Integer>> combinationSum2(int[] candidates, int target) {
        Arrays.sort(candidates);
        int sum = 0;
        for (int candidate : candidates) {
            sum+=target;
            if (sum>=target){
                break;
            }
        }
        if (sum < target){
            return null;
        }
        for (int i = 0; i < candidates.length; i++) {
            combinationSum(candidates,i,target,new LinkedList());
        }
        return result;
    }
    public void combinationSum(int[] candidates, int start,int target,LinkedList<Integer> ele) {
        if (target == 0){
            if (sets.add(ele.toString())){
                result.add((List<Integer>) ele.clone());
            }
            return;
        }
        if (target < 0){
            return;
        }
        for (int i = start; i < candidates.length && candidates[i] <= target; i++) {
            ele.push(candidates[i]);
            combinationSum(candidates,i+1,target-candidates[i],ele);
            ele.pop();
        }
    }
}
