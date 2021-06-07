package samle;

import java.util.TreeSet;

/**
 * 给定一组字符，使用原地算法将其压缩。
 * <p>
 * 压缩后的长度必须始终小于或等于原数组长度。
 * <p>
 * 数组的每个元素应该是长度为1 的字符（不是 int 整数类型）。
 * <p>
 * 在完成原地修改输入数组后，返回数组的新长度。
 * <p>
 *  
 * <p>
 * 进阶：
 * 你能否仅使用O(1) 空间解决问题？
 * <p>
 *  
 * <p>
 * 示例 1：
 * <p>
 * 输入：
 * ["a","a","b","b","c","c","c"]
 * <p>
 * 输出：
 * 返回 6 ，输入数组的前 6 个字符应该是：["a","2","b","2","c","3"]
 * <p>
 * 说明：
 * "aa" 被 "a2" 替代。"bb" 被 "b2" 替代。"ccc" 被 "c3" 替代。
 * 示例 2：
 * <p>
 * 输入：
 * ["a"]
 * <p>
 * 输出：
 * 返回 1 ，输入数组的前 1 个字符应该是：["a"]
 * <p>
 * 解释：
 * 没有任何字符串被替代。
 * 示例 3：
 * <p>
 * 输入：
 * ["a","b","b","b","b","b","b","b","b","b","b","b","b"]
 * <p>
 * 输出：
 * 返回 4 ，输入数组的前4个字符应该是：["a","b","1","2"]。
 * <p>
 * 解释：
 * 由于字符 "a" 不重复，所以不会被压缩。"bbbbbbbbbbbb" 被 “b12” 替代。
 * 注意每个数字在数组中都有它自己的位置。
 *  
 * <p>
 * 提示：
 * <p>
 * 所有字符都有一个ASCII值在[35, 126]区间内。
 * 1 <= len(chars) <= 1000。
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/string-compression
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_443 {

    public static void main(String[] args) {
        System.out.println(new L_443().compress(new char[]{'a','a','b','b','c','c','c'}));
    }

    public int compress(char[] chars) {
        TreeSet<Character> treeSet = new TreeSet<>();
        StringBuilder builder = new StringBuilder();
        treeSet.add(chars[0]);
        int i = 0;
        for (char aChar : chars) {
            if (treeSet.add(aChar)) {
                treeSet.remove(aChar);
                if (i > 1){
                    builder.append(treeSet.first()).append(i);
                }else {
                    builder.append(treeSet.first());
                }
                treeSet.clear();
                i=1;
                treeSet.add(aChar);
            }else {
                i+=1;
            }
        }
        if (i > 1){
            builder.append(treeSet.first()).append(i);
        }else {
            builder.append(treeSet.first());
        }
        String a = builder.toString();
        for (int j = 0; j < a.length(); j++) {
            chars[j] = a.charAt(j);
        }
        return builder.length();
    }
}
