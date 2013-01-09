package org.openjdk.tests.java.util.function;

import java.util.function.Supplier;
import java.util.function.Suppliers;

/**
 */
public class CachedSupplierTest {

    static class Worker extends Thread {
        private final Supplier<String> supplier;
        private final int loops;

        Worker(Supplier<String> supplier, int loops) {
            this.supplier = supplier;
            this.loops = loops;
        }

        @Override
        public void run() {
            for (int i = 0; i < loops; i++)
            {
                supplier.get();
            }
        }
    }

    static Object[] test(int threads, int loops) {
        Supplier<String> supplier = Suppliers.cached(true, true, true, () -> "x");
        Worker[] workers = new Worker[threads];
        for (int i = 0; i < threads; i++)
            workers[i] = new Worker(supplier, loops);
        long t0 = System.nanoTime();
        for (Worker worker : workers) worker.start();
        try {
            for (Worker worker : workers) {
                worker.join();
            }

        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return new Object[]{System.nanoTime() - t0};
    }

    static void testX(int threads, int loops) {
        System.out.printf("%4d threads x %,12d loops :", threads, loops);
        for (int i = 0; i < 5; i++)
            System.out.printf(" %,15d ns", test(threads, loops));
        System.out.println();
    }

    public static void main(String[] args) {
        testX(1, 100_000_000);
        testX(2, 100_000_000);
        testX(4, 100_000_000);
        testX(8, 100_000_000);
        testX(16, 100_000_000);
        testX(32, 100_000_000);
        testX(64, 100_000_000);
    }
}
