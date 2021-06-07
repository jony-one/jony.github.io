package samle;

public class L_64 {
    public static void main(String[] args) {
        int a = new L_64().sumNums(2);
        System.out.println(a);
    }
    public int sumNums(int n) {
        int sum = n;
        boolean al = (n != 0) && (sum = sum + sumNums(n-1))>=0;
        return sum;
    }
}
