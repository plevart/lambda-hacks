package org.openjdk.tests.java.util.concurrent.atomic;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 */
public class RandomTest
{

    static class TLRCurrentNextIntWorker extends Thread
    {
        private final int loops;

        TLRCurrentNextIntWorker(int loops)
        {
            this.loops = loops;
        }

        @Override
        public void run()
        {
            for (int i = 0; i < loops; i++)
                ThreadLocalRandom.current().nextInt();
        }
    }

    static class TLRNextIntWorker extends Thread
    {
        private final int loops;

        TLRNextIntWorker(int loops)
        {
            this.loops = loops;
        }

        @Override
        public void run()
        {
            Random rnd = ThreadLocalRandom.current();
            for (int i = 0; i < loops; i++)
                rnd.nextInt();
        }
    }

    static Object[] test(Function<Integer, Thread> workerFactory, int threads, int loops)
    {
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++)
            workers[i] = workerFactory.apply(loops);
        long t0 = System.nanoTime();
        for (Thread worker : workers) worker.start();
        try
        {
            for (Thread worker : workers) worker.join();
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        return new Object[]{System.nanoTime() - t0};
    }

    static void testX(int threads, int loops)
    {
        System.out.printf("\n%4d threads x %9d loops...\n\n", threads, loops);

        // warm-up
        for (int i = 0; i < 5; i++)
            test(TLRCurrentNextIntWorker::new, threads, loops);
        for (int i = 0; i < 5; i++)
            test(TLRNextIntWorker::new, threads, loops);

        System.out.printf("TLR.current().nextInt(): ");
        for (int i = 0; i < 7; i++)
            System.out.printf(" %,15d ns", test(TLRCurrentNextIntWorker::new, threads, loops));
        System.out.println();

        System.out.printf("          tlr.nextInt(): ");
        for (int i = 0; i < 7; i++)
            System.out.printf(" %,15d ns", test(TLRNextIntWorker::new, threads, loops));
        System.out.println();
    }

    public static void main(String[] args)
    {
        testX(1, 100_000_000);
        testX(2, 100_000_000);
        testX(4, 100_000_000);
        testX(6, 100_000_000);
    }
}
