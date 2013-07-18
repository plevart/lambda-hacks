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

/**
 * @author peter.levart@gmail.com
 */
public class Test {

    public static void main(String[] args) throws Exception {

        // dieharder tests to run
        int[] testIds = {
            0, 1, 2, 3, 4, 8, 9, 10, 11, 12, 13, 15, 16,
            /* 17, */ // 17 takes very long time to complete
            100, 101, 102,
            200, 201, 202, 203, 204, 205, 206, 207, 208, 209
        };

        // special options per selected test
        Map<Integer, List<String>> testOpts = new HashMap<>();
        testOpts.put(200, Arrays.asList("-n", "3"));

        Random rnd = new Random();
        ExecutorService exe = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<String>> results = new ArrayList<>();

        String headerPrefixFormat = " %5s | %16s | %64s ";
        String resultPrefixFormat = " %5d | %16x | %64s ";

        for (int testId : testIds) {
            results.add(exe.submit(new DieharderTest.Header(
                String.format(headerPrefixFormat, "ones", "seed", "gamma")
            )));

            for (int ones = 1; ones < 64; ones++) {
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

                results.add(exe.submit(new DieharderTest<>(
                    String.format(resultPrefixFormat, ones, seed, toBinaryString64(gamma)),
                    testId, testOpts.get(testId),
                    new byte[32768], new SplittableRandom(seed, gamma, null),
                    (buf, sr) -> {
                        for (int i = 0; i < buf.length; ) {
                            int x = sr.nextInt();
                            buf[i++] = (byte) (x & 0xFF); x >>>= 8;
                            buf[i++] = (byte) (x & 0xFF); x >>>= 8;
                            buf[i++] = (byte) (x & 0xFF); x >>>= 8;
                            buf[i++] = (byte) x;
                        }
                    }
                )));
            }
        }

        for (Future<String> result : results) {
            System.out.println(result.get());
        }
    }

    private static int mix32alt(long z) {
        int h = (int) (z >>> 32) ^ (int) z;
        h ^= h >> 16;
        h *= 0x85ebca6b;
        h ^= h >> 13;
        h *= 0xc2b2ae35;
        return h ^ (h >> 16);
    }

    private static String toBinaryString64(long value) {
        String bs = Long.toBinaryString(value);
        return "0000000000000000000000000000000000000000000000000000000000000000"
                   .substring(bs.length()) + bs;
    }
}
