package samle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 给定一个由表示变量之间关系的字符串方程组成的数组，每个字符串方程 equations[i] 的长度为 4，并采用两种不同的形式之一："a==b" 或 "a!=b"。在这里，a 和 b 是小写字母（不一定不同），表示单字母变量名。
 *
 * 只有当可以将整数分配给变量名，以便满足所有给定的方程时才返回 true，否则返回 false。 
 *
 *  
 *
 * 示例 1：
 *
 * 输入：["a==b","b!=a"]
 * 输出：false
 * 解释：如果我们指定，a = 1 且 b = 1，那么可以满足第一个方程，但无法满足第二个方程。没有办法分配变量同时满足这两个方程。
 * 示例 2：
 *
 * 输入：["b==a","a==b"]
 * 输出：true
 * 解释：我们可以指定 a = 1 且 b = 1 以满足满足这两个方程。
 * 示例 3：
 *
 * 输入：["a==b","b==c","a==c"]
 * 输出：true
 * 示例 4：
 *
 * 输入：["a==b","b!=c","c==a"]
 * 输出：false
 * 示例 5：
 *
 * 输入：["c==c","b==d","x!=z"]
 * 输出：true
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/satisfiability-of-equality-equations
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_990 {

    public static void main(String[] args) {
        String[] a;

        a = new String[]{"b!=b"};
        System.out.println(new L_990().equationsPossible(a));
    }

    public boolean equationsPossible(String[] equations) {
        List<String> ne = new ArrayList<String>();
        Map<String,Object> map = new HashMap<String, Object>();
        for (String equation : equations) {
            String start = equation.substring(0,1);
            String end = equation.substring(3,4);
            if (equation.contains("==")){
                if (map.get(start) == null && map.get(end)== null){
                    map.put(start,new Object());
                    map.put(end,map.get(start));
                }else {
                    if (map.get(start) != null && map.get(end) != null){
                        key(map,map.get(start),map.get(end));
                    } else {
                        Object o = map.get(start) == null?map.get(end):map.get(start);
                        map.put(start,o);
                        map.put(end,o);
                    }
                }
            }else {
                ne.add(equation);
            }
        }


        for (String s : ne) {
            String start = s.substring(0,1);
            String end = s.substring(3,4);
            Object o1 = map.get(start);
            Object o2 = map.get(end);
            if ((o1 == o2 && o1 != null) || start.equals(end)){
                return false;
            }
        }

        return true;
    }

    private void key(Map<String,Object> m,Object o1,Object o2){
        List<String> keys = new ArrayList<String>();
        for (Map.Entry<String, Object> stringObjectEntry : m.entrySet()) {
            if (stringObjectEntry.getValue() == o2){
                keys.add(stringObjectEntry.getKey());
            }
        }
        for (String key : keys) {
            m.put(key,o1);
        }
    }
}
