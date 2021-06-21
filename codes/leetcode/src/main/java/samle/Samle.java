package samle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Samle {
    public static void main(String[] args) {
        int a = new Samle().findTheLongestSubstring("a");
        System.out.println(a);
    }

    public int findTheLongestSubstring(String s) {
        // 双层遍历，遍历每个字符为开始字符，一直到结尾且剩余字符数量大于当前数量。
        // 每个字符遍历之后，写入一个数组列表中，每遍历一个字符元加一，如果遇到偶数0就地推一，否则不往前推
        int startIndex =0;
        int lastIndex = 0;
        int maxLength = lastIndex - startIndex;
        for(int i =0;i < s.length() && s.length()-i > maxLength;i++){
            startIndex = i;
            eles.clear();
            for(int j =i;j< s.length();j++){
                int sum = putEle(s.charAt(j));
                if(sum ==0){
                    lastIndex = j+1;
                }
            }

            maxLength = ((maxLength > lastIndex - startIndex)?maxLength:(lastIndex - startIndex));
        }
        return maxLength;
    }
    Map<Character,Integer> eles = new HashMap();
    public int putEle(char c){
        if(c ==  'a'|| c == 'e' || c=='i'|| c == 'o'|| c =='u'){
            if(eles.get(c) == null || eles.get(c) == 0){
                eles.put(c,1);
            } else if(eles.get(c) == 1){
                eles.put(c,0);
            }
        }
        // foreach 遍历求和返回值
        Iterator<Integer> iterator =  eles.values().iterator();
        Integer sum = 0;
        while (iterator.hasNext()){
            sum += iterator.next();
        }
        return sum;
    }
}
