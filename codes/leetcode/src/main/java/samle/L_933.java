package samle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class L_933 {


    public static void main(String[] args) {
        L_933 l = new L_933();
        System.out.println(l.ping(1));
        System.out.println(l.ping(100));
        System.out.println(l.ping(3001));
        System.out.println(l.ping(3002));
    }
    List<Integer> list = new ArrayList<>();
    public int ping(int t) {

        list.add(t);
        removeT(list,t);
        return list.size();
    }

    public void removeT(List<Integer> list,int t){
        t -= 3000;
        if (t<0){
            return;
        }
        Iterator<Integer> integerIterator = list.iterator();
        while (integerIterator.hasNext()){
            Integer integer = integerIterator.next();
            if (integer < t){
                integerIterator.remove();
            }
        }
    }

}
