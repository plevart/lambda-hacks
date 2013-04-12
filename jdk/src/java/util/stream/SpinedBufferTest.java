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

    static int dump(Spliterator<Integer> spliterator, int level, int iterateSize) {
        System.out.print("                                                ".substring(0, level));
        System.out.print(spliterator.estimateSize());
        Spliterator<Integer> spl2;
        if (spliterator.estimateSize() > iterateSize && (spl2 = spliterator.trySplit()) != null) {
            System.out.println(": split " + spl2.estimateSize() + " + " + spliterator.estimateSize());
            return dump(spl2, level + 1, iterateSize) +
                   dump(spliterator, level + 1, iterateSize);
        }
        else {
            Integer[] minMax = new Integer[2];
            spliterator.forEachRemaining(x -> {
                minMax[0] = minMax[0] == null ? x : Math.min(minMax[0], x);
                minMax[1] = minMax[1] == null ? x : Math.max(minMax[1], x);
            });
            System.out.println(": (" + minMax[0] + "..." + minMax[1] + ")");
            return 1;
        }
    }

    public static void main(String[] args) {
        SpinedBuffer<Integer> buf = new SpinedBuffer<>();

        for (int i = 0; i < 16384; i++) {
            buf.accept(i);
        }

        int leafs = dump(buf.spliterator(), 0, 1024);
        System.out.println("\nTotal leafs: " + leafs);
    }
}
