package samle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 假设Andy和Doris想在晚餐时选择一家餐厅，并且他们都有一个表示最喜爱餐厅的列表，每个餐厅的名字用字符串表示。
 * <p>
 * 你需要帮助他们用最少的索引和找出他们共同喜爱的餐厅。 如果答案不止一个，则输出所有答案并且不考虑顺序。 你可以假设总是存在一个答案。
 * <p>
 * 示例 1:
 * <p>
 * 输入:
 * ["Shogun", "Tapioca Express", "Burger King", "KFC"]
 * ["Piatti", "The Grill at Torrey Pines", "Hungry Hunter Steakhouse", "Shogun"]
 * 输出: ["Shogun"]
 * 解释: 他们唯一共同喜爱的餐厅是“Shogun”。
 * 示例 2:
 * <p>
 * 输入:
 * ["Shogun", "Tapioca Express", "Burger King", "KFC"]
 * ["KFC", "Shogun", "Burger King"]
 * 输出: ["Shogun"]
 * 解释: 他们共同喜爱且具有最小索引和的餐厅是“Shogun”，它有最小的索引和1(0+1)。
 * 提示:
 * <p>
 * 两个列表的长度范围都在 [1, 1000]内。
 * 两个列表中的字符串的长度将在[1，30]的范围内。
 * 下标从0开始，到列表的长度减1。
 * 两个列表都没有重复的元素。
 */
public class L_599 {
    public static void main(String[] args) {

        String[] result = new L_599().findRestaurant(new String[]{"Shogun", "Tapioca Express", "Burger King", "KFC"}, new String[]{"Piatti", "The Grill at Torrey Pines", "Hungry Hunter Steakhouse", "Shogun"});
        System.out.println(result);
    }

    public String[] findRestaurant(String[] list1, String[] list2) {
        Map<String, Integer> map = new HashMap<>();
        int base =  10000;
        Integer maxValue = 0;

        for (int i = 0; i < list1.length; i++) {
            if (map.get(list1[i]) == null) {
                map.put(list1[i], base + i);
            } else {
                map.put(list1[i], map.get(list1[i]) + i + base);
            }
            if (map.get(list1[i]) / base > maxValue) {
                maxValue = map.get(list1[i]) / base;
            }

        }

        for (int i = 0; i < list2.length; i++) {
            if (map.get(list2[i]) == null) {
                map.put(list2[i], base + i);
            } else {
                map.put(list2[i], map.get(list2[i]) + i + base);
            }
            if (map.get(list2[i]) / base > maxValue) {
                maxValue = map.get(list2[i]) / base;
            }

        }

        Set<String> sets = map.keySet();
        String[] strings = new String[sets.size()];
        sets.toArray(strings);
        Integer minIndex = Integer.MAX_VALUE;
        for (String key : strings) {
            if (map.get(key) / base != maxValue) {
                map.remove(key);
            } else {
                if (map.get(key) % base < minIndex) {
                    minIndex = map.get(key) % base;
                }
            }
        }


        sets = map.keySet();
        strings = new String[sets.size()];
        sets.toArray(strings);
        for (String key : strings) {
            if (map.get(key) % base != minIndex) {
                map.remove(key);
            }
        }



        strings = new String[map.keySet().size()];
        for (String string : map.keySet()) {
            System.out.println(string);
        }
        return map.keySet().toArray(strings);
    }
}
