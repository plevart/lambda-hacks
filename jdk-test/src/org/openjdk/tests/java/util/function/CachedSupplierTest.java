package org.openjdk.tests.java.util.function;

import java.util.Random;
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

        Throwable throwable;
        int sum;
        long t;

        @Override
        public void run() {
            try {
                long t0 = System.nanoTime();
                for (int i = 0; i < loops; i++) {
                    sum += supplier.get().length();
                }
                t = System.nanoTime() - t0;
            }
            catch (Throwable e) {
                this.throwable = e;
            }
        }
    }

    static long[] test(int threads, int loops, Supplier<String> supplier) throws Throwable {
//        supplier = Suppliers.cached(false, true, true, () -> new String("x"));

        Worker[] workers = new Worker[threads];
        for (int i = 0; i < threads; i++)
            workers[i] = new Worker(supplier, loops);
        for (Worker worker : workers) {
            worker.start();
            worker.join();
        }
        try {
            long[] times = new long[workers.length];
            for (int i = 0; i < workers.length; i++) {
                Worker worker = workers[i];
                worker.join();
                if (worker.throwable != null)
                    throw worker.throwable;
                if (worker.sum != loops)
                    throw new IllegalStateException("sum " + worker.sum + " != loops " + loops);
                times[i] = worker.t;
            }
            return times;
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static void testX(int threads, int loops, Supplier<String> supplier) throws Throwable {
//        supplier = Suppliers.cached(false, true, true, () -> new String("x"));

        System.out.printf("\n%4d threads x %,12d loops\n", threads, loops);
        for (int i = 0; i < 5; i++) {
            for (long t : test(threads, loops, supplier))
                System.out.printf(" %,15d ns", t);
            System.out.println();
        }
    }

    public static void main(String[] args) throws Throwable {
        Supplier<String> supplier = Suppliers.cached(false, true, true, () -> new String("x"));
        Random rnd = new Random();
        testX(1, rnd.nextInt(1000_000_000) + 1000_000_000, supplier);
        testX(2, rnd.nextInt(1000_000_000) + 1000_000_000, supplier);
        testX(4, rnd.nextInt(1000_000_000) + 1000_000_000, supplier);
        testX(6, rnd.nextInt(1000_000_000) + 1000_000_000, supplier);
        testX(8, rnd.nextInt(1000_000_000) + 1000_000_000, supplier);
    }
}
