package java.util.function;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Static utility methods pertaining to {@code Supplier} instances.
 * <p/>
 * <p>All of the returned suppliers are serializable if given serializable
 * parameters.
 */
public final class Suppliers
{
    private Suppliers()
    {
        throw new AssertionError("No instances!");
    }

    /**
     * Returns a {@code Supplier} that always returns a constant value
     * @param value the value that is always returned
     * @param <T> the type of value returned
     * @return a {@code Supplier} that always returns given value
     */
    public static <T> Supplier<T> constant(T value)
    {
        return new ConstantSupplier<>(value);
    }

    /**
     * Returns a {@code Supplier} that always throws unchecked exception
     * @param rtException a {@code RuntimeException} that is always thrown
     * @param <T> the type of value never returned
     * @return a {@code Supplier} that always throws given runtime exception
     */
    public static <T> Supplier<T> throwing(RuntimeException rtException)
    {
        return new ThrowingSupplier<>(rtException);
    }

    /**
     * Returns a {@code Supplier} that always throws unchecked exception
     * @param error an {@code Error} that is always thrown
     * @param <T> the type of value never returned
     * @return a {@code Supplier} that always throws given error
     */
    public static <T> Supplier<T> throwing(Error error)
    {
        return new ThrowingSupplier<>(error);
    }

    /**
     * Returns a {@link Supplier} that evaluates the given {@code supplier} parameter
     * on call to {@link Supplier#get()} and then caches the resulting value which is returned
     * in subsequent invocations.
     * <p/>
     * Returned {@code Supplier} is serializable if the underlying {@code supplier} is also serializable.
     * Any cached value returned by the underlying {@code supplier} is not part of serialized stream.
     * When it is de-serialized it is restored into an initial state - i.e.
     * the call to {@link Supplier#get()} on just de-serialized supplier will again trigger
     * underlying {@code supplier} evaluation.
     * <p/>
     */
    public static <T> Supplier<T> cached(boolean optimisticEvaluation, boolean cacheNulls, boolean cacheExceptions, Supplier<T> supplier)
    {
        return optimisticEvaluation
               ? new OptimisticCachedSupplier<>(cacheNulls, cacheExceptions, Objects.requireNonNull(supplier))
               : new PessimisticCachedSupplier<>(cacheNulls, cacheExceptions, Objects.requireNonNull(supplier));
    }


    static final class ConstantSupplier<T> implements Supplier<T>, Serializable
    {
        private static final long serialVersionUID = 1L;

        private final T value;

        ConstantSupplier(T value)
        {
            this.value = value;
        }

        @Override
        public T get()
        {
            return value;
        }
    }

    static final class ThrowingSupplier<T> implements Supplier<T>, Serializable
    {
        private static final long serialVersionUID = 1L;

        private final Throwable throwable;

        ThrowingSupplier(Throwable throwable)
        {
            if (!(throwable instanceof RuntimeException || throwable instanceof Error))
                throw new IllegalArgumentException("Not an unchecked exception: " + throwable);

            this.throwable = throwable;
        }

        @Override
        public T get()
        {
            return this.<RuntimeException>doThrow();
        }

        @SuppressWarnings("unchecked")
        private <Unchecked extends Throwable> T doThrow() throws Unchecked
        {
            throw (Unchecked) throwable;
        }
    }

    static final class OptimisticCachedSupplier<T> implements Supplier<T>, Serializable
    {
        private static final long serialVersionUID = 1L;

        final boolean cacheNulls, cacheExceptions;
        final Supplier<T> supplier;

        OptimisticCachedSupplier(boolean cacheNulls, boolean cacheExceptions, Supplier<T> supplier)
        {
            this.cacheNulls = cacheNulls;
            this.cacheExceptions = cacheExceptions;
            this.supplier = Objects.requireNonNull(supplier);

            current = new OptimisticBootstrap<>(this);
        }

        transient Supplier<T> current;

        static final AtomicReferenceFieldUpdater<OptimisticCachedSupplier, Supplier> currentUpdater
            = AtomicReferenceFieldUpdater.newUpdater(OptimisticCachedSupplier.class, Supplier.class, "current");

        @Override
        public T get()
        {
            return current.get();
        }

        static final class OptimisticBootstrap<T> implements Supplier<T>
        {
            private final OptimisticCachedSupplier<T> outer;

            OptimisticBootstrap(OptimisticCachedSupplier<T> outer)
            {
                this.outer = outer;
            }

            @Override
            public T get()
            {
                Supplier<T> current;
                try
                {
                    T value = outer.supplier.get();
                    if (outer.cacheNulls || value != null)
                        current = new ConstantSupplier<>(value);
                    else
                        return null;
                }
                catch (RuntimeException | Error throwable)
                {
                    if (outer.cacheExceptions)
                        current = new ThrowingSupplier<>(throwable);
                    else
                        throw throwable;
                }

                if (currentUpdater.compareAndSet(outer, this, current))
                    return current.get();
                else
                    return outer.current.get();
            }
        }

        @SuppressWarnings("unchecked")
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
        {
            in.defaultReadObject();
            current = new OptimisticBootstrap<T>(this);
        }
    }

    static final class PessimisticCachedSupplier<T> implements Supplier<T>, Serializable
    {
        private static final long serialVersionUID = 1L;

        private final Supplier<T> supplier;
        private final boolean cacheNulls, cacheExceptions;

        PessimisticCachedSupplier(boolean cacheNulls, boolean cacheExceptions, Supplier<T> supplier)
        {
            this.supplier = supplier;
            this.cacheNulls = cacheNulls;
            this.cacheExceptions = cacheExceptions;
        }

        private volatile transient Object none;
        private volatile transient Object value = none = new Object();

        @Override
        public T get()
        {
            @SuppressWarnings("unchecked")
            T value = (T) this.value;
            Object none = this.none;
            return value != none ? value : createValue(none);
        }

        private T createValue(Object none)
        {
            synchronized (none)
            {
                // recheck
                Object value = this.value;
                if (value == none)
                {
                    try
                    {
                        value = supplier.get();
                    }
                    catch (Throwable t)
                    {
                        if (cacheExceptions)
                        {
                            value = new ExceptionWrapper(t);
                        }
                        else
                        {
                            throw t;
                        }
                    }

                    if (value != null || cacheNulls)
                        this.value = value;
                }

                if (cacheExceptions && value instanceof ExceptionWrapper)
                    ((ExceptionWrapper) value).<RuntimeException>doThrow();

                return (T) value;
            }
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
        {
            in.defaultReadObject();
            value = none = new Object();
        }
    }

    static final class ExceptionWrapper
    {
        private final Throwable throwable;

        ExceptionWrapper(Throwable throwable)
        {
            this.throwable = throwable;
        }

        @SuppressWarnings("unchecked")
        <T extends Throwable> void doThrow() throws T
        {
            throw (T) throwable;
        }
    }
}
