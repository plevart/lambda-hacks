package sr;

import si.pele.dieharder.DieharderTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * @author peter.levart@gmail.com
 */
public class Test {

    static final int[] testIds = { 0, 1, 2 };
    static final int[] testIds2 = {
        // dieharder tests to run
        0, 1, 2, 3, 4, 8, 9, 10, 11, 12, 13, 15, 16,
        /* 17, */ // 17 takes very long time to complete
        100, 101, 102,
        200, 201, 202, 203, 204, 205, 206, 207, 208, 209
    };

    public static void main(String[] args) throws Exception {


        System.out.println("\nSplittableRandom.nextInt()\n");
        doTests(
            (sr, buf) -> {
                for (int i = 0; i < buf.length; ) {
                    int x = sr.nextInt();
                    buf[i++] = (byte) (x & 0xFF);
                    buf[i++] = (byte) ((x >>>= 8) & 0xFF);
                    buf[i++] = (byte) ((x >>>= 8) & 0xFF);
                    buf[i++] = (byte) (x >>> 8);
                }
            },
            testIds
        );

        System.out.println("\nSplittableRandom.nextIntAlt1()\n");
        doTests(
            (sr, buf) -> {
                for (int i = 0; i < buf.length; ) {
                    int x = sr.nextIntAlt1();
                    buf[i++] = (byte) (x & 0xFF);
                    buf[i++] = (byte) ((x >>>= 8) & 0xFF);
                    buf[i++] = (byte) ((x >>>= 8) & 0xFF);
                    buf[i++] = (byte) (x >>> 8);
                }
            },
            testIds
        );

        System.out.println("\nSplittableRandom.nextIntAlt2()\n");
        doTests(
            (sr, buf) -> {
                for (int i = 0; i < buf.length; ) {
                    int x = sr.nextIntAlt2();
                    buf[i++] = (byte) (x & 0xFF);
                    buf[i++] = (byte) ((x >>>= 8) & 0xFF);
                    buf[i++] = (byte) ((x >>>= 8) & 0xFF);
                    buf[i++] = (byte) (x >>> 8);
                }
            },
            testIds
        );
    }

    static void doTests(BiConsumer<SplittableRandom, byte[]> bufferFiller, int... testIds) throws Exception {

        // special options per selected test
        Map<Integer, List<String>> testOpts = new HashMap<>();
        testOpts.put(200, Arrays.asList("-n", "3"));

        // SplittableRandom parameters
        class SRInfo {
            final long seed, gamma;

            SRInfo(long seed, long gamma) {
                this.seed = seed;
                this.gamma = gamma;
            }
        }

        ExecutorService exe = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            Random rnd = new Random();
            List<Future<DieharderTest.Results<SRInfo>>> futures = new ArrayList<>();

            // submit tasks ...

            for (int testId : testIds) {
                for (int ones = 1; ones < 64; ones++) {

                    long seed = rnd.nextLong();

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

                    futures.add(exe.submit(new DieharderTest<>(
                        testId, testOpts.get(testId),
                        new SRInfo(seed, gamma),
                        new SplittableRandom(seed, gamma, null), new byte[65536],
                        bufferFiller
                    )));
                }
            }

            // wait for and print out results...

            String header = String.format(
                " %16s | %64s %2s | %s",
                "seed", "gamma", "1s", DieharderTest.Results.HEADER
            );
            String delimiterLine = header.replaceAll("[^\\|]", "-");

            int testId = -1;
            for (Future<DieharderTest.Results<SRInfo>> future : futures) {
                DieharderTest.Results<SRInfo> results = future.get();
                // on the boundary of tests, print-out header
                if (results.testId != testId) {
                    System.out.println(delimiterLine);
                    System.out.println(header);
                    System.out.println(delimiterLine);
                    testId = results.testId;
                }
                if (results.exception == null) {
                    System.out.format(
                        " %16x | %64s %2d | %s\n",
                        results.rngInfo.seed,
                        toBinaryString64(results.rngInfo.gamma),
                        Long.bitCount(results.rngInfo.gamma),
                        results
                    );
                } else {
                    throw results.exception;
                }
            }
        } finally {
            exe.shutdown();
        }
    }

    private static String toBinaryString64(long value) {
        String bs = Long.toBinaryString(value);
        return "0000000000000000000000000000000000000000000000000000000000000000"
                   .substring(bs.length()) + bs;
    }
}
