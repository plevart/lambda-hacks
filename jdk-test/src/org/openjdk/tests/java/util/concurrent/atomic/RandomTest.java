package org.openjdk.tests.java.util.concurrent.atomic;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 */
public class RandomTest
{
    static abstract class Worker extends Thread
    {
        final int loops;
        long time;

        Worker(int loops)
        {
            this.loops = loops;
        }

        @Override
        public void run()
        {
            long t0 = System.nanoTime();
            doWork();
            time = System.nanoTime() - t0;
        }

        protected abstract void doWork();
    }

    static class TLRCurrentNextIntWorker extends Worker
    {
        TLRCurrentNextIntWorker(int loops)
        {
            super(loops);    //To change body of overridden methods use File | Settings | File Templates.
        }

        int sum;

        @Override
        protected void doWork()
        {
            int sum = 0;
            for (int i = 0; i < loops; i++)
                sum += ThreadLocalRandom.current().nextInt();
            this.sum = sum;
        }
    }

    static class TLRNextIntWorker extends Worker
    {
        TLRNextIntWorker(int loops)
        {
            super(loops);
        }

        int sum;

        @Override
        protected void doWork()
        {
            Random rnd = ThreadLocalRandom.current();
            int sum = 0;
            for (int i = 0; i < loops; i++)
                sum += rnd.nextInt();
            this.sum = sum;
        }
    }

    static Object[] test(Function<Integer, Worker> workerFactory, int threads, int loops)
    {
        Worker[] workers = new Worker[threads];
        for (int i = 0; i < threads; i++)
            workers[i] = workerFactory.apply(loops);
        for (Worker worker : workers) worker.start();
        long tSum = 0L;
        try
        {
            for (Worker worker : workers)  {
                worker.join();
                tSum += worker.time;
            }
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }

        double tAvg = (double) tSum / threads;
        double vSum = 0L;
        for (Worker worker : workers) {
            vSum += ((double) worker.time - tAvg) * ((double) worker.time - tAvg);
        }
        double v = vSum / threads;
        double σ = Math.sqrt(v);
        return new Object[]{tAvg / loops, σ / loops};
    }

    static void testX(int threads, int loops)
    {
        System.out.printf("\n%3d threads, %,12d ops\n\n", threads, loops);

        // warm-up
        for (int i = 0; i < 5; i++)
            test(TLRCurrentNextIntWorker::new, threads, loops);
        for (int i = 0; i < 5; i++)
            test(TLRNextIntWorker::new, threads, loops);

        System.out.printf("TLR.current().nextInt()");
        for (int i = 0; i < 5; i++)
            System.out.printf(" | %,4.2f +- %,4.2f ns/op", test(TLRCurrentNextIntWorker::new, threads, loops));
        System.out.println();

        System.out.printf("          tlr.nextInt()");
        for (int i = 0; i < 5; i++)
            System.out.printf(" | %,4.2f +- %,4.2f ns/op", test(TLRNextIntWorker::new, threads, loops));
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
