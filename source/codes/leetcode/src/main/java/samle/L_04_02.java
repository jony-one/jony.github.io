package samle;

import java.util.Arrays;

public class L_04_02 {
    public static void main(String[] args) {
        System.out.println(new L_04_02().sortedArrayToBST(new int[]{-10, -3, 0, 5}));
    }

    public TreeNode sortedArrayToBST(int[] nums) {
        if (nums == null || nums.length == 0){
            return null;
        }
       TreeNode root = new TreeNode(nums[nums.length / 2]);
        root.left = sortedArrayToBST(Arrays.copyOfRange(nums,0,nums.length/2));
        root.right = sortedArrayToBST(Arrays.copyOfRange(nums,nums.length/2+1,nums.length));
        return root;
    }
//    public TreeNode sortedArrayToBST(int[] nums) {
//        if (nums == null || nums.length == 0){
//            return null;
//        }
//        root = new TreeNode(nums[nums.length / 2]);
//        for (int i = 1; i <= nums.length / 2; i++) {
//            if (!(nums.length / 2 + i >= nums.length)) {
//                inset(root, nums[nums.length / 2 + i]);
//            }
//            if (!(nums.length / 2 - i < 0)) {
//                inset(root, nums[nums.length / 2 - i]);
//            }
//        }
//        return root;
//    }

    public void inset(TreeNode treeNode, int i) {
        if (treeNode.val > i) {
            if (treeNode.left == null) {
                treeNode.left = new TreeNode(i);
                return;
            }
            inset(treeNode.left, i);
        } else {
            if (treeNode.right == null) {
                treeNode.right = new TreeNode(i);
                return;
            }
            inset(treeNode.right, i);
        }
    }
}
