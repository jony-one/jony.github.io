package samle;

import java.util.TreeSet;

/**
 * 给定一个由 4 位数字组成的数组，返回可以设置的符合 24 小时制的最大时间。
 * <p>
 * 最小的 24 小时制时间是 00:00，而最大的是 23:59。从 00:00 （午夜）开始算起，过得越久，时间越大。
 * <p>
 * 以长度为 5 的字符串返回答案。如果不能确定有效时间，则返回空字符串。
 * <p>
 *  
 * <p>
 * 示例 1：
 * <p>
 * 输入：[1,2,3,4]
 * 输出："23:41"
 * 示例 2：
 * <p>
 * 输入：[5,5,5,5]
 * 输出：""
 *  
 * <p>
 * 提示：
 * <p>
 * A.length == 4
 * 0 <= A[i] <= 9
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/largest-time-for-given-digits
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_949 {

    public static void main(String[] args) {
        System.out.println(new L_949().largestTimeFromDigits2(new int[]{1,2,3,4}));
//        System.out.println(new L_949().largestTimeFromDigits2(new int[]{5,5,5,5}));
//        System.out.println(new L_949().largestTimeFromDigits2(new int[]{0,0,0,0}));
//        System.out.println(new L_949().largestTimeFromDigits2(new int[]{0, 2, 6, 6}));
    }


    public String largestTimeFromDigits2(int[] A) {

        int max_hour = -1;
        int max_sec = -1;
        String a = "";
        for (int h1 = 0; h1 < A.length; h1++) {

            for (int min = 0; min < A.length; min++) {
                if (min == h1) {
                    continue;
                }
                for (int sec1 = 0; sec1 < A.length; sec1++) {
                    if (sec1 == h1 || sec1 == min) {
                        continue;
                    }
                    for (int sec = 0; sec < A.length; sec++) {
                        if (sec == h1 || sec == min || sec == sec1) {
                            continue;
                        }
                        StringBuilder builder = new StringBuilder(String.valueOf(A[h1]));
                        builder.append(A[min]);
                        builder.append(A[sec1]);
                        builder.append(A[sec]);
                        String time = builder.toString();
                        if (time.length() == 4) {
                            String hour = time.substring(0, 2);
                            String cur_sec = time.substring(2);
                            if (Integer.parseInt(hour) >= 0 && Integer.parseInt(hour) <= 23 && Integer.parseInt(cur_sec) >= 0 && Integer.parseInt(cur_sec) <= 59){
                                if (Integer.parseInt(hour) > max_hour ){
                                    max_hour = Integer.parseInt(hour);
                                    max_sec = Integer.parseInt(cur_sec);
                                    a = hour + ":"+ cur_sec;
                                }else if ( Integer.parseInt(hour) == max_hour && Integer.parseInt(cur_sec) > max_sec){
                                    a = hour + ":"+ cur_sec;
                                }

                            }
                        }
                    }
                }
            }
        }


        return a;
    }

    public String largestTimeFromDigits1(int[] A) {
        int max_hour = -1;
        int hi = -1, hj = -1;
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A.length; j++) {
                if (i == j) {
                    continue;
                }
                int max_c = A[i] * 10 + A[j];
                if (max_c >= 0 && max_c <= 23) {
                    max_hour = max_c;
                    hi = i;
                    hj = j;
                }
            }
        }
        A[hi] = -1;
        A[hj] = -1;

        if (max_hour == -1) {
            return "";
        }


        int min_sec = -1;
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A.length; j++) {
                if (i == j || A[i] == -1 || A[j] == -1) {
                    continue;
                }
                int max_min_sec_c = A[i] * 10 + A[j];
                if (max_min_sec_c >= 0 && max_min_sec_c <= 59) {
                    min_sec = max_min_sec_c;
                }
            }
        }
        if (min_sec < 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (max_hour < 10) {
            builder.append("0").append(max_hour);
        } else {
            builder.append(max_hour);
        }

        builder.append(":");
        if (min_sec < 10) {
            builder.append("0").append(min_sec);
        } else {
            builder.append(min_sec);
        }

        return builder.toString();
    }

    public String largestTimeFromDigits(int[] A) {
        StringBuilder time = new StringBuilder();
        hour(time, A);
        if (time.length() == 0) {
            return "";
        }

        hour2(time, A);

        if (time.length() != 2) {
            return "";
        }
        min(time, A);

        if (time.length() != 3) {
            return "";
        }

        sec(time, A);

        if (time.length() != 4) {
            return "";
        }
        String str = time.toString();
        return str.substring(0, 2) + ":" + str.substring(2);
    }

    public void hour(StringBuilder time, int[] A) {
        TreeSet<Integer> l = new TreeSet<Integer>();
        for (int i = 0; i < A.length; i++) {
            if (A[i] >= 0 && A[i] <= 2) {
                l.add(A[i]);
            }
        }
        if (l.size() == 0) {
            return;
        }
        for (int i = 0; i < A.length; i++) {
            if (A[i] == l.last()) {
                A[i] = -1;
                break;
            }
        }
        time.append(l.last());
    }

    public void hour2(StringBuilder time, int[] A) {
        TreeSet<Integer> l = new TreeSet<Integer>();
        String h1 = time.toString();
        for (int i = 0; i < A.length; i++) {
            if (h1.equals("2")) {
                if (A[i] >= 0 && A[i] <= 3) {
                    l.add(A[i]);
                }
            } else {
                l.add(A[i]);
            }
        }
        if (l.size() == 0) {
            return;
        }
        for (int i = 0; i < A.length; i++) {
            if (A[i] == l.last()) {
                A[i] = -1;
                break;
            }
        }
        time.append(l.last());
    }

    public void min(StringBuilder time, int[] A) {
        TreeSet<Integer> l = new TreeSet<Integer>();
        for (int i = 0; i < A.length; i++) {
            if (A[i] >= 0 && A[i] <= 5) {
                l.add(A[i]);
            }
        }
        if (l.size() == 0) {
            return;
        }
        for (int i = 0; i < A.length; i++) {
            if (A[i] == l.last()) {
                A[i] = -1;
                break;
            }
        }
        time.append(l.last());
    }


    public void sec(StringBuilder time, int[] A) {
        TreeSet<Integer> l = new TreeSet<Integer>();
        for (int i = 0; i < A.length; i++) {
            l.add(A[i]);
        }
        if (l.size() == 0) {
            return;
        }
        for (int i = 0; i < A.length; i++) {
            if (A[i] == l.last()) {
                A[i] = -1;
                break;
            }
        }
        time.append(l.last());
    }
}
