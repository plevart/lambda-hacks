/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class Test {

    static class TestTask implements Callable<String> {
        final String resultFormat;
        final long seed, gamma;
        final String[] cmd;

        TestTask(String resultFormat, long seed, long gamma, String... cmd) {
            this.resultFormat = resultFormat;
            this.seed = seed;
            this.gamma = gamma;
            this.cmd = cmd;
        }

        @Override
        public String call() throws Exception {
            SplittableRandom sr = new SplittableRandom(seed, gamma, null);

            File resultFile = File.createTempFile("test_out", ".log");

            Process process = new ProcessBuilder()
                .command(cmd)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.to(resultFile))
                .start();

            OutputStream out = process.getOutputStream();
            byte[] buf = new byte[20000 << 2]; // multiple of 4
            String result = "no result";
            try {
                // pipe to dieharder process until it's finished
                while (process.isAlive()) {
                    for (int i = 0; i < buf.length; ) {
                        int x = sr.nextInt();
                        buf[i++] = (byte) (x & 0xFF); x >>>= 8;
                        buf[i++] = (byte) (x & 0xFF); x >>>= 8;
                        buf[i++] = (byte) (x & 0xFF); x >>>= 8;
                        buf[i++] = (byte) x;
                    }
                    try {
                        out.write(buf);
                    } catch (IOException e) {
                        // when dieharder exits during write,
                        // we get broken pipe or stream closed exception...
                        if (e.getMessage().equals("Broken pipe") ||
                            e.getMessage().equals("Stream closed")) {
                            break;
                        } else { // re-throw
                            throw e;
                        }
                    }
                }

                try (
                    BufferedReader rin =
                        new BufferedReader(new InputStreamReader(new FileInputStream(resultFile), "ASCII"))
                ) {
                    String line;
                    // last line is the result line...
                    while ((line = rin.readLine()) != null) {
                        result = line;
                    }
                } finally {
                    resultFile.delete();
                }
            } finally {
                try {
                    out.close();
                } catch (IOException ignore) {}
            }

            return String.format(resultFormat, result);
        }

        private static int mix32(long z) {
            z ^= (z >>> 33);
            z *= 0xc4ceb9fe1a85ec53L;
            return (int) (z >>> 32);
        }

        private static int mix32alt(long z) {
            int h = (int) (z >>> 32) ^ (int) z;
            h ^= h >> 16;
            h *= 0x85ebca6b;
            h ^= h >> 13;
            h *= 0xc2b2ae35;
            return h ^ (h >> 16);
        }
    }

    static class TestRunner {
        final int cpus = Runtime.getRuntime().availableProcessors();
        final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>(cpus * 4);
        final ExecutorService exe = new ThreadPoolExecutor(
            cpus, cpus,
            0L, TimeUnit.MILLISECONDS,
            taskQueue
        );
        final Queue<Future<String>> resultQueue = new LinkedList<>();

        void run(Callable<String> testTask) throws Exception {
            resultQueue.add(exe.submit(testTask));
            printFinished();
        }

        void header(String header) throws Exception {
            FutureTask<String> headerTask = new FutureTask<>(() -> header);
            headerTask.run();
            resultQueue.add(headerTask);
            printFinished();
        }

        // see if there are any results ready and print them out
        private void printFinished() throws Exception {
            while (resultQueue.peek() != null && resultQueue.peek().isDone() ||
                   // wait for next result if task queue is full
                   taskQueue.remainingCapacity() < 1) {
                System.out.println(resultQueue.remove().get());
            }
        }

        // flush remaining results and shutdown thread pool
        void finishUp() throws Exception {
            try {
                Future<String> result;
                while ((result = resultQueue.poll()) != null) {
                    System.out.println(result.get());
                }
            } finally {
                exe.shutdown();
            }
        }
    }

    private static String toBinaryString64(long value) {
        String bs = Long.toBinaryString(value);
        return "0000000000000000000000000000000000000000000000000000000000000000"
                   .substring(bs.length()) + bs;
    }

    public static void main(String[] args) throws Exception {
        Random rnd = new Random();
        TestRunner runner = new TestRunner();

        for (int testId : new int[]{
            0, 1, 2, 3, 4, 8, 9, 10,
            /* 11, 12, */ // commented-out tests don't finish in reasonable time for some inputs
            13, 15, 16,
            /* 17, */
            100, 101, 102, 200, 201, 202,
            203, 204, 205, 206, 207, 208, 209
        }) {
            runner.header(String.format("\nTest id=%d\n", testId));
            for (int ones = 0; ones <= 64; ones++) {
                long seed = 0L;
                long gamma = 0L;
                for (int j = 0; j < ones; j++) {
                    for (; ; ) {
                        long mask = 1L << rnd.nextInt(64);
                        if ((gamma & mask) == 0L) {
                            gamma |= mask;
                            break;
                        }
                    }
                }
                runner.run(new TestTask(
                    String.format("%2d ones, seed=%d, gamma=%s - %%s",
                        ones, seed, toBinaryString64(gamma)),
                    seed, gamma,
                    "dieharder", "-g", "200", "-d", String.valueOf(testId)
                ));
            }
        }
        runner.finishUp();
    }
}
