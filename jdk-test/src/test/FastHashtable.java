/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * @author peter
 */
public final class FastHashtable<K, V> extends AtomicReferenceArray<Object> {

    private static final int stride = 1 << 1;

    private final int mask;

    public FastHashtable(int capacity) {
        super(tableSizeFor(capacity * stride));
        mask = length() - stride;
    }

    private static final int MAXIMUM_CAPACITY = 1 << 30;

    private static final int tableSizeFor(int c) {
        int n = c - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    public V get(K key) {
        int hash = key.hashCode();
        int len = length();
        for (int i = 0; i < len; i += stride) {
            int slot = (hash + i) & mask;
            Object tk = get(slot);
            if (key.equals(tk)) {
                return (V) get(slot + 1);
            } else if (tk == null) {
                break;
            }
        }

        return null;
    }

    public void put(K key, V value) {
        int hash = key.hashCode();
        int len = length();
        for (int i = 0; i < len; i += stride) {
            int slot = (hash + i) & mask;
            for (; ; ) {
                Object tk = get(slot);
                if (key.equals(tk)) {
                    int vslot = slot + 1;
                    set(vslot, value);
                    return;
                } else if (tk == null) {
                    if (compareAndSet(slot, null, key)) {
                        int vslot = slot + 1;
                        set(vslot, value);
                        return;
                    }
                } else {
                    break;
                }
            }
        }

        throw new ArrayIndexOutOfBoundsException("FastHashtable overflow");
    }

    public V getAndPut(K key, V value) {
        int hash = key.hashCode();
        int len = length();
        for (int i = 0; i < len; i += stride) {
            int slot = (hash + i) & mask;
            for (; ; ) {
                Object tk = get(slot);
                if (key.equals(tk)) {
                    int vslot = slot + 1;
                    return (V) getAndSet(vslot, value);
                } else if (tk == null) {
                    if (compareAndSet(slot, null, key)) {
                        int vslot = slot + 1;
                        return (V) getAndSet(vslot, value);
                    }
                } else {
                    break;
                }
            }
        }

        throw new ArrayIndexOutOfBoundsException("FastHashtable overflow");
    }
}
