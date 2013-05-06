/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package rules;

import java.util.function.Supplier;

/**
 * @author peter
 */
public class Context {

    public <T> Supplier<Iterable<T>> beans(Class<T> beanType) {
        return null;
    }
}
