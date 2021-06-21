package samle;

/**
 * 给定一个二叉树，找出其最大深度。
 *
 * 二叉树的深度为根节点到最远叶子节点的最长路径上的节点数。
 *
 * 说明: 叶子节点是指没有子节点的节点。
 *
 * 示例：
 * 给定二叉树 [3,9,20,null,null,15,7]，
 *
 *     3
 *    / \
 *   9  20
 *     /  \
 *    15   7
 * 返回它的最大深度 3 。
 *
 */
public class L_104 {

    public int maxDepth(TreeNode root) {
        if (root == null){
            return  0;
        }
        maxDepth(root,0);
        return max_;
    }

    int max_ = 0;

    public void maxDepth(TreeNode root,int max) {
        if (root == null){
            max_ = Math.max(max,max_);
            return;
        }
        maxDepth(root.left,max+1);
        maxDepth(root.right,max+1);

    }

    class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;
        TreeNode(int x) { val = x; }
    }
}
