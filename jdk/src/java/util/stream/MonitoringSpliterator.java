/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package java.util.stream;

import java.util.Spliterator;
import java.util.function.IntConsumer;

/**
 * @author peter
 */
public class MonitoringSpliterator implements Spliterator.OfInt {

    private final Spliterator.OfInt delegate;
    private boolean iterating;

    public MonitoringSpliterator(OfInt delegate) {
        this.delegate = delegate;
    }

    @Override
    public OfInt trySplit() {
        OfInt split = delegate.trySplit();
        return split == null ? null : new MonitoringSpliterator(split);
    }

    @Override
    public boolean tryAdvance(IntConsumer action) {
        if (!iterating) {
            iterating = true;
            System.out.println("Starting iteration at estimateSize: " + delegate.estimateSize());
        }
        return delegate.tryAdvance(action);
    }

    @Override
    public void forEachRemaining(IntConsumer action) {
        if (!iterating) {
            iterating = true;
            System.out.println("Starting iteration at estimateSize: " + delegate.estimateSize());
        }
        delegate.forEachRemaining(action);
    }


    @Override
    public int characteristics() {
        return delegate.characteristics();
    }

    @Override
    public long estimateSize() {
        return delegate.estimateSize();
    }
}
