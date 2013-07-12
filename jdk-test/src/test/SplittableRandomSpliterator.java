/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.util.Comparator;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;

/**
 * @author peter
 */
public class SplittableRandomSpliterator<T> implements Spliterator<T> {
    private final Spliterator<T> spl;
    private final SplittableRandom rnd;
    private final ConcurrentMap<Thread, SplittableRandom> threadRndMap;

    public SplittableRandomSpliterator(Spliterator<T> spl, SplittableRandom rnd) {
        this(
            spl, rnd,
            new ConcurrentHashMap<>(Runtime.getRuntime().availableProcessors(), 0.5f)
        );
    }

    private SplittableRandomSpliterator(Spliterator<T> spl, SplittableRandom rnd,
                                        ConcurrentMap<Thread, SplittableRandom> threadRndMap) {
        this.spl = spl;
        this.rnd = rnd;
        this.threadRndMap = threadRndMap;
    }

    public <U> Consumer<U> usingRandom(BiConsumer<U, SplittableRandom> consumer) {
        return u -> consumer.accept(u, threadRndMap.get(Thread.currentThread()));
    }

    public <U, V> Function<U, V> usingRandom(BiFunction<U, SplittableRandom, V> function) {
        return u -> function.apply(u, threadRndMap.get(Thread.currentThread()));
    }

    // etc... for primitive variants

    public boolean tryAdvance(Consumer<? super T> action) {
        Thread current = Thread.currentThread();
        SplittableRandom prevRnd = threadRndMap.put(current, rnd);
        try {
            return spl.tryAdvance(action);
        } finally {
            if (prevRnd == null) {
                threadRndMap.remove(current);
            } else {
                threadRndMap.put(current, prevRnd);
            }
        }
    }

    public void forEachRemaining(Consumer<? super T> action) {
        Thread current = Thread.currentThread();
        SplittableRandom prevRnd = threadRndMap.put(current, rnd);
        try {
            spl.forEachRemaining(action);
        } finally {
            if (prevRnd == null) {
                threadRndMap.remove(current);
            } else {
                threadRndMap.put(current, prevRnd);
            }
        }
    }

    @Override
    public Spliterator<T> trySplit() {
        Spliterator<T> split = spl.trySplit();
        return split == null
               ? null
               : new SplittableRandomSpliterator<>(split, rnd.split(), threadRndMap);
    }

    @Override
    public long estimateSize() {
        return spl.estimateSize();
    }

    @Override
    public long getExactSizeIfKnown() {
        return spl.getExactSizeIfKnown();
    }

    @Override
    public int characteristics() {
        return spl.characteristics();
    }

    @Override
    public boolean hasCharacteristics(int characteristics) {
        return spl.hasCharacteristics(characteristics);
    }

    @Override
    public Comparator<? super T> getComparator() {
        return spl.getComparator();
    }


    public static class OfInt implements Spliterator.OfInt {
        private final Spliterator.OfInt spl;
        private final SplittableRandom rnd;
        private final FastHashtable<Thread, SplittableRandom> threadRndMap;

        public OfInt(Spliterator.OfInt spl, SplittableRandom rnd) {
            this(
                spl, rnd,
                new FastHashtable<>(Runtime.getRuntime().availableProcessors() * 2)
            );
        }

        private OfInt(Spliterator.OfInt spl, SplittableRandom rnd,
                      FastHashtable<Thread, SplittableRandom> threadRndMap) {
            this.spl = spl;
            this.rnd = rnd;
            this.threadRndMap = threadRndMap;
        }

        public IntConsumer consumerUsingRandom(IntObjectConsumer<SplittableRandom> consumer) {
            return u -> consumer.accept(u, threadRndMap.get(Thread.currentThread()));
        }

        public <R> IntFunction<R> fnUsingRandom(IntObjectFunction<SplittableRandom, R> function) {
            return u -> function.apply(u, threadRndMap.get(Thread.currentThread()));
        }

        public IntToLongFunction fnUsingRandom(IntObjectToLongFunction<SplittableRandom> function) {
            return u -> function.applyAsLong(u, threadRndMap.get(Thread.currentThread()));
        }

        // etc... for primitive variants

        public boolean tryAdvance(IntConsumer action) {
            Thread current = Thread.currentThread();
            SplittableRandom prevRnd = threadRndMap.getAndPut(current, rnd);
            try {
                return spl.tryAdvance(action);
            } finally {
                threadRndMap.put(current, prevRnd);
            }
        }

        public void forEachRemaining(IntConsumer action) {
            Thread current = Thread.currentThread();
            SplittableRandom prevRnd = threadRndMap.getAndPut(current, rnd);
            try {
                spl.forEachRemaining(action);
            } finally {
                threadRndMap.put(current, prevRnd);
            }
        }

        @Override
        public Spliterator.OfInt trySplit() {
            Spliterator.OfInt split = spl.trySplit();
            return split == null
                   ? null
                   : new SplittableRandomSpliterator.OfInt(split, rnd.split(), threadRndMap);
        }

        @Override
        public long estimateSize() {
            return spl.estimateSize();
        }

        @Override
        public long getExactSizeIfKnown() {
            return spl.getExactSizeIfKnown();
        }

        @Override
        public int characteristics() {
            return spl.characteristics();
        }

        @Override
        public boolean hasCharacteristics(int characteristics) {
            return spl.hasCharacteristics(characteristics);
        }

        @Override
        public Comparator<? super Integer> getComparator() {
            return spl.getComparator();
        }
    }
}
