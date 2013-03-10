package test;

/**
 */
public class X {
    interface II<T> {
        Object foo(T x);
    }

    interface JJ<R extends Number> extends II<R> {}

    static class CC {
        int a;

        CC(int a) {
            this.a = a;
        }

        String impl(int i) { return a + " + " + i + " = " + (a+i); }
    }

    public static void main(String[] args) {
        JJ<Integer> iii = (new CC(1))::impl;
        JJ<Integer> jjj = (new CC(2))::impl;
        System.out.println("iii: " + iii + ": " + iii.foo(10));
        System.out.println("jjj: " + jjj + ": " + jjj.foo(10));
    }

}
