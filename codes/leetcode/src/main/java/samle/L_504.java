package samle;

public class L_504 {
    public static void main(String[] args) {
        System.out.println(new L_504().convertToBase7(11000000));
    }

    public String convertToBase7(int num) {
        long i = 1;
        long sum = 0;
        while (num != 0){
            int s = num % 7;
            sum += s * i;
            i *= 10;
            num /= 7;
        }
        return String.valueOf(sum);
    }
}
