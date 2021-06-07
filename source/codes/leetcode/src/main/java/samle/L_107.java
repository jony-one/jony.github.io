package samle;

/**
 * 给定一个二叉树，返回其节点值自底向上的层次遍历。 （即按从叶子节点所在层到根节点所在的层，逐层从左向右遍历）
 *
 * 例如：
 * 给定二叉树 [3,9,20,null,null,15,7],
 *
 *     3
 *    / \
 *   9  20
 *     /  \
 *    15   7
 * 返回其自底向上的层次遍历为：
 *
 * [
 *   [15,7],
 *   [9,20],
 *   [3]
 * ]
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/binary-tree-level-order-traversal-ii
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_107 {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(3);
        TreeNode left = new TreeNode(9);
        root.left = left;
        TreeNode rootrl = new TreeNode(15);
        TreeNode rootrr = new TreeNode(7);
        TreeNode rootr = new TreeNode(20);
        rootr.left = rootrl;
        rootr.right = rootrr;
        root.right = rootr;
        new L_107().levelOrderBottom(root);
    }
    public void levelOrderBottom(TreeNode root) {
        if (root == null){
            return;
        }
        levelOrderBottom(root.left);
        levelOrderBottom(root.right);
        System.out.println(root.val);
    }
}
