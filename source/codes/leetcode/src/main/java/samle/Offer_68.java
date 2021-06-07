package samle;

public class Offer_68 {

    public static void main(String[] args) {

    }
    TreeNode g = null;
    public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null){
            return null;
        }
        if (a(root,p) && a(root,q)){
            if (g == null){
                g = root;
            }
        }
        lowestCommonAncestor(root.left,p,q);
        lowestCommonAncestor(root.right,p,q);

        return g;
    }
    public boolean a(TreeNode root,TreeNode p){
        if (root == null){
            return false;
        }
        if (root == p){
            return true;
        }
        return a(root.left,p) || a(root.right,p);
    }
}
