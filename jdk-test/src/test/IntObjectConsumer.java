/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.util.function.BiConsumer;

/**
 * @author peter
 */
public interface IntObjectConsumer<T>  {
    void accept(int i, T t);
}
