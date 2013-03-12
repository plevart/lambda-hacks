/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.io.*;
import java.util.function.IntBinaryOperator;

public class LambdaTest {

    interface MyIntBinaryOperator extends IntBinaryOperator {
    }

    static class A {
        static void test1() {
            System.out.println("\nA.test1()\n");

            IntBinaryOperator methRef = Math::min;
            System.out.println("       methRef: " + methRef);

            IntBinaryOperator serMethRef = (IntBinaryOperator & Serializable) Math::min;
            System.out.println("    serMethRef: " + serMethRef);

            IntBinaryOperator deserMethRef = deserialize(serialize(serMethRef));
            System.out.println("  deserMethRef: " + deserMethRef);

            MyIntBinaryOperator myMethRef = Math::min;
            System.out.println("     myMethRef: " + myMethRef);

            IntBinaryOperator lambda = (a, b) -> a <= b ? a : b;
            System.out.println("        lambda: " + lambda);

            IntBinaryOperator serLambda = (IntBinaryOperator & Serializable) (a, b) -> a <= b ? a : b;
            System.out.println("     serLambda: " + serLambda);

            IntBinaryOperator deserLambda = deserialize(serialize(serLambda));
            System.out.println("   deserLambda: " + deserLambda);
        }

        static void test2() {
            System.out.println("\nA.test2()\n");

            IntBinaryOperator methRef = Math::min;
            System.out.println("       methRef: " + methRef);

            IntBinaryOperator serMethRef = (IntBinaryOperator & Serializable) Math::min;
            System.out.println("    serMethRef: " + serMethRef);

            IntBinaryOperator deserMethRef = deserialize(serialize(serMethRef));
            System.out.println("  deserMethRef: " + deserMethRef);

            MyIntBinaryOperator myMethRef = Math::min;
            System.out.println("     myMethRef: " + myMethRef);

            IntBinaryOperator lambda = (a, b) -> a <= b ? a : b;
            System.out.println("        lambda: " + lambda);

            IntBinaryOperator serLambda = (IntBinaryOperator & Serializable) (a, b) -> a <= b ? a : b;
            System.out.println("     serLambda: " + serLambda);

            IntBinaryOperator deserLambda = deserialize(serialize(serLambda));
            System.out.println("   deserLambda: " + deserLambda);
        }
    }

    static class B {
        static void test1() {
            System.out.println("\nB.test1()\n");

            IntBinaryOperator methRef = Math::min;
            System.out.println("       methRef: " + methRef);

            IntBinaryOperator serMethRef = (IntBinaryOperator & Serializable) Math::min;
            System.out.println("    serMethRef: " + serMethRef);

            IntBinaryOperator deserMethRef = deserialize(serialize(serMethRef));
            System.out.println("  deserMethRef: " + deserMethRef);

            MyIntBinaryOperator myMethRef = Math::min;
            System.out.println("     myMethRef: " + myMethRef);

            IntBinaryOperator lambda = (a, b) -> a <= b ? a : b;
            System.out.println("        lambda: " + lambda);

            IntBinaryOperator serLambda = (IntBinaryOperator & Serializable) (a, b) -> a <= b ? a : b;
            System.out.println("     serLambda: " + serLambda);

            IntBinaryOperator deserLambda = deserialize(serialize(serLambda));
            System.out.println("   deserLambda: " + deserLambda);
        }

        static void test2() {
            System.out.println("\nB.test2()\n");

            IntBinaryOperator methRef = Math::min;
            System.out.println("       methRef: " + methRef);

            IntBinaryOperator serMethRef = (IntBinaryOperator & Serializable) Math::min;
            System.out.println("    serMethRef: " + serMethRef);

            IntBinaryOperator deserMethRef = deserialize(serialize(serMethRef));
            System.out.println("  deserMethRef: " + deserMethRef);

            MyIntBinaryOperator myMethRef = Math::min;
            System.out.println("     myMethRef: " + myMethRef);

            IntBinaryOperator lambda = (a, b) -> a <= b ? a : b;
            System.out.println("        lambda: " + lambda);

            IntBinaryOperator serLambda = (IntBinaryOperator & Serializable) (a, b) -> a <= b ? a : b;
            System.out.println("     serLambda: " + serLambda);

            IntBinaryOperator deserLambda = deserialize(serialize(serLambda));
            System.out.println("   deserLambda: " + deserLambda);
        }
    }

    static byte[] serialize(Object o) {
        ByteArrayOutputStream baos;
        try (
                ObjectOutputStream oos =
                        new ObjectOutputStream(baos = new ByteArrayOutputStream())
        ) {
            oos.writeObject(o);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    static <T> T deserialize(byte[] bytes) {
        try (
                ObjectInputStream ois =
                        new ObjectInputStream(new ByteArrayInputStream(bytes))
        ) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        A.test1();
        B.test1();
        A.test2();
        B.test2();
        A.test1();
        B.test1();
    }
}
