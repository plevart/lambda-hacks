/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package rules.example;

import java.util.List;

/**
 * @author peter
 */
public class Invoice extends Document {

    public boolean isPaid() {
        return false;
    }

    public static class Item {

        public enum Type { SERVICE, RESOURCE }

        public Type getType() {
            return null;
        }
    }

    public List<Item> getItems() {
        return null;
    }
}
