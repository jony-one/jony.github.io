package samle;

/**
 * 字符串轮转。给定两个字符串s1和s2，请编写代码检查s2是否为s1旋转而成（比如，waterbottle是erbottlewat旋转后的字符串）。
 * <p>
 * 示例1:
 * <p>
 * 输入：s1 = "waterbottle", s2 = "erbottlewat"
 * 输出：True
 * 示例2:
 * <p>
 * 输入：s1 = "aa", s2 = "aba"
 * 输出：False
 * 提示：
 * <p>
 * 字符串长度在[0, 100000]范围内。
 * 说明:
 * <p>
 * 你能只调用一次检查子串的方法吗？
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/string-rotation-lcci
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_01_09 {

    public static void main(String[] args) {
        System.out.println(new L_01_09().isFlipedString("waterbottle", "erbottlewat"));
    }

    public boolean isFlipedString(String s1, String s2) {
        if (s1 == null || s2 == null){
            return false;
        }
        if (s1.length() != s2.length()) {
            return false;
        }
        if (s1.equals(s2)){
            return true;
        }
        int le = s1.length();
        StringBuilder stringBuilder1 = new StringBuilder(s1);
        for (int i = 0; i < le; i++) {
            String s = s1.substring(i, i + 1);
            stringBuilder1.append(s);
            if (stringBuilder1.toString().indexOf(s2) > 0){
                return true;
            }
        }
        return false;
    }
}
