package samle;

/**
 * 回旋镖定义为一组三个点，这些点各不相同且不在一条直线上。
 *
 * 给出平面上三个点组成的列表，判断这些点是否可以构成回旋镖。
 *
 *  
 *
 * 示例 1：
 *
 * 输入：[[1,1],[2,3],[3,2]]
 * 输出：true
 * 示例 2：
 *
 * 输入：[[1,1],[2,2],[3,3]]
 * 输出：false
 *  
 *
 * 提示：
 *
 * points.length == 3
 * points[i].length == 2
 * 0 <= points[i][j] <= 100
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/valid-boomerang
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_1037 {
    public static void main(String[] args) {
        int[][] points = new int[][]{{1,1},{2,3},{3,2}};
        System.out.println(new L_1037().isBoomerang(points));

        points = new int[][]{{1,1},{2,2},{3,3}};
        System.out.println(new L_1037().isBoomerang(points));
        points = new int[][]{{0,0},{1,0},{2,2}};
        System.out.println(new L_1037().isBoomerang(points));
    }

    /**
     * ...... 余弦定理
     * @param points
     * @return
     */
    public boolean isBoomerang(int[][] points) {
        int max = 0;
        for (int i = 0; i < points.length; i++) {
            for (int j = 0; j < points[i].length; j++) {
                if (max < points[i][j]){
                    max = points[i][j];
                }
            }
        }

        int[][] ints = new int[max+1][max+1];
        for (int i = 0; i <= max; i++) {
            for (int j = 0; j <= max; j++) {

                for (int k = 0; k < points.length; k++) {
                    if (i == points[k][0] && j == points[k][1]){
                        ints[i][j] = 1;
                    }else {
                        ints[i][j] = 0;
                    }
                }

            }
        }

        for (int i = 0; i <= max; i++) {
            int sum = 0;
            for (int j = 0; j <= max; j++) {
                sum += ints[i][j];
                sum += ints[j][i];
            }
            if (sum > 1){
                return false;
            }
        }

        return true;
    }
}
