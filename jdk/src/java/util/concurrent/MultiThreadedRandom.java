package java.util.concurrent;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.function.LongSupplier;

/**
 */
public class MultiThreadedRandom extends Random {
    private static final long multiplier = 0x5DEECE66DL;
    private static final long addend = 0xBL;
    private static final long mask = (1L << 48) - 1;

    private static final AtomicLong seedUniquifier
        = new AtomicLong(8682522807148012L);

    private static final LongSupplier defaultSeedSupplier = () -> {
        // L'Ecuyer, "Tables of Linear Congruential Generators of
        // Different Sizes and Good Lattice Structure", 1999
        for (; ; ) {
            long current = seedUniquifier.get();
            long next = current * 181783497276652981L;
            if (seedUniquifier.compareAndSet(current, next))
                return next;
        }
    };

    private final LongAccumulator rnd;

    public MultiThreadedRandom() {
        this(defaultSeedSupplier);
    }

    public MultiThreadedRandom(final LongSupplier seedSupplier) {
        super(0L); // ignored
        Objects.requireNonNull(seedSupplier);
        this.rnd = new LongAccumulator(
            (seed, _ignore) -> {
                if (seed == 0L) seed = (seedSupplier.getAsLong() ^ multiplier) & mask;
                return (seed * multiplier + addend) & mask;
            },
            0L
        );
    }

    public void setSeed(long seed) {
        if (rnd != null)
            throw new UnsupportedOperationException();
    }

    @Override
    protected int next(int bits) {
        long nextseed = rnd.accumulate(0L);
        return (int) (nextseed >>> (48 - bits));
    }
}
