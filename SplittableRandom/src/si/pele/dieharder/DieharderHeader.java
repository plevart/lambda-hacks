/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package si.pele.dieharder;

import java.util.concurrent.Callable;

/**
 * @author peter.levart@gmail.com
 */
public class DieharderHeader implements Callable<String> {
    static final String headerFormat = String.format(DieharderTest.headerMetaFormat,
        "test name", "ntup", "tsamples", "psamples", "pvalue", "assessment(s)");

    final String headerPrefix;

    public DieharderHeader(String headerPrefix) {
        this.headerPrefix = headerPrefix;
    }

    @Override
    public String call() throws Exception {
        String header = String.format(headerFormat, headerPrefix);
        String delimiter = header.replaceAll("[^\\|]", "-");
        return delimiter + "\n" + header + "\n" + delimiter;
    }
}
