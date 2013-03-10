package test;

import java.io.Serializable;

/**
 */
public class LambdasTest {

    interface MarkerX {
        default int hash() {
            return hashCode();
        }
    }

    public static void main(String[] args) {
        Runnable sr1 = (Runnable & Serializable & MarkerX) () -> {System.out.println("2");};
        System.out.println("sr1: " + sr1);
        System.out.println(((MarkerX) sr1).hash());
    }
}
