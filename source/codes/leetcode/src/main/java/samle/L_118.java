package samle;

import java.util.ArrayList;
import java.util.List;

public class L_118 {
    public static void main(String[] args) {
        List<List<Integer>> a = new L_118().generate(5);
        for (List<Integer> integers : a) {
            for (Integer integer : integers) {
                System.out.print(integer + ",");
            }
            System.out.println();
        }
    }

    public List<List<Integer>> generate(int numRows) {
        List<List<Integer>> lists = new ArrayList<>();
        if (numRows <= 0){
            return lists;
        }

        List<Integer> integers =new ArrayList<>();
        integers.add(1);
        lists.add(integers);
        if (numRows == 1){
            return lists;
        }

        List<Integer> integers2 =new ArrayList<>();
        integers2.add(1);
        integers2.add(1);
        lists.add(integers2);
        if (numRows == 2){
            return lists;
        }
        List<Integer> a = integers2;
        for (int i = 2; i < numRows; i++) {
            List<Integer> ll =new ArrayList<>();
            ll.add(1);
            for (int k = 0; k < a.size()-1; k++) {
                ll.add(a.get(k) + a.get(k+1));
            }
            ll.add(1);
            a = ll;
            lists.add(ll);
        }
        return lists;
    }
}
