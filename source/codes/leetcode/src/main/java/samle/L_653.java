package samle;

import java.util.HashSet;
import java.util.Set;

public class L_653 {

    Set<Integer> set = new HashSet<>();

    public boolean findTarget(TreeNode root, int k) {
        bsd(root);
        for (Integer integer : set) {
            int a = k - integer;
            if (a == k - a) {
                return false;
            }
            if (set.contains(a)) {
                return true;
            }
        }
        return false;
    }

    public void bsd(TreeNode root) {
        if (root == null) {
            return;
        }
        set.add(root.val);
        bsd(root.left);
        bsd(root.right);
    }
}
