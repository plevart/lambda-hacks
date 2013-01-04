package map;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created with IntelliJ IDEA.
 * User: peter
 * Date: 1/3/13
 * Time: 10:26 AM
 * To change this template use File | Settings | File Templates.
 */
public class MapTest {

    static void testCompute(Map<Integer, String> map) {
        System.out.println("\n" + map.getClass().getName() + ":\n");
        System.out.println(" before: " + map);
        System.out.println("compute: " + map.compute(1, (k, v) -> v == null ? "x" : v + "y"));
        System.out.println(" middle: " + map);
        System.out.println("compute: " + map.compute(1, (k, v) -> v == null ? "x" : v + "y"));
        System.out.println("  after: " + map);
    }

    static void testMerge(Map<Integer, String> map) {
        System.out.println("\n" + map.getClass().getName() + ":\n");
        System.out.println(" before: " + map);
        System.out.println("  merge: " + map.merge(1, "x", (oldV, v) -> oldV + "y"));
        System.out.println(" middle: " + map);
        System.out.println("  merge: " + map.merge(1, "x", (oldV, v) -> oldV + "y"));
        System.out.println("  after: " + map);
    }

    public static void main(String[] args) {
        testCompute(new HashMap<>());
        testCompute(new ConcurrentHashMap<>());
        testMerge(new HashMap<>());
        testMerge(new ConcurrentHashMap<>());
    }
}
