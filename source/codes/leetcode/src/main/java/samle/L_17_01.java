package samle;

public class L_17_01 {

    public static void main(String[] args) {
        System.out.println(new L_17_01().add(1,2));
    }

    public int add(int a, int b) {
        while (b != 0){
            int carry = (a & b) << 1;
            a ^= b;
            b = carry;
        }
        return a;
    }
}
