/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

/**
 * @author peter
 */
public class ModY {

    public interface BinOp {
        int apply(int a, int b);
    }

    public static int min(int a, int b) {
        return a <= b ? a : b;
    }
}
