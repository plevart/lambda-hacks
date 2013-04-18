/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package java.util.stream;

import java.util.Spliterator;

/**
 * @author peter
 */
public class SpinedBufferTest {

    static int dump(Spliterator<? extends Comparable> spliterator, int level, int iterateSize) {
        System.out.print("                                                ".substring(0, level));
        System.out.print(spliterator.estimateSize());
        Spliterator<? extends Comparable> spl2;
        if (spliterator.estimateSize() > iterateSize && (spl2 = spliterator.trySplit()) != null) {
            System.out.println(": split " + spl2.estimateSize() + " + " + spliterator.estimateSize());
            return dump(spl2, level + 1, iterateSize) +
                   dump(spliterator, level + 1, iterateSize);
        }
        else {
            Comparable[] minMax = new Comparable[2];
            spliterator.forEachRemaining(x -> {
                minMax[0] = minMax[0] == null || minMax[0].compareTo(x) > 0 ? x : minMax[0];
                minMax[1] = minMax[1] == null || minMax[1].compareTo(x) < 0 ? x : minMax[0];
            });
            System.out.println(": (" + minMax[0] + "..." + minMax[1] + ")");
            return 1;
        }
    }

    static void testRef() {
        SpinedBuffer<Integer> buf = new SpinedBuffer<>();
        for (int i = 0; i < 16384; i++) {
            buf.accept(i);
        }
        System.out.println("\n***" + buf.getClass().getName());
        int leafs = dump(buf.spliterator(), 0, 1024);
        System.out.println("\nTotal leafs: " + leafs);
    }

    static void testInt() {
        SpinedBuffer.OfInt buf = new SpinedBuffer.OfInt();
        for (int i = 0; i < 16384; i++) {
            buf.accept(i);
        }
        System.out.println("\n***" + buf.getClass().getName());
        int leafs = dump(buf.spliterator(), 0, 1024);
        System.out.println("\nTotal leafs: " + leafs);
    }

    static void testLong() {
        SpinedBuffer.OfLong buf = new SpinedBuffer.OfLong();
        for (int i = 0; i < 16384; i++) {
            buf.accept((long)i);
        }
        System.out.println("\n***" + buf.getClass().getName());
        int leafs = dump(buf.spliterator(), 0, 1024);
        System.out.println("\nTotal leafs: " + leafs);
    }

    static void testDouble() {
        SpinedBuffer.OfDouble buf = new SpinedBuffer.OfDouble();
        for (int i = 0; i < 16384; i++) {
            buf.accept((double)i);
        }
        System.out.println("\n***" + buf.getClass().getName());
        int leafs = dump(buf.spliterator(), 0, 1024);
        System.out.println("\nTotal leafs: " + leafs);
    }

    public static void main(String[] args) {
        testRef();
        testInt();
        testLong();
        testDouble();
    }
}
