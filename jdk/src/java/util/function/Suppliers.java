/*
 * Written by Peter Levart and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package java.util.function;

import sun.misc.Unsafe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.LongAdder;

/**
 * Static utility methods pertaining to {@code Supplier} instances.<p>
 * All of the returned suppliers are serializable if given serializable
 * parameters.
 */
public final class Suppliers {
    private Suppliers() {
        throw new AssertionError("No instances!");
    }

    /**
     * Returns a {@code Supplier} that always returns a constant value
     *
     * @param value the value that is always returned
     * @param <T>   the type of value returned
     * @return a {@code Supplier} that always returns given value
     */
    public static <T> Supplier<T> constant(T value) {
        return new ConstantSupplier<>(value);
    }

    /**
     * Returns a {@code Supplier} that always throws unchecked exception
     *
     * @param rtException a {@code RuntimeException} that is always thrown
     * @param <T>         the type of value never returned
     * @return a {@code Supplier} that always throws given runtime exception
     * @throws NullPointerException if given a null parameter
     */
    public static <T> Supplier<T> throwing(RuntimeException rtException) throws NullPointerException {
        return new ThrowingSupplier<>(rtException);
    }

    /**
     * Returns a {@code Supplier} that always throws unchecked exception
     *
     * @param error an {@code Error} that is always thrown
     * @param <T>   the type of value never returned
     * @return a {@code Supplier} that always throws given error
     * @throws NullPointerException if given a null parameter
     */
    public static <T> Supplier<T> throwing(Error error) throws NullPointerException {
        return new ThrowingSupplier<>(error);
    }

    /**
     * Returns a {@link Supplier} that evaluates the given {@code supplier} parameter
     * on initial call to {@link Supplier#get()} and then caches the resulting value which is returned
     * in subsequent invocations. Useful for lazy just-in-time initialization of expensive singleton resources
     * that might not always be needed.<p>
     * Returned {@code Supplier} is serializable if the underlying {@code supplier} is also serializable.
     * Any cached value returned by the underlying {@code supplier} is not part of serialized stream.
     * When such supplier is de-serialized it is restored into an initial state - i.e.
     * the call to {@link Supplier#get()} on just de-serialized supplier will again trigger
     * underlying {@code supplier} evaluation.<p>
     * Boolean flags control how and what is being cached:
     *
     * @param optimisticEvaluation when {@code true} it means that underlying
     *                             {@code supplier} will be invoked optimistically, possibly multiple times in face of
     *                             concurrent initial evaluations of this supplier. A single instance will
     *                             nevertheless be returned to each concurrent thread and other obtained instances will
     *                             be released. This is the preferred mode when the given supplier argument is an
     *                             idempotent (side-effect free) supplier function. When this flag is {@code false}
     *                             it means that underlying {@code supplier} will be invoked at most once for
     *                             "cache-able" returns and any threads but one, trying to initially evaluate this
     *                             supplier concurrently, will block waiting for the one to obtain a "cache-able"
     *                             result.<p>
     * @param cacheNulls           when {@code true} it means that {@code null} value returned by underlying supplier
     *                             is "cache-able". When this flag is {@code false} it means that {@code null} values
     *                             will not be cached and will  cause re-evaluation of underlying supplier on
     *                             subsequent call to this supplier.<p>
     * @param cacheExceptions      when {@code true} it means that an unchecked exception thrown by underlying supplier
     *                             is "cache-able". When this flag is {@code false} it means that an unchecked
     *                             exception thrown by underlying supplier will not be cached and will cause
     *                             re-evaluation of underlying supplier on subsequent call to this supplier.<p>
     * @param supplier             the underlying {@link Supplier} to evaluate on initial call to this supplier<p>
     * @throws NullPointerException when given a null {@code supplier} parameter
     */
    public static <T> Supplier<T> cached(boolean optimisticEvaluation, boolean cacheNulls, boolean cacheExceptions, Supplier<T> supplier) {
        return new CachedSupplier<>(optimisticEvaluation, cacheNulls, cacheExceptions, supplier);
    }

    // package-private implementations ...

    static final class ConstantSupplier<T> implements Supplier<T>, Serializable {
        private static final long serialVersionUID = 1L;

        private final T value;

        ConstantSupplier(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }
    }

    static final class ThrowingSupplier<T> implements Supplier<T>, Serializable {
        private static final long serialVersionUID = 1L;

        private final Throwable throwable;

        ThrowingSupplier(Throwable throwable) throws NullPointerException, IllegalArgumentException {
            this.throwable = Objects.requireNonNull(throwable);
            if (!(throwable instanceof RuntimeException || throwable instanceof Error))
                throw new IllegalArgumentException("Not an unchecked exception: " + throwable);
        }

        @Override
        public T get() {
            return this.<RuntimeException>doThrow();
        }

        @SuppressWarnings("unchecked")
        private <Unchecked extends Throwable> T doThrow() throws Unchecked {
            throw (Unchecked) throwable;
        }
    }

    public static long races(Supplier<?> supplier) {
        return supplier instanceof CachedSupplier ? ((CachedSupplier) supplier).OPTIMISTIC_CACHED_RACES.sum() : 0L;
    }

    static final class CachedSupplier<T> implements Supplier<T>, Serializable {
        private static final long serialVersionUID = 1L;

        final LongAdder OPTIMISTIC_CACHED_RACES = new LongAdder();

        final boolean optimisticEvaluation, cacheNulls, cacheExceptions;
        final Supplier<T> supplier;

        CachedSupplier(boolean optimisticEvaluation, boolean cacheNulls, boolean cacheExceptions, Supplier<T> supplier) throws NullPointerException {
            this.optimisticEvaluation = optimisticEvaluation;
            this.cacheNulls = cacheNulls;
            this.cacheExceptions = cacheExceptions;
            this.supplier = Objects.requireNonNull(supplier);

            Current.set(this, optimisticEvaluation ? new OptimisticBootstrap() : new PessimisticBootstrap());
        }

        transient Supplier<T> current;

        static class Current {
            private static final Unsafe unsafe = Unsafe.getUnsafe();
            private static final long currentOffset;

            static {
                try {
                    currentOffset = unsafe.objectFieldOffset(CachedSupplier.class.getDeclaredField("current"));
                }
                catch (NoSuchFieldException e) {
                    throw new Error("No 'current' field found", e);
                }
            }

            @SuppressWarnings("unchecked")
            static <T> Supplier<T> get(CachedSupplier<T> cs) {
                return (Supplier<T>) unsafe.getObjectVolatile(cs, currentOffset);
            }

            static <T> void set(CachedSupplier<T> cs, Supplier<T> supplier) {
                unsafe.putObjectVolatile(cs, currentOffset, supplier);
            }

            static <T> boolean cas(CachedSupplier<T> cs, Supplier<T> oldSupplier, Supplier<T> newSupplier) {
                return unsafe.compareAndSwapObject(cs, currentOffset, oldSupplier, newSupplier);
            }
        }

        @Override
        public T get() {
            return current.get();
        }

        final class OptimisticBootstrap implements Supplier<T> {
            @Override
            public T get() {
                Supplier<T> current;
                try {
                    T value = supplier.get();
                    if (cacheNulls || value != null)
                        current = new ConstantSupplier<>(value);
                    else
                        return null;
                }
                catch (RuntimeException | Error throwable) {
                    if (cacheExceptions)
                        current = new ThrowingSupplier<>(throwable);
                    else
                        throw throwable;
                }

                if (!Current.cas(CachedSupplier.this, this, current))
                {
                    //noinspection unchecked
                    current = Current.get(CachedSupplier.this);
                    OPTIMISTIC_CACHED_RACES.increment();
                }

                return current.get();
            }
        }

        final class PessimisticBootstrap implements Supplier<T> {
            @Override
            public synchronized T get() {
                // re-check
                @SuppressWarnings("unchecked")
                Supplier<T> current = Current.get(CachedSupplier.this);
                if (current == this) {
                    try {
                        T value = supplier.get();
                        if (cacheNulls || value != null)
                            current = new ConstantSupplier<>(value);
                        else
                            return null;
                    }
                    catch (RuntimeException | Error throwable) {
                        if (cacheExceptions)
                            current = new ThrowingSupplier<>(throwable);
                        else
                            throw throwable;
                    }
                    Current.set(CachedSupplier.this, current);
                }

                return current.get();
            }
        }

        @SuppressWarnings("unchecked")
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            Current.set(this, optimisticEvaluation ? new OptimisticBootstrap() : new PessimisticBootstrap());
        }
    }
}
