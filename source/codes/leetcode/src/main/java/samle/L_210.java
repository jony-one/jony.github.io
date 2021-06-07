package samle;


import java.util.*;

/**
 * 现在你总共有 n 门课需要选，记为 0 到 n-1。
 * <p>
 * 在选修某些课程之前需要一些先修课程。 例如，想要学习课程 0 ，你需要先完成课程 1 ，我们用一个匹配来表示他们: [0,1]
 * <p>
 * 给定课程总量以及它们的先决条件，返回你为了学完所有课程所安排的学习顺序。
 * <p>
 * 可能会有多个正确的顺序，你只要返回一种就可以了。如果不可能完成所有课程，返回一个空数组。
 * <p>
 * 示例 1:
 * <p>
 * 输入: 2, [[1,0]]
 * 输出: [0,1]
 * 解释: 总共有 2 门课程。要学习课程 1，你需要先完成课程 0。因此，正确的课程顺序为 [0,1] 。
 * 示例 2:
 * <p>
 * 输入: 4, [[1,0],[2,0],[3,1],[3,2]]
 * 输出: [0,1,2,3] or [0,2,1,3]
 * 解释: 总共有 4 门课程。要学习课程 3，你应该先完成课程 1 和课程 2。并且课程 1 和课程 2 都应该排在课程 0 之后。
 *      因此，一个正确的课程顺序是 [0,1,2,3] 。另一个正确的排序是 [0,2,1,3] 。
 * 说明:
 * <p>
 * 输入的先决条件是由边缘列表表示的图形，而不是邻接矩阵。详情请参见图的表示法。
 * 你可以假定输入的先决条件中没有重复的边。
 * 提示:
 * <p>
 * 这个问题相当于查找一个循环是否存在于有向图中。如果存在循环，则不存在拓扑排序，因此不可能选取所有课程进行学习。
 * 通过 DFS 进行拓扑排序 - 一个关于Coursera的精彩视频教程（21分钟），介绍拓扑排序的基本概念。
 * 拓扑排序也可以通过 BFS 完成。
 * <p>
 * <p>
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/course-schedule-ii
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 *
 * 问题总计，寻找入度为 0 的顶点，如果没有则是环
 * 将入度为 0 的顶点从各表中删除，
 * 将找到的入度为0 的顶点入栈
 * 最后出栈即为 答案
 *
 */
public class L_210 {

    public static void main(String[] args) {
//        new L_210().findOrder(1, new int[][]{}); // [0]
//        new L_210().findOrder(2, new int[][]{{1, 0}, {0, 1}}); // 环路
//        new L_210().findOrder(3, new int[][]{{0, 1}, {0, 2}, {1, 2}}); // [2, 1, 0]
//        new L_210().findOrder(3, new int[][]{{1, 0}, {0, 2}, {2, 1}}); // 环路
//        new L_210().findOrder(2, new int[][]{{1, 0}}); // [0, 1]
//        new L_210().findOrder(7, new int[][]{{1,0},{0,3},{0,2},{3,2},{2,5},{4,5},{5,6},{2,4}}); // [6, 5, 4, 2, 3, 0, 1]
//        new L_210().findOrder(3, new int[][]{{1, 0}, {2, 0}, {0, 2}}); // 环路
        new L_210().findOrder(10, new int[][]{{5,6},{0,2},{1,7},{5,9},{1,8},{3,4},{0,6},{0,7},{0,3},{8,9}});

    }

    public int[] findOrder(int numCourses, int[][] prerequisites) {

        if (numCourses <= 1){
            return new int[]{0};
        }

        LinkedList<Integer>[] linkedLists = new LinkedList[numCourses];

        for (int i = 0; i < linkedLists.length; i++) {
            linkedLists[i] = new LinkedList<>();
        }

        // 转为邻接表
        for (int i = 0; i < prerequisites.length; i++) {
            int from = prerequisites[i][1];
            int to = prerequisites[i][0];
            if (linkedLists[from] == null){
                linkedLists[from] = new LinkedList<>();
            }
            if (!linkedLists[from].contains(to)){
                linkedLists[from].add(to);
            }
        }
        // 入度为 0 的顶点存放集合，从后往前存
        int[] sqe = new int[numCourses];
        int step = numCourses-1;

        Set<Integer> points = new HashSet<>();
        while (true){
            int len = linkedLists.length;
            points.clear();

            // 寻找入度为 0 的顶点
            for (int i = 0; i < len; i++) {
                if (linkedLists[i] != null && linkedLists[i].size() == 0){
                    points.add(i);
                    linkedLists[i] = null;
                }
            }

            if (points.size() == 0){
                // 如果入度为 0 的顶点集为空，且邻接表不为空，说明有环
                for (int i = 0; i < len; i++) {
                    if (linkedLists[i] != null && linkedLists[i].size() != 0){
                        System.out.println("存在环");
                        return new int[0];
                    }
                }
                break;
            }else {
                // 否则就添加到倒序列表中
                for (Integer point : points) {
                    for (int i = 0; i < len; i++) {
                        if (linkedLists[i] != null && linkedLists[i].size() != 0){
                            int index = linkedLists[i].indexOf(point);
                            if (index > -1 ){
                                linkedLists[i].remove(index);
                            }
                        }
                    }
                    sqe[step--] = point;
                }
            }
        }

        return sqe;
    }




}
