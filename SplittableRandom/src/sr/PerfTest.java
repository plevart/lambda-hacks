/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package sr;

import si.pele.microbench.TestRunner;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author peter
 */
public class PerfTest extends TestRunner {

    public static class TLR_nextInt extends Test {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            while (loop.nextIteration()) {
                devNull1.yield(tlr.nextInt());
            }
        }
    }

    public static class TLR_current_nextInt extends Test {
        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            while (loop.nextIteration()) {
                devNull1.yield(ThreadLocalRandom.current().nextInt());
            }
        }
    }

    public static class SR_nextInt extends Test {
        SplittableRandom sr = new SplittableRandom(0L);
        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            while (loop.nextIteration()) {
                devNull1.yield(sr.nextInt());
            }
        }
    }

    public static class SR_nextIntAlt1 extends Test {
        SplittableRandom sr = new SplittableRandom(0L);
        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            while (loop.nextIteration()) {
                devNull1.yield(sr.nextIntAlt1());
            }
        }
    }

    public static class SR_nextIntAlt2 extends Test {
        SplittableRandom sr = new SplittableRandom(0L);
        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            while (loop.nextIteration()) {
                devNull1.yield(sr.nextIntAlt2());
            }
        }
    }

    public static void main(String[] args) throws Exception {

        doTest(TLR_nextInt.class, 5000L, 1, 4, 1);
        doTest(SR_nextInt.class, 5000L, 1, 4, 1);
        doTest(SR_nextIntAlt1.class, 5000L, 1, 4, 1);
        doTest(SR_nextIntAlt2.class, 5000L, 1, 4, 1);
    }
}
