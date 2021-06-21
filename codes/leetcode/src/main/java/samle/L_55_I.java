package samle;

public class L_55_I {
    public static void main(String[] args) {
        System.out.println(new L_55_I().maxDepth(null));
    }

    public int maxDepth(TreeNode root) {
        if (root == null){
            return 0;
        }

        return 1 + Math.max(maxDepth(root.left),maxDepth(root.right));
    }
}
