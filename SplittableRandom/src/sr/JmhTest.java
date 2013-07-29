/**
 * Written by Peter.Levart@gmail.com 
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package sr;

import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.logic.BlackHole;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author peter
 */
@State
public class JmhTest {

    private ThreadLocalRandom tlr;
    private SplittableRandom sr;

    @Setup
    public void setup() {
        tlr = ThreadLocalRandom.current();
        sr = new SplittableRandom();
    }

    @GenerateMicroBenchmark
    public void TLR_nextInt(BlackHole bh) {
        bh.consume(tlr.nextInt());
    }

    @GenerateMicroBenchmark
    public void SR_nextInt(BlackHole bh) {
        bh.consume(sr.nextInt());
    }

    @GenerateMicroBenchmark
    public void SR_nextIntAlt1(BlackHole bh) {
        bh.consume(sr.nextIntAlt1());
    }

    @GenerateMicroBenchmark
    public void SR_nextIntAlt2(BlackHole bh) {
        bh.consume(sr.nextIntAlt2());
    }
}
