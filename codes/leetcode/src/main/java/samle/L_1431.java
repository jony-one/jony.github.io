package samle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class L_1431 {
    int maxnum;
    public static void main(String[] args) {
        int[] candies = new int[]{2,3,5,1,3};
        List<Boolean> candies_ = new L_1431().kidsWithCandies(candies,3);
        System.out.println(candies_);
    }
    public List<Boolean> kidsWithCandies(int[] candies, int extraCandies) {

        List<Boolean> result = new ArrayList<Boolean>();
        if (candies.length == 0) {
            return  result;
        }
        max(candies,result,extraCandies,0);
        Collections.reverse(result);
        return result;
    }
    public void max(int[] candies,List<Boolean> result,int extraCandies,int index) {
        if (candies.length > index ){
            if (candies[index] > maxnum) {
                maxnum = candies[index];
            }
            max(candies,result,extraCandies,index+1);
            if (candies[index]+extraCandies >= maxnum) {
               result.add(true);
            }else {
                result.add(false);
            }
        }
    }
}
