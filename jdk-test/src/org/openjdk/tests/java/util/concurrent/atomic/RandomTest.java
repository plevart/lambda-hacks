package org.openjdk.tests.java.util.concurrent.atomic;

import java.util.Random;
import java.util.concurrent.MultiThreadedRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 */
public class RandomTest
{
    static class RandomWorker extends Thread
    {
        private final Random rnd;
        private final int loops;

        RandomWorker(Random rnd, int loops)
        {
            this.rnd = rnd;
            this.loops = loops;
        }

        @Override
        public void run()
        {
            for (int i = 0; i < loops; i++)
                rnd.nextInt();
        }
    }

    static class TLRandomWorker extends Thread
    {
        private final int loops;

        TLRandomWorker(int loops)
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

    static Object[] test(int threads, int loops, Random rnd)
    {
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++)
            workers[i] = rnd == null ? new TLRandomWorker(loops) : new RandomWorker(rnd, loops);
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
        return new Object[] { System.nanoTime() - t0 };
    }

    static void testX(int threads, int loops, Random rnd)
    {
        System.out.printf("%20s ", rnd == null ? "ThreadLocalRandom" : rnd.getClass().getSimpleName());
        System.out.printf("%4d threads x %9d loops :", threads, loops);
        for (int i = 0; i < 7; i++)
            System.out.printf(" %,15d ns", test(threads, loops, rnd));
        System.out.println();
    }

    public static void main(String[] args)
    {
        Random rnd = new MultiThreadedRandom();
        testX(1, 100_000_000, null);
        testX(1, 100_000_000, rnd);
        testX(2, 100_000_000, null);
        testX(2, 100_000_000, rnd);
        testX(4, 100_000_000, null);
        testX(4, 100_000_000, rnd);
        testX(6, 100_000_000, null);
        testX(6, 100_000_000, rnd);
    }
}
