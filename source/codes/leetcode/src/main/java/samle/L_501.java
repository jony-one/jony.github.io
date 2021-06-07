package samle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 给定一个有相同值的二叉搜索树（BST），找出 BST 中的所有众数（出现频率最高的元素）。
 *
 * 假定 BST 有如下定义：
 *
 * 结点左子树中所含结点的值小于等于当前结点的值
 * 结点右子树中所含结点的值大于等于当前结点的值
 * 左子树和右子树都是二叉搜索树
 * 例如：
 * 给定 BST [1,null,2,2],
 *
 *    1
 *     \
 *      2
 *     /
 *    2
 * 返回[2].
 *
 * 提示：如果众数超过1个，不需考虑输出顺序
 *
 * 进阶：你可以不使用额外的空间吗？（假设由递归产生的隐式调用栈的开销不被计算在内）
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/find-mode-in-binary-search-tree
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_501 {
    public static void main(String[] args) {
        System.out.println(new L_501().findMode(null));
    }

    Map<Integer,Integer> map = new HashMap<>();
    public int[] findMode(TreeNode root) {
        findMode2(root);
        int max = Integer.MIN_VALUE;
        for (Integer value : map.values()) {
            if (max < value){
                max = value;
            }
        }
        List<Integer> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> integerIntegerEntry : map.entrySet()) {
            if (integerIntegerEntry.getValue() == max){
                list.add(integerIntegerEntry.getKey());
            }
        }
        int[] lll = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            lll[i] = list.get(i);
        }
        return lll;
    }
    public void findMode2(TreeNode root) {
        if (root == null){
            return;
        }
        map.put(root.val,map.getOrDefault(root.val,0)+1);
        findMode2(root.right);
        findMode2(root.left);
    }
}
