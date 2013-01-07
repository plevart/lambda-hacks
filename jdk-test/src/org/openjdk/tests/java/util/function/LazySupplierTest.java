package org.openjdk.tests.java.util.function;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.function.Suppliers;

/**
 */
public class LazySupplierTest
{
    private static final AtomicInteger counter = new AtomicInteger();

    public static void main(String[] args)
    {
        Supplier<String> stringSupplier = Suppliers.cached(true, true, true, () -> "Hello World #" + counter.incrementAndGet());

        System.out.println(stringSupplier.get());
        System.out.println(stringSupplier.get());
        System.out.println(stringSupplier.get());
    }
}
