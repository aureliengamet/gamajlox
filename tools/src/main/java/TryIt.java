import java.util.function.IntSupplier;

public class TryIt {
    private int myVar = 1;

    private int myFun() {
        return myVar++;
    }

    public static void main(String[] args) {
        TryIt it = new TryIt();
        IntSupplier myOp = it::myFun;
        System.out.println(myOp.getAsInt());
        System.out.println(myOp.getAsInt());
    }
}
