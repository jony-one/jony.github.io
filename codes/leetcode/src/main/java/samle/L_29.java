package samle;

/**
 * 给定两个整数，被除数 dividend 和除数 divisor。将两数相除，要求不使用乘法、除法和 mod 运算符。
 *
 * 返回被除数 dividend 除以除数 divisor 得到的商。
 *
 * 整数除法的结果应当截去（truncate）其小数部分，例如：truncate(8.345) = 8 以及 truncate(-2.7335) = -2
 *
 *  
 *
 * 示例 1:
 *
 * 输入: dividend = 10, divisor = 3
 * 输出: 3
 * 解释: 10/3 = truncate(3.33333..) = truncate(3) = 3
 * 示例 2:
 *
 * 输入: dividend = 7, divisor = -3
 * 输出: -2
 * 解释: 7/-3 = truncate(-2.33333..) = -2
 *  
 *
 * 提示：
 *
 * 被除数和除数均为 32 位有符号整数。
 * 除数不为 0。
 * 假设我们的环境只能存储 32 位有符号整数，其数值范围是 [−231,  231 − 1]。本题中，如果除法结果溢出，则返回 231 − 1。
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/divide-two-integers
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 */
public class L_29 {

    public static void main(String[] args) {
//        -2147483648
//                -1
//        System.out.println(new L_29().divide(-2147483648,-1));
        System.out.println(new L_29().divide(10,3));
        System.out.println(new L_29().divide(-10,-3));
        System.out.println(new L_29().divide(7,-3));
        System.out.println(new L_29().divide(0,-3));
        System.out.println(new L_29().divide(-2147483648,-1));
    }
    public int divide(long dividend, long divisor) {
        boolean zs = false;
        if ((dividend >0 && divisor >0) || (dividend <0 && divisor <0)){
            zs = true;
        }
        if (divisor == 1 || divisor == -1){

            if (zs){
                if (Math.abs(dividend) > 2147483647){
                    return 2147483647;
                }
                return (int)Math.abs(dividend);
            }else {
                return -(int)Math.abs(dividend);
            }
        }
        dividend = Math.abs(dividend);
        divisor = Math.abs(divisor);
        long sum = 0;
        for (long i = divisor; i <= dividend; i+=divisor) {
            sum += 1;
        }
        if (zs){
            if (sum > 2147483647){
                return 2147483647;
            }
            return (int)sum;
        }
        return (int)-sum;
    }
//    public int divide(int dividend, int divisor) {
//        boolean z = false;
//        if ((dividend >= 0 && divisor >= 0) || (dividend <= 0 && divisor <= 0)){
//            z = true;
//        }
//        if (dividend > 0){
//            dividend = 0-dividend;
//        }
//        if (divisor > 0){
//            divisor = 0-divisor;
//        }
//        int base = 1;
//        long _divisor = divisor;
//        long _dividend = dividend;
//        List<Long> list = new ArrayList<>();
//        while (_dividend < _divisor  ){
//            long b = Math.abs(_divisor);
//            boolean isBreak = false;
//            for (int i = 0; i < b; i++) {
//                if (_dividend <= _divisor){
//                    _divisor += _divisor;
//                    isBreak = true;
//                    break;
//                }
//            }
//            if (!isBreak){
//                base++;
//                list.add(_divisor);
//            }
//        }
//        System.out.println(base);
//
//
////        if (z){
////            if (s < (-2147483647)){
////                s++;
////            }
////            return 0-s;
////        }
//
//        return 0;
//    }

    /**
     * 普通解法
     * @param dividend
     * @param divisor
     * @return
     */
//    public int divide(int dividend, int divisor) {
//        boolean z = false;
//        if ((dividend >= 0 && divisor >= 0) || (dividend <= 0 && divisor <= 0)){
//            z = true;
//        }
//        if (dividend > 0){
//            dividend = 0-dividend;
//        }
//        if (divisor > 0){
//            divisor = 0-divisor;
//        }
//        int s = 0;
//        while (dividend <= divisor){
//            s--;
//            dividend = dividend - divisor;
//            if (!(dividend <= divisor)){
//                break;
//            }
//        }
//
//        if (z){
//            if (s < (-2147483647)){
//                s++;
//            }
//            return 0-s;
//        }
//
//        return s;
//    }

}
