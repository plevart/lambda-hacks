/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author peter
 */
public class SerializedLambdaTest {

    public interface SerializableRunnable extends Runnable, Serializable {}

    public static void main(String[] args) throws Exception {

        SerializableRunnable r0 = () -> {System.out.println("HELLO");};

        ByteArrayOutputStream baos;
        try (
            ObjectOutputStream oos =
                new ObjectOutputStream(baos = new ByteArrayOutputStream())
        ) {
            oos.writeObject(r0);
        }

        Runnable r1;
        try (
            ObjectInputStream ois =
                new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))
        ) {
            r1 = (Runnable) ois.readObject();
        }

        try (
            ObjectOutputStream oos =
                new ObjectOutputStream(baos = new ByteArrayOutputStream())
        ) {
            oos.writeObject(r1);
        }

        Runnable r2;
        try (
            ObjectInputStream ois =
                new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))
        ) {
            r2 = (Runnable) ois.readObject();
        }

        System.out.println(r0);
        r0.run();
        System.out.println(r1);
        r1.run();
        System.out.println(r2);
        r2.run();
    }
}
