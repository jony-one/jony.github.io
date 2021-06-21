package samle;

public class L_258 {
    public static void main(String[] args) {

    }

    public int addDigits(int num) {
        int s = 0;
        if (num < 10){
            return num;
        }
        while (num > 0){
            s += num % 10;
            num /= 10;
        }
        return addDigits(s);
    }
}
