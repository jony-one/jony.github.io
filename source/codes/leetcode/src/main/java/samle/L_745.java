package samle;

import java.util.HashMap;
import java.util.Map;

/**
 * 给定多个 words，words[i] 的权重为 i 。
 *
 * 设计一个类 WordFilter 实现函数WordFilter.f(String prefix, String suffix)。这个函数将返回具有前缀 prefix 和后缀suffix 的词的最大权重。如果没有这样的词，返回 -1。
 *
 * 例子:
 *
 * 输入:
 * WordFilter(["apple"])
 * WordFilter.f("a", "e") // 返回 0
 * WordFilter.f("b", "") // 返回 -1
 * 注意:
 *
 * words的长度在[1, 15000]之间。
 * 对于每个测试用例，最多会有words.length次对WordFilter.f的调用。
 * words[i]的长度在[1, 10]之间。
 * prefix, suffix的长度在[0, 10]之前。
 * words[i]和prefix, suffix只包含小写字母。
 *
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/prefix-and-suffix-search
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_745 {
    public static void main(String[] args) {
        WordFilter wordFilter = new L_745().new WordFilter(new String[]{
                "b","b","b","b",
        });
        System.out.println(wordFilter.f("b",""));

    }

    class WordFilter {
        Map<String,Integer> value = new HashMap<>();
        Map<String,Integer> map =new HashMap<>();

        public WordFilter(String[] words) {
            for (int i = 0; i < words.length; i++) {
                value.put(words[i],i);
            }
        }

        public int f(String prefix, String suffix) {
            String key = new StringBuilder(prefix).append("@_@").append(suffix).toString();
            Integer num = map.get(key);
            if (num != null){
                return num;
            }
            int a = -1;
            for (String item : value.keySet()) {
                boolean start = item.startsWith(prefix);
                boolean end = item.endsWith(suffix);
                if (start && end){
                    a = Math.max(a,value.getOrDefault(item,-1));
                }
            }
            map.put(key,a);
            return a;
        }



//        public int f(String prefix, String suffix) {
//            String key = new StringBuilder(prefix).append("@_@").append(suffix).toString();
//            Integer num = map.get(key);
//            if (num != null){
//                return num;
//            }
//            int a = -1;
//            int l = words.length;
//            for (int i = 0; i <= l/2; i++) {
//                boolean start = words[i].startsWith(prefix);
//                boolean end = words[i].endsWith(suffix);
//                if (start && end){
//                    a = Math.max(a,i);
//                }
//
//                start = words[l-i-1].startsWith(prefix);
//                end = words[l-i-1].endsWith(suffix);
//                if (start && end){
//                    a = Math.max(a,l-i-1);
//                }
//            }
//            return a;
//        }


//                9
//                4
//                5
//                0
//                8
//                1
//                2
//                5
//                3
//                1

//        public int f(String prefix, String suffix) {
//            b = -1;
//            f(prefix,suffix,0,words.length-1);
//            return b;
//        }
//        int b;
//        public void f(String prefix, String suffix,int startIndex,int endIndex) {
//            if (startIndex == endIndex){
//                try {
//                    boolean start = words[startIndex].startsWith(prefix);
//                    boolean end = words[endIndex].endsWith(suffix);
//                    if (start && end){
//                        b = Math.max(b,startIndex);
//                    }
//                }catch (StackOverflowError e){
//                    System.out.println(e);
//                }
//            } else {
//                f(prefix,suffix,startIndex,(startIndex+endIndex)/2);
//                f(prefix,suffix,(startIndex+endIndex)/2+1,endIndex);
//            }
//        }
    }
}

