package samle;

/**
 * 给定一个二叉树，找出其最小深度。
 *
 * 最小深度是从根节点到最近叶子节点的最短路径上的节点数量。
 *
 * 说明: 叶子节点是指没有子节点的节点。
 *
 * 示例:
 *
 * 给定二叉树 [3,9,20,null,null,15,7],
 *
 *     3
 *    / \
 *   9  20
 *     /  \
 *    15   7
 * 返回它的最小深度  2.
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/minimum-depth-of-binary-tree
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_111 {
    public static void main(String[] args) {
//        TreeNode root = new TreeNode(3);
//        TreeNode left = new TreeNode(9);
//        root.left = left;
//        TreeNode rootrl = new TreeNode(15);
//        TreeNode rootrr = new TreeNode(7);
//        TreeNode rootr = new TreeNode(20);
//        rootr.left = rootrl;
//        rootr.right = rootrr;
//        root.right = rootr;
//        System.out.println(new L_111().minDepth(root));
        TreeNode root = new TreeNode(2);
        root.right =new TreeNode(3);
        root.right.right =new TreeNode(4);
        root.right.right.right =new TreeNode(5);
        root.right.right.right.right =new TreeNode(6);
        System.out.println(new L_111().minDepth(root));
    }

    int dep = Integer.MAX_VALUE;
    public int minDepth(TreeNode root) {
        if (root == null){
            return 0;
        }
        if (root.left == null && root.right == null){
            return 1;
        }
        minDepth(root.left,1);
        minDepth(root.right,1);
        return dep;
    }
    public void minDepth(TreeNode root,int depth){
        if (root == null){
            return;
        }
        depth+=1;
        if (root.left == null && root.right == null){
            if (depth < dep){
                dep = depth;
            }
            return;
        }
        if (root.left != null){
            minDepth(root.left,depth);
        }
        if (root.right != null){
            minDepth(root.right,depth);
        }
    }
}
