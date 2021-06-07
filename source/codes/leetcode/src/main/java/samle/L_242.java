package samle;

import java.util.Arrays;

/**
 * 给定两个字符串 s 和 t ，编写一个函数来判断 t 是否是 s 的字母异位词。
 *
 * 示例 1:
 *
 * 输入: s = "anagram", t = "nagaram"
 * 输出: true
 * 示例 2:
 *
 * 输入: s = "rat", t = "car"
 * 输出: false
 * 说明:
 * 你可以假设字符串只包含小写字母。
 *
 * 进阶:
 * 如果输入字符串包含 unicode 字符怎么办？你能否调整你的解法来应对这种情况？
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/valid-anagram
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_242 {

    public static void main(String[] args) {

    }

    public boolean isAnagram(String s, String t) {
        if (s.length() != t.length()){
            return false;
        }
        char[] sums = new char[s.length()];
        char[] sumt = new char[s.length()];
        for (int i = 0; i < s.length(); i++) {
            sums[i] = s.charAt(i);
        }
        for (int i = 0; i < t.length(); i++) {
            sumt[i] = t.charAt(i);
        }
        Arrays.sort(sums);
        Arrays.sort(sumt);

        return new String(sums).equals(new String(sumt));
    }
}
