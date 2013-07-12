/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * @author peter
 */
public class Test
{
    static long threadLocalRandomTest() {
        long t0 = System.nanoTime();
        long sum = IntStream.range(0, 1000000000)
            .parallel()
            .mapToLong(i -> ThreadLocalRandom.current().nextLong() + i)
            .sum();
        return System.nanoTime() - t0;
    }

    static long splittableRandomTest() {
        long t0 = System.nanoTime();
        SplittableRandomSpliterator.OfInt spl = new SplittableRandomSpliterator.OfInt(
            IntStream.range(0, 1000000000).spliterator(),
            new SplittableRandom()
        );
        long sum = StreamSupport.intStream(spl, true)
            .mapToLong(spl.fnUsingRandom((i, rnd) -> rnd.nextLong() + i))
            .sum();
        return System.nanoTime() - t0;
    }

    public static void main(String[] args)
    {
        System.out.println("threadLocalRandomTest: " + threadLocalRandomTest() + " nanos");
        System.out.println("threadLocalRandomTest: " + threadLocalRandomTest() + " nanos");
        System.out.println("threadLocalRandomTest: " + threadLocalRandomTest() + " nanos");
        System.out.println("threadLocalRandomTest: " + threadLocalRandomTest() + " nanos");
        System.out.println("threadLocalRandomTest: " + threadLocalRandomTest() + " nanos");
        System.out.println("threadLocalRandomTest: " + threadLocalRandomTest() + " nanos");
        System.out.println("threadLocalRandomTest: " + threadLocalRandomTest() + " nanos");
        System.out.println("threadLocalRandomTest: " + threadLocalRandomTest() + " nanos");
        System.out.println();
        System.out.println(" splittableRandomTest: " + splittableRandomTest() + " nanos");
        System.out.println(" splittableRandomTest: " + splittableRandomTest() + " nanos");
        System.out.println(" splittableRandomTest: " + splittableRandomTest() + " nanos");
        System.out.println(" splittableRandomTest: " + splittableRandomTest() + " nanos");
        System.out.println(" splittableRandomTest: " + splittableRandomTest() + " nanos");
        System.out.println(" splittableRandomTest: " + splittableRandomTest() + " nanos");
        System.out.println(" splittableRandomTest: " + splittableRandomTest() + " nanos");
    }
}
