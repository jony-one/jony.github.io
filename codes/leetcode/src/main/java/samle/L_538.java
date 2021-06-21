package samle;

/**
 * 给定一个二叉搜索树（Binary Search Tree），把它转换成为累加树（Greater Tree)，使得每个节点的值是原来的节点值加上所有大于它的节点值之和。
 *
 *  
 *
 * 例如：
 *
 * 输入: 原始二叉搜索树:
 *               5
 *             /   \
 *            2     13
 *
 * 输出: 转换为累加树:
 *              18
 *             /   \
 *           20     13
 *
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/convert-bst-to-greater-tree
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_538 {

    public static void main(String[] args) {
//        TreeNode roo = new TreeNode(5);
//        roo.left = new TreeNode(2);
//        roo.right = new TreeNode(13);
//        TreeNode r = new L_538().convertBST(roo);
//        System.out.println();
        TreeNode roo = new TreeNode(2);
        roo.left = new TreeNode(1);
        roo.right = new TreeNode(3);
        TreeNode r = new L_538().convertBST(roo);
        System.out.println();
    }

    TreeNode bak = null;
    public TreeNode convertBST(TreeNode root) {
        if (root == null){
            return null;
        }
        if (bak == null && root != null){
            bak = new TreeNode(root.val);
            copy(bak,root);
        }

        add(bak,root,root.val);
        convertBST(root.left);
        convertBST(root.right);
        return root;
    }

    public void add(TreeNode bak,TreeNode root,int initval){
        if (bak == null){
            return ;
        }
        if (bak == root){

        }else {
            if (initval < bak.val){
                root.val += bak.val;
            }
        }
        add(bak.left,root,initval);
        add(bak.right,root,initval);
    }

    public void copy(TreeNode r1,TreeNode rr){

        if (rr.right != null){
            r1.right = new TreeNode(rr.right.val);
            copy(r1.right,rr.right);
        }
        if (rr.left != null){
            r1.left = new TreeNode(rr.left.val);
            copy(r1.left,rr.left);
        }
    }
}
