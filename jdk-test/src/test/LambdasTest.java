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
        SerializableRunnable sr1 = () -> {System.out.println("2");};
        System.out.println("sr1: " + sr1);
        System.out.println("---------");

        final LambdasTest t = new LambdasTest();

        SerializableRunnable sr2 = () -> {t.doIt();};
        System.out.println("sr2: " + sr2);
        System.out.println("---------");

        SerializableRunnable sr3 = t::doIt;
        System.out.println("sr3: " + sr3);
        System.out.println("---------");
    }
}
