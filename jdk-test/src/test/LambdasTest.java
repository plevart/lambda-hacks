package test;

import java.io.Serializable;

/**
 */
public class LambdasTest {

    public interface SerializableRunnable extends Serializable, Runnable {}

    void doIt() {
        System.out.println("doIt()");
    }

    public static void main(String[] args) {
        Runnable r = () -> {System.out.println("1");};
        SerializableRunnable sr = () -> {System.out.println("2");};

        final LambdasTest t = new LambdasTest();

        Runnable r2 = () -> {t.doIt();};
        SerializableRunnable sr2 = () -> {t.doIt();};
    }
}
