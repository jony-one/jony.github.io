package samle;

/**
 * 统计字符串中的单词个数，这里的单词指的是连续的不是空格的字符。
 *
 * 请注意，你可以假定字符串里不包括任何不可打印的字符。
 *
 * 示例:
 *
 * 输入: "Hello, my name is John"
 * 输出: 5
 * 解释: 这里的单词是指连续的不是空格的字符，所以 "Hello," 算作 1 个单词。
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/number-of-segments-in-a-string
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_434 {

    public static void main(String[] args) {
        System.out.println(new L_434().countSegments("Hello,  my name is John"));
    }

    public int countSegments(String s) {
        String[] a = s.split(" ");
        int l = a.length;
        for (String s1 : a) {
            if (s1.length() == 0){
                --l;
            }
        }
        return l;
    }
}
