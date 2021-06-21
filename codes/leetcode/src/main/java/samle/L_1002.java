package samle;

import java.util.ArrayList;
import java.util.List;

/**
 * 给定仅有小写字母组成的字符串数组 A，返回列表中的每个字符串中都显示的全部字符（包括重复字符）组成的列表。例如，如果一个字符在每个字符串中出现 3 次，但不是 4 次，则需要在最终答案中包含该字符 3 次。
 *
 * 你可以按任意顺序返回答案。
 *
 *  
 *
 * 示例 1：
 *
 * 输入：["bella","label","roller"]
 * 输出：["e","l","l"]
 * 示例 2：
 *
 * 输入：["cool","lock","cook"]
 * 输出：["c","o"]
 *  
 *
 * 提示：
 *
 * 1 <= A.length <= 100
 * 1 <= A[i].length <= 100
 * A[i][j] 是小写字母
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/find-common-characters
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_1002 {

    public static void main(String[] args) {
        System.out.println(new L_1002().commonChars(new String[]{"ba","la","ra"}));

    }
    public List<String> commonChars(String[] A) {
        String min_str = null;
        int min_len = Integer.MAX_VALUE;
        for (String s : A) {
            if (s.length() < min_len){
                min_len = s.length();
                min_str = s;
            }
        }
        if (min_len == 0){
            return new ArrayList<>();
        }
        List<String> arr = new ArrayList<>();
        char[] chars = min_str.toCharArray();
        for (char aChar : chars) {
            int size = 0;
            for (int i = 0; i < A.length; i++) {
                if (A[i].indexOf(aChar) >= 0){
                    try {
                        A[i] = A[i].substring(0,A[i].indexOf(aChar)) + A[i].substring(A[i].indexOf(aChar)+1);
                    }catch (Exception e){
                        A[i] = A[i].substring(0,A[i].indexOf(aChar));
                    }
                    size+=1;
                }
            }
            if (size == A.length){
                arr.add(String.valueOf(aChar));
            }
        }
        return arr;
    }
}
