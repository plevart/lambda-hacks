/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package rules;


import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author peter
 */
public abstract class RuleTask {

    protected abstract void execute(Context ctx);

    protected <T> void match(Supplier<T> beanSupplier, Consumer<? super T> action) {
    }

    protected <T> void match(Supplier<T> beanSupplier, Predicate<? super T> cond, Consumer<? super T> action) {
    }

    protected <T> void matchAll(Supplier<? extends Iterable<T>> beans, Consumer<? super T> action) {
    }

    protected <T> void matchAll(Supplier<? extends Iterable<T>> beans, Predicate<? super T> cond, Consumer<? super T> action) {
    }
}
