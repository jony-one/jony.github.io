package samle;

import java.util.ArrayList;
import java.util.List;

/**
 * 给定一个二叉树，返回所有从根节点到叶子节点的路径。
 *
 * 说明: 叶子节点是指没有子节点的节点。
 *
 * 示例:
 *
 * 输入:
 *
 *    1
 *  /   \
 * 2     3
 *  \
 *   5
 *
 * 输出: ["1->2->5", "1->3"]
 *
 * 解释: 所有根节点到叶子节点的路径为: 1->2->5, 1->3
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/binary-tree-paths
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_257 {
    public static void main(String[] args) {
        TreeNode treeNode = new TreeNode(1);
        treeNode.left = new TreeNode(2);
        treeNode.left.right = new TreeNode(5);
        treeNode.right = new TreeNode(3);
        List<String> a = new L_257().binaryTreePaths(treeNode);
        for (String s : a) {
            System.out.println(s);
        }
    }
    List<String> array = new ArrayList<>();

    public List<String> binaryTreePaths(TreeNode root) {
        if (root == null){
            return array;
        }
        binaryTreePaths(root,"");

        return array;
    }

    public void  binaryTreePaths(TreeNode root,String s){

        if (root.left == null && root.right == null){
            String a = s + root.val;
            array.add(a);
            return;
        }
        if (root.left != null){
            String a = s + root.val + "->";
            binaryTreePaths(root.left,a);
        }
        if (root.right != null){
            String a = s + root.val + "->";
            binaryTreePaths(root.right,a);
        }
    }



}
