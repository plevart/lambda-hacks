package org.openjdk.tests.java.util;

import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

/**
 * Tests for default methods in java.util.Map and for overridden methods in java.util.HashMap
 */
public class MapDefaultMethodsTest {

    static void testCompute(Map<Integer, String> map) {
        BiFunction<Integer, String, String> remap =
            (k, v) -> v == null ? "x" + k : (v.length() < 6 ? v + "y" + k : null);

        assertEquals(map.size(), 0);
        assertSame(map.compute(1, (k, v) -> null), null);
        assertEquals(map.size(), 0);
        assertEquals(map.compute(1, remap), "x1");
        assertEquals(map.get(1), "x1");
        assertEquals(map.compute(1, remap), "x1y1");
        assertEquals(map.get(1), "x1y1");
        assertEquals(map.compute(1, remap), "x1y1y1");
        assertEquals(map.get(1), "x1y1y1");
        assertSame(map.compute(1, remap), null);
        assertEquals(map.size(), 0);
    }

    @Test
    public void testCompute() {
        testCompute(createOldHashMap());
        testCompute(createNewHashMap());
    }

    static void testMerge(Map<Integer, String> map) {
        BiFunction<String, String, String> remap =
            (oldV, v) -> oldV.length() < 3 ? oldV + v : null;

        assertEquals(map.size(), 0);
        assertSame(map.merge(1, null, (oldV, v) -> null), null);
        assertEquals(map.size(), 0);
        assertEquals(map.merge(1, "x", remap), "x");
        assertEquals(map.get(1), "x");
        assertEquals(map.merge(1, "y", remap), "xy");
        assertEquals(map.get(1), "xy");
        assertEquals(map.merge(1, "z", remap), "xyz");
        assertEquals(map.get(1), "xyz");
        assertSame(map.merge(1, "q", remap), null);
        assertEquals(map.size(), 0);
    }

    @Test
    public void testMerge() {
        testMerge(createOldHashMap());
        testMerge(createNewHashMap());
    }

    static Map<Integer, String> createNewHashMap() {
        return new HashMap<>();
    }

    static Map<Integer, String> createOldHashMap() {
        return new ConcurrentHashMap<>();
    }
}
