/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import si.pele.microbench.SizeOf;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public class SizeOfTest {
//    static final Field classValueMapField;
//
//    static {
//        try {
//            classValueMapField = Class.class.getDeclaredField("classValueMap");
//            classValueMapField.setAccessible(true);
//        } catch (NoSuchFieldException e) {
//            throw (Error) new NoSuchFieldError(e.getMessage()).initCause(e);
//        }
//    }
//
//    static Object getClassValueMap(Class<?> clazz) {
//        try {
//            return classValueMapField.get(clazz);
//        } catch (IllegalAccessException e) {
//            throw (Error) new IllegalAccessError(e.getMessage()).initCause(e);
//        }
//    }

    static class Test {
        static void test1() {
            Runnable r = () -> {
                System.out.println("test1");
            };
            r.run();
        }
        static void test2() {
            Runnable r = () -> {
                System.out.println("test2");
            };
            r.run();
        }
        static void test3() {
            Runnable r = Test::test1;
            r.run();
        }
        static void test4() {
            Runnable r = Test::test2;
            r.run();
        }
    }

    static void compareSizes() {
        ConcurrentHashMap<String, String> chm = new ConcurrentHashMap<>(8);
        SizeOf sizeOf = new SizeOf(SizeOf.Visitor.NULL);
        for (int i = 0; i < 10; i++) {
            System.out.print(sizeOf.deepSizeOf(chm) + ", ");
            String k = String.valueOf(i).intern();
            chm.put(k, k);
        }
        System.out.println(sizeOf.deepSizeOf(chm));

        System.out.println("*************************");

        long size0 = sizeOf.deepSizeOf(Test.class);
        ClassValue<?>[] cvs = new ClassValue[10];
        for (int i = 0; i < cvs.length; i++) {
            System.out.print((sizeOf.deepSizeOf(Test.class) - size0) + ", ");
            cvs[i] = new ClassValue<String>() {
                @Override
                protected String computeValue(Class<?> type) {
                    return "1";
                }
            };
            cvs[i].get(Test.class);
        }
        System.out.println((sizeOf.deepSizeOf(Test.class) - size0));
    }

    public static void main(String[] args) {
        SizeOf sizeOf = new SizeOf(SizeOf.Visitor.STDOUT);
        sizeOf.deepSizeOf(Test.class);
        Test.test1();
        sizeOf.deepSizeOf(Test.class);
        Test.test2();
        sizeOf.deepSizeOf(Test.class);
        Test.test3();
        sizeOf.deepSizeOf(Test.class);
        Test.test4();
        sizeOf.deepSizeOf(Test.class);
    }
}
