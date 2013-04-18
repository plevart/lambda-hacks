/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.util.stream.StreamBuilder;
import java.util.stream.Streams;

/**
 * @author peter
 */
public class StreamBuilderTest {

    static long test(boolean parallel) {
        StreamBuilder.OfInt builder = Streams.intBuilder();
        for (int i = 0; i < 10000000; i++) {
            builder.accept(i);
        }

        try {
            System.gc();
            Thread.sleep(500L);
            System.gc();
            Thread.sleep(500L);
            System.gc();
            Thread.sleep(500L);
        } catch (InterruptedException e) {}

        long t0 = System.nanoTime();
        double pi = (parallel ? builder.build().parallel() : builder.build().sequential())
                        .mapToDouble(i ->
                            (i & 1) == 0
                            ? 4d / ((2d * i + 2d) * (2d * i + 3d) * (2d * i + 4d))
                            : -4d / ((2d * i + 2d) * (2d * i + 3d) * (2d * i + 4d))
                        )
                        .sum() + 3d;
        long t = System.nanoTime() - t0;
        System.out.println("  pi=" + pi + " in " + t + " ns");
        return t;
    }

    static void testN(int n, boolean parallel) {
        System.out.println("  warm-up");
        for (int i = 0; i < 5; i++) test(parallel);
        System.out.println("  measure");
        long tSum = 0;
        for (int i = 0; i < n; i++) tSum += test(parallel);
        System.out.println("  average: " + ((double) tSum / n) + " ns");
    }

    static void testNSeqPar() {
        System.out.println("Sequential:");
        testN(10, false);
        System.out.println("Parallel:");
        testN(10, true);
    }

    static void testParLongop() {
        StreamBuilder.OfInt builder = Streams.intBuilder();
        for (int i = 0; i < 5000000; i++) {
            builder.accept(i);
        }

        long t0 = System.nanoTime();
        long sum = builder.build().parallel()
            .map(i -> {
                return i;
            })
            .sum();
        long t = System.nanoTime() - t0;
        System.out.println("sum=" + sum + " in " + t + " ns");
    }

    public static void main(String[] args) {
        testParLongop();
        testParLongop();
        testParLongop();
        testParLongop();
        testParLongop();
        testParLongop();
        testParLongop();
        testParLongop();
        testParLongop();
        testParLongop();
    }
}
