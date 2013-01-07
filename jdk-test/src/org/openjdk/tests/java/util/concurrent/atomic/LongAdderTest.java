package org.openjdk.tests.java.util.concurrent.atomic;

import java.util.concurrent.atomic.LongAdder;

/**
 * Created with IntelliJ IDEA.
 * User: peter
 * Date: 1/6/13
 * Time: 4:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class LongAdderTest
{
    static class Worker extends Thread
    {
        private final LongAdder adder;
        private final int loops;

        Worker(LongAdder adder, int loops)
        {
            this.adder = adder;
            this.loops = loops;
        }

        @Override
        public void run()
        {
            for (int i = 0; i < loops; i++)
                adder.add(1L);
        }
    }

    static Object[] test(int threads, int loops)
    {
        LongAdder adder = new LongAdder();
        Worker[] workers = new Worker[threads];
        for (int i = 0; i < threads; i++)
            workers[i] = new Worker(adder, loops);
        long t0 = System.nanoTime();
        for (Worker worker : workers) worker.start();
        try
        {
            for (Worker worker : workers) worker.join();
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        return new Object[] { System.nanoTime() - t0, adder.getCellsLength() };
    }

    static void testX(int threads, int loops)
    {
        System.out.printf("%4d threads x %9d loops :", threads, loops);
        for (int i = 0; i < 7; i++)
            System.out.printf(" %,15d ns (%4d cells)", test(threads, loops));
        System.out.println();
    }
    public static void main(String[] args)
    {
        testX(1, 100_000_000);
        testX(2, 100_000_000);
        testX(4, 100_000_000);
        testX(8, 100_000_000);
        testX(16, 100_000_000);
        testX(32, 100_000_000);
        testX(64, 100_000_000);
    }
}
