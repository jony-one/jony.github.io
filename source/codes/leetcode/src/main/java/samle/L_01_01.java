package samle;


import java.util.HashSet;
import java.util.Set;

/**
 * 实现一个算法，确定一个字符串 s 的所有字符是否全都不同。
 *
 * 示例 1：
 *
 * 输入: s = "leetcode"
 * 输出: false
 * 示例 2：
 *
 * 输入: s = "abc"
 * 输出: true
 * 限制：
 *
 * 0 <= len(s) <= 100
 * 如果你不使用额外的数据结构，会很加分。
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/is-unique-lcci
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_01_01 {

    public static void main(String[] args) {

    }
    public boolean isUnique(String astr) {
        if (astr == null){
            return true;
        }
        if (astr.length() == 0){
            return true;
        }
        Set<Character> set = new HashSet<>();
        for (int i = 0; i < astr.length(); i++) {
            set.add(astr.charAt(i));
        }
        return set.size() == astr.length();
    }
}
