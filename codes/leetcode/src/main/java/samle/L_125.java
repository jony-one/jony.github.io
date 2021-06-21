package samle;

/**
 * 给定一个字符串，验证它是否是回文串，只考虑字母和数字字符，可以忽略字母的大小写。
 *
 * 说明：本题中，我们将空字符串定义为有效的回文串。
 *
 * 示例 1:
 *
 * 输入: "A man, a plan, a canal: Panama"
 * 输出: true
 * 示例 2:
 *
 * 输入: "race a car"
 * 输出: false
 *
 */
public class L_125 {

    public static void main(String[] args) {
        System.out.println(new L_125().isPalindrome("A man, a plan, a canal: Panama"));
    }

    public boolean isPalindrome(String s) {
        if (s == null){
            return false;
        }
        if (s.length() == 0 || s.length() == 1){
            return true;
        }

        StringBuffer s1 = new StringBuffer();

        for (int i = 0; i < s.length(); i++) {
           if (Character.isLetterOrDigit(s.charAt(i))){
               s1.append(Character.toLowerCase(s.charAt(i)));
           }
        }

        s = s1.toString();
        String res = s1.reverse().toString();

        return s.equals(res);
    }

}
