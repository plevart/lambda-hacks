/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.util.stream;

import java.util.*;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;

/**
 * An ordered collection of elements.  Elements can be added, but not removed.
 * <p>
 * One or more arrays are used to store elements.
 * The use of a multiple arrays has better performance characteristics than a single array used by {@link ArrayList}
 * when the capacity of the list needs to be increased as no copying of elements is required.
 * The trade-off is the elements may be fragmented over two or more arrays. However, for the purposes
 * of adding elements, iterating and splitting this trade-off is acceptable.
 * </p>
 *
 * @param <E> the type of elements in this list
 * @since 1.8
 */
class SpinedBuffer<E> extends AbstractSpinedBuffer<E> implements Consumer<E>, Iterable<E> {

    /*
     * We optimistically hope that all the data will fit into the first chunk, so we try to avoid
     * inflating the spine[] and priorElementCount[] arrays prematurely.  So methods must be prepared
     * to deal with these arrays being null.  If spine is non-null, then spineIndex points to the current
     * chunk within the spine, otherwise it is zero.  The spine and priorElementCount arrays are always
     * the same size, and for any i <= spineIndex, priorElementCount[i] is the sum of the sizes of all
     * the prior chunks.
     *
     * The curChunk pointer is always valid.  The elementIndex is the index of the next element to be
     * written in curChunk; this may be past the end of curChunk so we have to check before writing.
     * When we inflate the spine array, curChunk becomes the first element in it.  When we clear the
     * buffer, we discard all chunks except the first one, which we clear, restoring it to the initial
     * single-chunk state.
     *
     */

    /** Chunk that we're currently writing into; may be aliased with an element of the spine, or not */
    protected E[] curChunk;

    /** All chunks, or null if there is only one chunk */
    protected E[][] spine;

    /**
     * Constructs an empty list with the specified initial capacity.
     *
     * @param  initialCapacity  the initial capacity of the list
     * @throws IllegalArgumentException if the specified initial capacity
     *         is negative
     */
    SpinedBuffer(int initialCapacity) {
        super(initialCapacity);
        curChunk = (E[]) new Object[1 << initialChunkPower];
    }

    /**
     * Constructs an empty list with an initial capacity of sixteen.
     */
    SpinedBuffer() {
        super();
        curChunk = (E[]) new Object[1 << initialChunkPower];
    }

    /**
     * Constructs a list containing the elements of the specified
     * iterable, in the order they are returned by the iterables's
     * iterator.
     *
     * @param i the iterable whose elements are to be placed into this list
     * @throws NullPointerException if the specified iterable is null
     */
    SpinedBuffer(Iterable<E> i) {
        this();

        // @@@ This can be more efficient if c.toArray() is used
        i.forEach(this);

        // @@@ Note for testing purposes we need to simulate the contents as if
        //     elements are added individually, if this method is modified check usage in tests and
        //     as data source
    }

    /** Returns the current capacity of the buffer */
    protected long capacity() {
        return (spineIndex == 0)
               ? curChunk.length
               : priorElementCount[spineIndex] + spine[spineIndex].length;
    }

    private void inflateSpine() {
        if (spine == null) {
            spine = (E[][]) new Object[MIN_SPINE_SIZE][];
            priorElementCount = new long[MIN_SPINE_SIZE];
            spine[0] = curChunk;
        }
    }

    /** Ensure that the buffer has at least capacity to hold the target size */
    protected final void ensureCapacity(long targetSize) {
        long capacity = capacity();
        if (targetSize > capacity) {
            inflateSpine();
            for (int i=spineIndex+1; targetSize > capacity; i++) {
                if (i >= spine.length) {
                    int newSpineSize = spine.length * 2;
                    spine = Arrays.copyOf(spine, newSpineSize);
                    priorElementCount = Arrays.copyOf(priorElementCount, newSpineSize);
                }
                int nextChunkSize = chunkSize(i);
                spine[i] = (E[]) new Object[nextChunkSize];
                priorElementCount[i] = priorElementCount[i-1] + spine[i-1].length;
                capacity += nextChunkSize;
            }
        }
    }

    /** Force the buffer to increase its capacity */
    protected void increaseCapacity() {
        ensureCapacity(capacity() + 1);
    }

    /** Retrieve the element at the specified index */
    public E get(long index) {
        // @@@ can further optimize by caching last seen spineIndex, which is going to be right most of the time
        if (spineIndex == 0) {
            if (index < elementIndex)
                return curChunk[((int) index)];
            else
                throw new IndexOutOfBoundsException(Long.toString(index));
        }

        if (index >= count())
            throw new IndexOutOfBoundsException(Long.toString(index));

        for (int j=0; j <= spineIndex; j++)
            if (index < priorElementCount[j] + spine[j].length)
                return spine[j][((int) (index - priorElementCount[j]))];

        throw new IndexOutOfBoundsException(Long.toString(index));
    }

    /** Copy the elements, starting at the specified offset, into the specified array */
    public void copyInto(E[] array, int offset) {
        long finalOffset = offset + count();
        if (finalOffset > array.length || finalOffset < offset) {
            throw new IndexOutOfBoundsException("does not fit");
        }

        if (spineIndex == 0)
            System.arraycopy(curChunk, 0, array, offset, elementIndex);
        else {
            // full chunks
            for (int i=0; i < spineIndex; i++) {
                System.arraycopy(spine[i], 0, array, offset, spine[i].length);
                offset += spine[i].length;
            }
            if (elementIndex > 0)
                System.arraycopy(curChunk, 0, array, offset, elementIndex);
        }
    }

    /** Create a new array using the specified array factory, and copy the elements into it */
    public E[] asArray(IntFunction<E[]> arrayFactory) {
        // @@@ will fail for size == MAX_VALUE
        E[] result = arrayFactory.apply((int) count());

        copyInto(result, 0);

        return result;
    }

    @Override
    public void clear() {
        if (spine != null) {
            curChunk = spine[0];
            for (int i=0; i<curChunk.length; i++)
                curChunk[i] = null;
            spine = null;
            priorElementCount = null;
        }
        else {
            for (int i=0; i<elementIndex; i++)
                curChunk[i] = null;
        }
        elementIndex = 0;
        spineIndex = 0;
    }

    @Override
    public Iterator<E> iterator() {
        return Spliterators.iteratorFromSpliterator(spliterator());
    }

    @Override
    public void forEach(Consumer<? super E> consumer) {
        // completed chunks, if any
        for (int j = 0; j < spineIndex; j++)
            for (E t : spine[j])
                consumer.accept(t);

        // current chunk
        for (int i=0; i<elementIndex; i++)
            consumer.accept(curChunk[i]);
    }

    @Override
    public void accept(E e) {
        if (elementIndex == curChunk.length) {
            inflateSpine();
            if (spineIndex+1 >= spine.length || spine[spineIndex+1] == null)
                increaseCapacity();
            elementIndex = 0;
            ++spineIndex;
            curChunk = spine[spineIndex];
        }
        curChunk[elementIndex++] = e;
    }

    @Override
    public String toString() {
        List<E> list = new ArrayList<>();
        forEach(list::add);
        return "SpinedBuffer:" + list.toString();
    }

    private static final int SPLITERATOR_CHARACTERISTICS
            = Spliterator.SIZED | Spliterator.ORDERED | Spliterator.SUBSIZED;

    static final boolean useAltSpliterator = true;

    Spliterator<E> spliterator(
        int firstSpineIndex,             // 1st spine (inclusive)
        int lastSpineIndex,              // last spine (inclusive)
        int firstSpineStartElementIndex, // index of 1st element in 1st spine (inclusive)
        int lastSpineEndElementIndex     // index of end element of last spine (exclusive)
    ) {
        if (firstSpineIndex == lastSpineIndex) {
            assert spine != null || firstSpineIndex == 0;
            return Arrays.spliterator((spine == null) ? curChunk : spine[firstSpineIndex], firstSpineStartElementIndex, lastSpineEndElementIndex);
        }
        else {
            return new AltSpliterator(firstSpineIndex, lastSpineIndex, firstSpineStartElementIndex, lastSpineEndElementIndex);
        }
    }

    class AltSpliterator implements Spliterator<E> {

        // The current spine index
        int splSpineIndex;

        // Last spine index
        int lastSpineIndex;

        // The current element index into the current spine
        int splElementIndex;

        // Last spine's last element index + 1
        int lastSpineEndElementIndex;

        // When splSpineIndex >= lastSpineIndex and splElementIndex >= lastSpineEndElementIndex then
        // this spliterator is fully traversed

        // The current spine array
        E[] splChunk;

        AltSpliterator(int firstSpineIndex, int lastSpineIndex, int firstSpineStartElementIndex, int lastSpineEndElementIndex) {
            this.splSpineIndex = firstSpineIndex;
            this.lastSpineIndex = lastSpineIndex;
            this.splElementIndex = firstSpineStartElementIndex;
            this.lastSpineEndElementIndex = lastSpineEndElementIndex;
            assert spine != null || firstSpineIndex == 0 && lastSpineIndex == 0;
            splChunk = (spine == null) ? curChunk : spine[firstSpineIndex];
        }

        @Override
        public long estimateSize() {
            return (splSpineIndex == lastSpineIndex)
                   ? (long)lastSpineEndElementIndex - splElementIndex
                   : priorElementCount[lastSpineIndex] + lastSpineEndElementIndex - // # of elements prior to end -
                     priorElementCount[splSpineIndex] - splElementIndex;            // # of elements prior to current
        }

        @Override
        public int characteristics() {
            return SPLITERATOR_CHARACTERISTICS;
        }

        @Override
        public boolean tryAdvance(Consumer<? super E> consumer) {
            if (splSpineIndex < lastSpineIndex || (splSpineIndex == lastSpineIndex && splElementIndex < lastSpineEndElementIndex)) {
                consumer.accept(splChunk[splElementIndex++]);
                if (splElementIndex == splChunk.length) {
                    splElementIndex = 0;
                    splChunk = spine[++splSpineIndex];
                }
                return true;
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super E> consumer) {
            if (splSpineIndex < lastSpineIndex || (splSpineIndex == lastSpineIndex && splElementIndex < lastSpineEndElementIndex)) {
                int i = splElementIndex;
                // completed chunks, if any
                for (int sp = splSpineIndex; sp < lastSpineIndex; sp++) {
                    E[] chunk = spine[sp];
                    for (; i < chunk.length; i++) {
                        consumer.accept(chunk[i]);
                    }
                    i = 0;
                }
                // last chunk
                E[] chunk = (splSpineIndex == lastSpineIndex) ? splChunk : spine[lastSpineIndex];
                int hElementIndex = lastSpineEndElementIndex;
                for (; i < hElementIndex; i++) {
                    consumer.accept(chunk[i]);
                }
                // mark consumed
                splSpineIndex = lastSpineIndex;
                splElementIndex = lastSpineEndElementIndex;
            }
        }

        @Override
        public Spliterator<E> trySplit() {
            if (splSpineIndex < lastSpineIndex) {
                // split just before last chunk (if it is full this means 50:50 split)
                Spliterator<E> ret = spliterator(splSpineIndex, lastSpineIndex - 1, splElementIndex, spine[lastSpineIndex-1].length);
                // position us to start of last chunk
                splSpineIndex = lastSpineIndex;
                splElementIndex = 0;
                splChunk = spine[splSpineIndex];
                return ret;
            }
            else if (splSpineIndex == lastSpineIndex) {
                int t = (lastSpineEndElementIndex - splElementIndex) / 2;
                if (t == 0)
                    return null;
                else {
                    Spliterator<E> ret = Arrays.spliterator(splChunk, splElementIndex, splElementIndex + t);
                    splElementIndex += t;
                    return ret;
                }
            }
            else {
                return null;
            }
        }
    }

    public Spliterator<E> spliterator() {
        return useAltSpliterator ? spliterator(0, spineIndex, 0, elementIndex) : originalSpliterator();
    }

    /** Return a {@link Spliterator} describing the contents of the buffer */
    Spliterator<E> originalSpliterator() {
        return new Spliterator<E>() {
            // The current spine index
            int splSpineIndex;

            // The current element index into the current spine
            int splElementIndex;

            // When splSpineIndex >= spineIndex and splElementIndex >= elementIndex then
            // this spliterator is fully traversed
            // tryAdvance can set splSpineIndex > spineIndex if the last spine is full

            // The current spine array
            E[] splChunk = (spine == null) ? curChunk : spine[0];

            @Override
            public long estimateSize() {
                return (spine == null)
                       ? (elementIndex - splElementIndex)
                       : count() - (priorElementCount[splSpineIndex] + splElementIndex);
            }

            @Override
            public int characteristics() {
                return SPLITERATOR_CHARACTERISTICS;
            }

            @Override
            public boolean tryAdvance(Consumer<? super E> consumer) {
                if (splSpineIndex < spineIndex || (splSpineIndex == spineIndex && splElementIndex < elementIndex)) {
                    consumer.accept(splChunk[splElementIndex++]);

                    if (splElementIndex == splChunk.length) {
                        splElementIndex = 0;
                        ++splSpineIndex;
                        if (spine != null && splSpineIndex < spine.length)
                            splChunk = spine[splSpineIndex];
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void forEachRemaining(Consumer<? super E> consumer) {
                if (splSpineIndex < spineIndex || (splSpineIndex == spineIndex && splElementIndex < elementIndex)) {
                    int i = splElementIndex;
                    // completed chunks, if any
                    for (int sp = splSpineIndex; sp < spineIndex; sp++) {
                        E[] chunk = spine[sp];
                        for (; i < chunk.length; i++) {
                            consumer.accept(chunk[i]);
                        }
                        i = 0;
                    }

                    // current chunk
                    E[] chunk = curChunk;
                    int hElementIndex = elementIndex;
                    for (; i < hElementIndex; i++) {
                        consumer.accept(chunk[i]);
                    }

                    splSpineIndex = spineIndex;
                    splElementIndex = elementIndex;
                }
            }

            @Override
            public Spliterator<E> trySplit() {
                if (splSpineIndex < spineIndex) {
                    Spliterator<E> ret = Arrays.spliterator(spine[splSpineIndex], 0, spine[splSpineIndex].length);
                    splChunk = spine[++splSpineIndex];
                    return ret;
                }
                else if (splSpineIndex == spineIndex) {
                    int t = (elementIndex - splElementIndex) / 2;
                    if (t == 0)
                        return null;
                    else {
                        Spliterator<E> ret = Arrays.spliterator(curChunk, splElementIndex, splElementIndex + t);
                        splElementIndex += t;
                        return ret;
                    }
                }
                else {
                    return null;
                }
            }
        };
    }

    /**
     * An ordered collection of primitive values.
     * Values can be added, no values can be removed.
     * <p>
     * One or more arrays are used to store values.
     * The use of a multiple arrays has better performance characteristics than a single array used by {@link ArrayList}
     * when the capacity of the list needs to be increased. No copying of arrays of values is required.
     * The trade-off is the values may be fragmented over two or more arrays. However, for the purposes
     * of adding values, iterating and splitting this trade-off is acceptable.
     * </p>
     *
     * @param <E> the type of primitive value.
     * @param <A> the type primitive array of values.
     * @param <C> the type of primitive consumer.
     */
    abstract static class OfPrimitive<E, A, C>
            extends AbstractSpinedBuffer<E> implements Iterable<E> {

        /*
         * We optimistically hope that all the data will fit into the first chunk, so we try to avoid
         * inflating the spine[] and priorElementCount[] arrays prematurely.  So methods must be prepared
         * to deal with these arrays being null.  If spine is non-null, then spineIndex points to the current
         * chunk within the spine, otherwise it is zero.  The spine and priorElementCount arrays are always
         * the same size, and for any i <= spineIndex, priorElementCount[i] is the sum of the sizes of all
         * the prior chunks.
         *
         * The curChunk pointer is always valid.  The elementIndex is the index of the next element to be
         * written in curChunk; this may be past the end of curChunk so we have to check before writing.
         * When we inflate the spine array, curChunk becomes the first element in it.  When we clear the
         * buffer, we discard all chunks except the first one, which we clear, restoring it to the initial
         * single-chunk state.
         *
         */

        // The chunk we're currently writing into
        A curChunk;

        // All chunks, or null if there is only one chunk
        A[] spine;

        /**
         * Constructs an empty list with the specified initial capacity.
         *
         * @param  initialCapacity  the initial capacity of the list
         * @throws IllegalArgumentException if the specified initial capacity
         *         is negative
         */
        OfPrimitive(int initialCapacity) {
            super(initialCapacity);
            curChunk = newArray(1 << initialChunkPower);
        }

        /**
         * Constructs an empty list with an initial capacity of sixteen.
         */
        OfPrimitive() {
            super();
            curChunk = newArray(1 << initialChunkPower);
        }

        @Override
        public abstract Iterator<E> iterator();

        @Override
        public abstract void forEach(Consumer<? super E> consumer);


        protected abstract A[] newArrayArray(int size);
        protected abstract A newArray(int size);
        protected abstract int arrayLength(A array);
        protected abstract void arrayForEach(A array, int from, int to, C consumer);

        protected long capacity() {
            return (spineIndex == 0)
                   ? arrayLength(curChunk)
                   : priorElementCount[spineIndex] + arrayLength(spine[spineIndex]);
        }

        private void inflateSpine() {
            if (spine == null) {
                spine = newArrayArray(MIN_SPINE_SIZE);
                priorElementCount = new long[MIN_SPINE_SIZE];
                spine[0] = curChunk;
            }
        }

        protected final void ensureCapacity(long targetSize) {
            long capacity = capacity();
            if (targetSize > capacity) {
                inflateSpine();
                for (int i=spineIndex+1; targetSize > capacity; i++) {
                    if (i >= spine.length) {
                        int newSpineSize = spine.length * 2;
                        spine = Arrays.copyOf(spine, newSpineSize);
                        priorElementCount = Arrays.copyOf(priorElementCount, newSpineSize);
                    }
                    int nextChunkSize = chunkSize(i);
                    spine[i] = newArray(nextChunkSize);
                    priorElementCount[i] = priorElementCount[i-1] + arrayLength(spine[i - 1]);
                    capacity += nextChunkSize;
                }
            }
        }

        protected void increaseCapacity() {
            ensureCapacity(capacity() + 1);
        }

        protected int chunkFor(long index) {
            if (spineIndex == 0) {
                if (index < elementIndex)
                    return 0;
                else
                    throw new IndexOutOfBoundsException(Long.toString(index));
            }

            if (index >= count())
                throw new IndexOutOfBoundsException(Long.toString(index));

            for (int j=0; j <= spineIndex; j++)
                if (index < priorElementCount[j] + arrayLength(spine[j]))
                    return j;

            throw new IndexOutOfBoundsException(Long.toString(index));
        }

        public void copyInto(A array, int offset) {
            long finalOffset = offset + count();
            if (finalOffset > arrayLength(array) || finalOffset < offset) {
                throw new IndexOutOfBoundsException("does not fit");
            }

            if (spineIndex == 0)
                System.arraycopy(curChunk, 0, array, offset, elementIndex);
            else {
                // full chunks
                for (int i=0; i < spineIndex; i++) {
                    System.arraycopy(spine[i], 0, array, offset, arrayLength(spine[i]));
                    offset += arrayLength(spine[i]);
                }
                if (elementIndex > 0)
                    System.arraycopy(curChunk, 0, array, offset, elementIndex);
            }
        }

        public A asPrimitiveArray() {
            // @@@ will fail for size == MAX_VALUE
            A result = newArray((int) count());
            copyInto(result, 0);
            return result;
        }

        protected void preAccept() {
            if (elementIndex == arrayLength(curChunk)) {
                inflateSpine();
                if (spineIndex+1 >= spine.length || spine[spineIndex+1] == null)
                    increaseCapacity();
                elementIndex = 0;
                ++spineIndex;
                curChunk = spine[spineIndex];
            }
        }

        public void clear() {
            if (spine != null) {
                curChunk = spine[0];
                spine = null;
                priorElementCount = null;
            }
            elementIndex = 0;
            spineIndex = 0;
        }

        public void forEach(C consumer) {
            // completed chunks, if any
            for (int j = 0; j < spineIndex; j++)
                arrayForEach(spine[j], 0, arrayLength(spine[j]), consumer);

            // current chunk
            arrayForEach(curChunk, 0, elementIndex, consumer);
        }

        abstract class BaseSpliterator<T_SPLITER extends Spliterator<E>> implements Spliterator<E> {
            // The current spine index
            int splSpineIndex;

            // The current element index into the current spine
            int splElementIndex;

            // When splSpineIndex >= spineIndex and splElementIndex >= elementIndex then
            // this spliterator is fully traversed
            // tryAdvance can set splSpineIndex > spineIndex if the last spine is full

            // The current spine array
            A splChunk = (spine == null) ? curChunk : spine[0];

            abstract void arrayForOne(A array, int index, C consumer);

            abstract T_SPLITER arraySpliterator(A array, int offset, int len);

            @Override
            public long estimateSize() {
                return (spine == null)
                       ? (elementIndex - splElementIndex)
                       : count() - (priorElementCount[splSpineIndex] + splElementIndex);
            }

            @Override
            public int characteristics() {
                return SPLITERATOR_CHARACTERISTICS;
            }

            public boolean tryAdvance(C consumer) {
                if (splSpineIndex < spineIndex || (splSpineIndex == spineIndex && splElementIndex < elementIndex)) {
                    arrayForOne(splChunk, splElementIndex++, consumer);

                    if (splElementIndex == arrayLength(splChunk)) {
                        splElementIndex = 0;
                        ++splSpineIndex;
                        if (spine != null && splSpineIndex < spine.length)
                            splChunk = spine[splSpineIndex];
                    }
                    return true;
                }
                return false;
            }

            public void forEachRemaining(C consumer) {
                if (splSpineIndex < spineIndex || (splSpineIndex == spineIndex && splElementIndex < elementIndex)) {
                    int i = splElementIndex;
                    // completed chunks, if any
                    for (int sp = splSpineIndex; sp < spineIndex; sp++) {
                        A chunk = spine[sp];
                        arrayForEach(chunk, i, arrayLength(chunk), consumer);
                        i = 0;
                    }

                    arrayForEach(curChunk, i, elementIndex, consumer);

                    splSpineIndex = spineIndex;
                    splElementIndex = elementIndex;
                }
            }

            @Override
            public T_SPLITER trySplit() {
                if (splSpineIndex < spineIndex) {
                    T_SPLITER ret = arraySpliterator(spine[splSpineIndex], 0, arrayLength(spine[splSpineIndex]));
                    splChunk = spine[++splSpineIndex];
                    return ret;
                }
                else if (splSpineIndex == spineIndex) {
                    int t = (elementIndex - splElementIndex) / 2;
                    if (t == 0)
                        return null;
                    else {
                        T_SPLITER ret = arraySpliterator(curChunk, splElementIndex, t);
                        splElementIndex += t;
                        return ret;
                    }
                }
                else {
                    return null;
                }
            }
        }
    }

    /**
     * An ordered collection of {@code int} values.
     */
    static class OfInt extends SpinedBuffer.OfPrimitive<Integer, int[], IntConsumer> implements IntConsumer {
        OfInt() { }

        OfInt(int initialCapacity) {
            super(initialCapacity);
        }

        @Override
        public void forEach(Consumer<? super Integer> consumer) {
            if (consumer instanceof IntConsumer) {
                forEach((IntConsumer) consumer);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(), "{0} calling SpinedBuffer.OfInt.forEach(Consumer)");
                spliterator().forEachRemaining(consumer);
            }
        }

        @Override
        protected int[][] newArrayArray(int size) {
            return new int[size][];
        }

        @Override
        protected int[] newArray(int size) {
            return new int[size];
        }

        @Override
        protected int arrayLength(int[] array) {
            return array.length;
        }

        @Override
        protected void arrayForEach(int[] array, int from, int to, IntConsumer consumer) {
            for (int i = from; i < to; i++)
                consumer.accept(array[i]);
        }

        @Override
        public void accept(int i) {
            preAccept();
            curChunk[elementIndex++] = i;
        }

        public int get(long index) {
            int ch = chunkFor(index);
            if (spineIndex == 0 && ch == 0)
                return curChunk[(int) index];
            else
                return spine[ch][(int) (index-priorElementCount[ch])];
        }

        public int[] asIntArray() {
            return asPrimitiveArray();
        }

        @Override
        public PrimitiveIterator.OfInt iterator() {
            return Spliterators.iteratorFromSpliterator(spliterator());
        }

        public Spliterator.OfInt spliterator() {
            class Splitr extends BaseSpliterator<Spliterator.OfInt> implements Spliterator.OfInt {

                @Override
                void arrayForOne(int[] array, int index, IntConsumer consumer) {
                    consumer.accept(array[index]);
                }

                @Override
                Spliterator.OfInt arraySpliterator(int[] array, int offset, int len) {
                    return Arrays.spliterator(array, offset, offset+len);
                }
            };
            return new Splitr();
        }

        @Override
        public String toString() {
            int[] array = asIntArray();
            if (array.length < 200) {
                return String.format("%s[length=%d, chunks=%d]%s", getClass().getSimpleName(), array.length,
                                     spineIndex, Arrays.toString(array));
            }
            else {
                int[] array2 = Arrays.copyOf(array, 200);
                return String.format("%s[length=%d, chunks=%d]%s...", getClass().getSimpleName(), array.length,
                                     spineIndex, Arrays.toString(array2));
            }
        }
    }

    /**
     * An ordered collection of {@code long} values.
     */
    static class OfLong extends SpinedBuffer.OfPrimitive<Long, long[], LongConsumer> implements LongConsumer {
        OfLong() { }

        OfLong(int initialCapacity) {
            super(initialCapacity);
        }

        @Override
        public void forEach(Consumer<? super Long> consumer) {
            if (consumer instanceof LongConsumer) {
                forEach((LongConsumer) consumer);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(), "{0} calling SpinedBuffer.OfLong.forEach(Consumer)");
                spliterator().forEachRemaining(consumer);
            }
        }

        @Override
        protected long[][] newArrayArray(int size) {
            return new long[size][];
        }

        @Override
        protected long[] newArray(int size) {
            return new long[size];
        }

        @Override
        protected int arrayLength(long[] array) {
            return array.length;
        }

        @Override
        protected void arrayForEach(long[] array, int from, int to, LongConsumer consumer) {
            for (int i = from; i < to; i++)
                consumer.accept(array[i]);
        }

        @Override
        public void accept(long i) {
            preAccept();
            curChunk[elementIndex++] = i;
        }

        public long get(long index) {
            int ch = chunkFor(index);
            if (spineIndex == 0 && ch == 0)
                return curChunk[(int) index];
            else
                return spine[ch][(int) (index-priorElementCount[ch])];
        }

        public long[] asLongArray() {
            return asPrimitiveArray();
        }

        @Override
        public PrimitiveIterator.OfLong iterator() {
            return Spliterators.iteratorFromSpliterator(spliterator());
        }


        public Spliterator.OfLong spliterator() {
            class Splitr extends BaseSpliterator<Spliterator.OfLong> implements Spliterator.OfLong {
                @Override
                void arrayForOne(long[] array, int index, LongConsumer consumer) {
                    consumer.accept(array[index]);
                }

                @Override
                Spliterator.OfLong arraySpliterator(long[] array, int offset, int len) {
                    return Arrays.spliterator(array, offset, offset+len);
                }
            };
            return new Splitr();
        }

        @Override
        public String toString() {
            long[] array = asLongArray();
            if (array.length < 200) {
                return String.format("%s[length=%d, chunks=%d]%s", getClass().getSimpleName(), array.length,
                                     spineIndex, Arrays.toString(array));
            }
            else {
                long[] array2 = Arrays.copyOf(array, 200);
                return String.format("%s[length=%d, chunks=%d]%s...", getClass().getSimpleName(), array.length,
                                     spineIndex, Arrays.toString(array2));
            }
        }
    }

    /**
     * An ordered collection of {@code double} values.
     */
    static class OfDouble extends SpinedBuffer.OfPrimitive<Double, double[], DoubleConsumer> implements DoubleConsumer {
        OfDouble() { }

        OfDouble(int initialCapacity) {
            super(initialCapacity);
        }

        @Override
        public void forEach(Consumer<? super Double> consumer) {
            if (consumer instanceof DoubleConsumer) {
                forEach((DoubleConsumer) consumer);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(), "{0} calling SpinedBuffer.OfDouble.forEach(Consumer)");
                spliterator().forEachRemaining(consumer);
            }
        }

        @Override
        protected double[][] newArrayArray(int size) {
            return new double[size][];
        }

        @Override
        protected double[] newArray(int size) {
            return new double[size];
        }

        @Override
        protected int arrayLength(double[] array) {
            return array.length;
        }

        @Override
        protected void arrayForEach(double[] array, int from, int to, DoubleConsumer consumer) {
            for (int i = from; i < to; i++)
                consumer.accept(array[i]);
        }

        @Override
        public void accept(double i) {
            preAccept();
            curChunk[elementIndex++] = i;
        }

        public double get(long index) {
            int ch = chunkFor(index);
            if (spineIndex == 0 && ch == 0)
                return curChunk[(int) index];
            else
                return spine[ch][(int) (index-priorElementCount[ch])];
        }

        public double[] asDoubleArray() {
            return asPrimitiveArray();
        }

        @Override
        public PrimitiveIterator.OfDouble iterator() {
            return Spliterators.iteratorFromSpliterator(spliterator());
        }

        public Spliterator.OfDouble spliterator() {
            class Splitr extends BaseSpliterator<Spliterator.OfDouble> implements Spliterator.OfDouble {
                @Override
                void arrayForOne(double[] array, int index, DoubleConsumer consumer) {
                    consumer.accept(array[index]);
                }

                @Override
                Spliterator.OfDouble arraySpliterator(double[] array, int offset, int len) {
                    return Arrays.spliterator(array, offset, offset+len);
                }
            }
            return new Splitr();
        }

        @Override
        public String toString() {
            double[] array = asDoubleArray();
            if (array.length < 200) {
                return String.format("%s[length=%d, chunks=%d]%s", getClass().getSimpleName(), array.length,
                                     spineIndex, Arrays.toString(array));
            }
            else {
                double[] array2 = Arrays.copyOf(array, 200);
                return String.format("%s[length=%d, chunks=%d]%s...", getClass().getSimpleName(), array.length,
                                     spineIndex, Arrays.toString(array2));
            }
        }
    }
}

