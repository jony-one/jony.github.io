package samle;

/**
 * 请实现整数数字的乘法、减法和除法运算，运算结果均为整数数字，程序中只允许使用加法运算符和逻辑运算符，允许程序中出现正负常数，不允许使用位运算。
 * <p>
 * 你的实现应该支持如下操作：
 * <p>
 * Operations() 构造函数
 * minus(a, b) 减法，返回a - b
 * multiply(a, b) 乘法，返回a * b
 * divide(a, b) 除法，返回a / b
 * 示例：
 * <p>
 * Operations operations = new Operations();
 * operations.minus(1, 2); //返回-1
 * operations.multiply(3, 4); //返回12
 * operations.divide(5, -2); //返回-2
 * 提示：
 * <p>
 * 你可以假设函数输入一定是有效的，例如不会出现除法分母为0的情况
 * 单个用例的函数调用次数不会超过1000次
 * <p>
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/operations-lcci
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_M_16_09 {

    public static void main(String[] args) {
//        System.out.println(new L_M_16_09().minus(0, -2147483647));
//        System.out.println(new L_M_16_09().minus(-1, -2147483647));
////
        System.out.println(new L_M_16_09().multiply(-1, -2147483647));
        System.out.println(new L_M_16_09().multiply(-100, 21474836));
//
//        System.out.println(new L_M_16_09().divide(2147483647, -1) + ",-2147483647");
//        System.out.println(new L_M_16_09().divide(-2147483648, 1) + ",-2147483648");
//        System.out.println(new L_M_16_09().divide(1,-109883727));
//        System.out.println(new L_M_16_09().divide(-2147483648,1));
        System.out.println(new L_M_16_09().multiply(-2,-5));
//        System.out.println(new L_M_16_09().divide(-13969484,-5));


    }


    public int minus(int a, int b) { // 减法
        return a + (~b + 1);
    }

//    public int multiply(int a, int b) {
//        return Math.multiplyExact(a, b);
//    }

    public int multiply(int a, int b) { // 乘法
        int c = 0;
        boolean z = isZF(a, b);
        a = toF(a);
        b = toF(b);

        int len = Math.min(a, b);
        int base = Math.max(a, b);


        while (len < 0) {
            int step = -1;
            int t = base;
            while (step < len) {
                t+=t;
                step=minus(step,step);
            }
            c+=t;
            len=minus(len,step);
        }

        if (z) {
            return minus(0, c);
        }
        return c;
    }

    public int divide(int a, int b) { // 除法
        int result = 0;
        boolean z = isZF(a, b);
        a = toF(a);
        b = toF(b);

        while (a <= b) {
            int d = b;
            int step = 0;
            while (a <= d) {
                if (step == 0) {
                    step = -1;
                } else {
                    step += step;
                }
                int c = -2147483648 + minus(0, d);

                if (!(a <= d + d) || c > d) {
                    break;
                }
                d += d;

            }
            result += step;
            a += minus(0, d);
        }

        if (z) {
            return minus(0, result);
        }
        return result;
    }


    private int toF(int a) {
        if (a > 0) {
            return minus(0, a);
        }
        return a;
    }

    private boolean isZF(int a, int b) {
        return (a > 0 && b > 0) || (a < 0 && b < 0);
    }
}
