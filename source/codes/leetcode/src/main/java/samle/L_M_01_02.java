package samle;


import java.util.Arrays;

/**
 * 给定两个字符串 s1 和 s2，请编写一个程序，确定其中一个字符串的字符重新排列后，能否变成另一个字符串。
 *
 * 示例 1：
 *
 * 输入: s1 = "abc", s2 = "bca"
 * 输出: true
 * 示例 2：
 *
 * 输入: s1 = "abc", s2 = "bad"
 * 输出: false
 * 说明：
 *
 * 0 <= len(s1) <= 100
 * 0 <= len(s2) <= 100
 *
 */
public class L_M_01_02 {

    public static void main(String[] args) {
        System.out.println(new L_M_01_02().CheckPermutation("",""));
    }

    public boolean CheckPermutation(String s1, String s2) {

        if (s1== null || s2 == null) {
            return false;
        }

        char[] c1 =s1.toCharArray();
        char[] c2 =s2.toCharArray();
        Arrays.sort(c1);
        Arrays.sort(c2);
        return new String(c1).equals(new String(c2));
    }
}
