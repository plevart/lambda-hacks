/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package si.pele.dieharder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * @author peter.levart@gmail.com
 */
public class DieharderTest<RNG> implements Callable<String> {
    static final String headerMetaFormat =
        "%%s| %20s | %7s | %8s | %8s | %23s | %s";
    static final String resultFormat =
        "%s| %20s | %2d...%2d | %8d | %8d | %10.8f...%10.8f | %s";

    enum Assessment {PASSED, WEAK, FAILED}

    final String resultPrefix;
    final int testId;
    final List<String> opts;
    final byte[] buffer;
    final RNG rng;
    final BiConsumer<byte[], RNG> bufferFiller;

    public DieharderTest(String resultPrefix,
                         int testId, List<String> opts,
                         byte[] buffer, RNG rng,
                         BiConsumer<byte[], RNG> bufferFiller
    ) {
        this.resultPrefix = resultPrefix;
        this.testId = testId;
        this.opts = opts;
        this.buffer = buffer;
        this.rng = rng;
        this.bufferFiller = bufferFiller;
    }

    @Override
    public String call() throws Exception {

        List<String> cmd = new ArrayList<>(Arrays.asList(
            "dieharder", "-g", "200", "-d", String.valueOf(testId))
        );
        if (opts != null) cmd.addAll(opts);

        Process process = new ProcessBuilder()
            .command(cmd)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start();

        List<String> results = new ArrayList<>();
        OutputStream out = process.getOutputStream();

        try (InputStream in = process.getInputStream()) {
            for (; ; ) {
                bufferFiller.accept(buffer, rng);
                try {
                    out.write(buffer);
                } catch (IOException e) {
                    // when dieharder exits during write,
                    // we get broken pipe or stream closed exception...
                    if (e.getMessage().equals("Broken pipe") ||
                        e.getMessage().equals("Stream closed")) {
                        break;
                    } else { // re-throw
                        throw e;
                    }
                }
            }

            BufferedReader rin = new BufferedReader(new InputStreamReader(in, "ASCII"));
            String line;
            while ((line = rin.readLine()) != null) {
                if (line.startsWith("#==")) {
                    results.clear();
                } else {
                    results.add(line);
                }
            }
        } finally {
            try {
                // sometimes when writing to pipe throws IOException
                // with "Broken pipe" or "Stream closed" the stream is
                // already closed, so the following close will throw
                // IOException: "Already closed" and we ignore it.
                out.close();
            } catch (IOException ignore) {}
        }

        if (results.isEmpty()) {
            return String.format("%s| no result", resultPrefix);
        }

        String testName = null;
        int ntupMin = 0, ntupMax = 0;
        long tsamplesSum = 0L, psamplesSum = 0L;
        double pvalueMin = 0, pvalueMax = 0;
        final int[] assHistogram = new int[3];
        boolean first = true;
        for (String result : results) {
            String[] fields = result.trim().split("\\s*\\|\\s*");
            if (fields.length != 6) {
                return String.format("%s| invalid result: %s", resultPrefix, result);
            }
            int ntup = Integer.parseInt(fields[1]);
            double pvalue = Double.parseDouble(fields[4]);
            if (first) {
                first = false;
                testName = fields[0];
                ntupMin = ntupMax = ntup;
                pvalueMin = pvalueMax = pvalue;
            } else {
                ntupMin = Math.min(ntupMin, ntup);
                ntupMax = Math.max(ntupMax, ntup);
                pvalueMin = Math.min(pvalueMin, pvalue);
                pvalueMax = Math.max(pvalueMax, pvalue);
            }
            tsamplesSum += Long.parseLong(fields[2]);
            psamplesSum += Long.parseLong(fields[3]);
            Assessment ass = Assessment.valueOf(fields[5]);
            assHistogram[ass.ordinal()]++;
        }
        Assessment[] assessments = Assessment.values();
        Arrays.sort(assessments, (a1, a2) -> assHistogram[a2.ordinal()] - assHistogram[a1.ordinal()]);
        StringBuilder assBuilder = new StringBuilder();
        for (Assessment ass : assessments) {
            if (assHistogram[ass.ordinal()] > 0) {
                if (assBuilder.length() > 0) assBuilder.append(", ");
                assBuilder.append(assHistogram[ass.ordinal()]).append("*").append(ass);
            }
        }

        return String.format(
            resultFormat,
            resultPrefix,
            testName,
            ntupMin, ntupMax,
            tsamplesSum / results.size(), psamplesSum / results.size(),
            pvalueMin, pvalueMax,
            assBuilder.toString()
        );
    }

    public static class Header implements Callable<String> {
        static final String headerFormat = String.format(headerMetaFormat,
            "test name", "ntup", "tsamples", "psamples", "pvalue", "assessment(s)");

        final String headerPrefix;

        public Header(String headerPrefix) {
            this.headerPrefix = headerPrefix;
        }

        @Override
        public String call() throws Exception {
            String header = String.format(headerFormat, headerPrefix);
            String delimiter = header.replaceAll("[^\\|]", "-");
            return delimiter + "\n" + header + "\n" + delimiter;
        }
    }
}
