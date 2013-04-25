/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public class CHMSerialization {

    static void serialize(ConcurrentHashMap<?, ?> chm, File file) throws IOException {
        try (
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos)
        ) {
            oos.writeObject(chm);
        }
    }

    @SuppressWarnings("unchecked")
    static <K, V> ConcurrentHashMap<K, V> deserialize(File file) throws IOException, ClassNotFoundException {
        try (
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis)
        ) {
            return (ConcurrentHashMap<K, V>) ois.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw invalidUsage();
        }
        File[] files = new File[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            files[i-1] = new File(args[i]).getAbsoluteFile();
        }
        ConcurrentHashMap<String, Integer> chm;
        switch (args[0]) {
            case "w":
                chm = new ConcurrentHashMap<>();
                chm.put("a", 1);
                chm.put("b", 2);
                chm.put("c", 3);
                serialize(chm, files[0]);
                System.out.println("Written: " + chm + " to: " + files[0]);
                break;
            case "r":
                for (File file : files) {
                    chm = deserialize(file);
                    System.out.println("Read: " + chm + " from: " + file);
                }
                break;
            default:
                throw invalidUsage();
        }
    }

    private static IllegalArgumentException invalidUsage() {
        return new IllegalArgumentException("Usage: CHMSerialization [w|r] filename");

    }
}
