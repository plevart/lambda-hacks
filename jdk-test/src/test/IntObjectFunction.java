/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.util.function.BiFunction;

/**
 * @author peter
 */
public interface IntObjectFunction<T, R> {
    R apply(int i, T t);
}
