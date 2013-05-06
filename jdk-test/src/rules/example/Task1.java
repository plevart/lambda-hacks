/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package rules.example;

import rules.Context;
import rules.RuleTask;

/**
 * @author peter
 */
public class Task1 extends RuleTask {
    @Override
    protected void execute(Context ctx) {
        matchAll(
            ctx.beans(Invoice.class),
            Invoice::isFiled,
            invoice -> {
                matchAll(
                    invoice::getItems,
                    item -> item.getType() == Invoice.Item.Type.SERVICE,
                    item -> {
                        System.out.println(invoice + ", " + item + "[SERVICE]: filed");
                    }
                );
                match(
                    () -> invoice,
                    Invoice::isPaid,
                    paidInvoice -> {
                        matchAll(
                            paidInvoice::getItems,
                            item -> {
                                System.out.println(invoice + ", " + item + ": filed & paid");
                            }
                        );
                    }
                );
            });
    }
}
