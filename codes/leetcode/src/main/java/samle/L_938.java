package samle;

public class L_938 {

    public static void main(String[] args) {

    }
    int sum=0;
    public int rangeSumBST(TreeNode root, int L, int R) {
        s(root,L,R);
        return sum;
    }

    private void s(TreeNode root, int L, int R){
        if(root == null){
            return;
        }
        if (root.val >= L && root.val <= R){
            sum += root.val;
        }

        if (L < root.val){
            s(root.left,L,R);
        }
        if (R > root.val){
            s(root.right,L,R);
        }
    }

}
