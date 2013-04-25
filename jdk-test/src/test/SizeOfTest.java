/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import si.pele.microbench.SizeOf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public class SizeOfTest {
    public static void main(String[] args) {
        ConcurrentHashMap<Object, Object> chm = new ConcurrentHashMap<>();
        SizeOf sizeOf = new SizeOf(SizeOf.Visitor.STDOUT);
        sizeOf.deepSizeOf(chm);
    }
}
