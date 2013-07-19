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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * An interface to {@code dieharder} test tool in the form of a {@link Callable} implementation.
 */
public class DieharderTest<RNG, RNGINFO> implements Callable<DieharderTest.Results<RNGINFO>> {

    final int testId;
    final List<String> opts;
    final RNGINFO rngInfo;
    final byte[] buffer;
    final RNG rng;
    final BiConsumer<RNG, byte[]> bufferFiller;

    public DieharderTest(int testId, List<String> opts,
                         RNGINFO rngInfo,
                         RNG rng, byte[] buffer,
                         BiConsumer<RNG, byte[]> bufferFiller
    ) {
        this.testId = testId;
        this.opts = opts;
        this.rngInfo = rngInfo;
        this.buffer = buffer;
        this.rng = rng;
        this.bufferFiller = bufferFiller;
    }

    @Override
    public Results<RNGINFO> call() throws Exception {

        List<String> cmd = new ArrayList<>(Arrays.asList(
            "dieharder", "-g", "200", "-d", String.valueOf(testId))
        );
        if (opts != null) cmd.addAll(opts);

        Process process = new ProcessBuilder()
            .command(cmd)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start();

        List<String> rawResults = new ArrayList<>(1);
        OutputStream out = process.getOutputStream();

        try (InputStream in = process.getInputStream()) {
            for (; ; ) {
                bufferFiller.accept(rng, buffer);
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
                    rawResults.clear();
                } else {
                    rawResults.add(line);
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

        List<Result> results = new ArrayList<>(rawResults.size());
        for (String rawResult : rawResults) {
            String[] fields = rawResult.trim().split("\\s*\\|\\s*");
            try {
                if (fields.length != 6) {
                    throw new Exception("Invalid result from dieharder tool: " + rawResult);
                }
                results.add(new Result(
                    fields[0],
                    Integer.parseInt(fields[1]),
                    Long.parseLong(fields[2]),
                    Long.parseLong(fields[3]),
                    Double.parseDouble(fields[4]),
                    Assessment.valueOf(fields[5]),
                    rawResult
                ));
            } catch (Exception exception) {
                results.add(new Result(rawResult, exception));
            }
        }

        return new Results<>(rngInfo, testId, results);
    }

    public enum Assessment {PASSED, WEAK, FAILED}

    public static class Result {

        public final String testName;
        public final int ntup;
        public final long tsamples, psamples;
        public final double pvalue;
        public final Assessment assessment;
        public final String rawResult;
        public final Exception exception;

        public Result(String testName, int ntup, long tsamples, long psamples, double pvalue, Assessment assessment, String rawResult) {
            this.testName = testName;
            this.ntup = ntup;
            this.tsamples = tsamples;
            this.psamples = psamples;
            this.pvalue = pvalue;
            this.assessment = assessment;
            this.rawResult = rawResult;
            this.exception = null;
        }

        public Result(String rawResult, Exception exception) {
            this.testName = "*invalid result*";
            this.ntup = 0;
            this.tsamples = 0L;
            this.psamples = 0L;
            this.pvalue = 0d;
            this.assessment = Assessment.WEAK;
            this.rawResult = rawResult;
            this.exception = exception;
        }
    }

    public static class Results<RNGINFO> {

        /**
         * a default format that can be used with {@link #format(String)}
         */
        public static final String FORMAT =
            "%3d | %20s | %2d...%2d | %8d | %8d | %10.8f...%10.8f | %s";

        /**
         * a header string that goes with {@link #FORMAT}
         */
        public static final String HEADER = String.format(
            "%3s | %20s | %7s | %8s | %8s | %23s | %s",
            "Tid", "test name", "ntup", "tsamples", "psamples", "pvalue", "assessment(s)");

        /**
         * a header delimiter string that goes with {@link #HEADER}
         */
        public static final String HEADER_DELIMITER = HEADER.replaceAll("[^\\|]", "-");

        public final RNGINFO rngInfo;
        public final int testId;
        public final List<Result> testResults;
        // aggregation
        public final String testName;
        public final int ntupMin, ntupMax;
        public final long tsamplesAvg, psamplesAvg;
        public final double pvalueMin, pvalueMax;
        public final String assessments;
        public final Exception exception;

        // private
        private final Assessment[] orderedAssessments = Assessment.values(); // ordered by decreasing count
        private final int[] assHistogram = new int[orderedAssessments.length]; // indexed by Assessment.ordinal()

        public Results(RNGINFO rngInfo, int testId, List<Result> testResults) {
            this.rngInfo = rngInfo;
            this.testId = testId;
            this.testResults = Collections.unmodifiableList(testResults);

            String testName = "*no results*";
            int ntupMin = 0, ntupMax = 0;
            long tsamplesSum = 0L, psamplesSum = 0L;
            double pvalueMin = 0, pvalueMax = 0;
            Exception exception = null;

            int okResultCount = 0;
            for (Result result : testResults) {
                if (result.exception != null) {
                    if (exception == null) {
                        exception = result.exception;
                    } else {
                        exception.addSuppressed(result.exception);
                    }
                } else {
                    if (okResultCount == 0) {
                        testName = result.testName;
                        ntupMin = ntupMax = result.ntup;
                        pvalueMin = pvalueMax = result.pvalue;
                    } else {
                        ntupMin = Math.min(ntupMin, result.ntup);
                        ntupMax = Math.max(ntupMax, result.ntup);
                        pvalueMin = Math.min(pvalueMin, result.pvalue);
                        pvalueMax = Math.max(pvalueMax, result.pvalue);
                    }
                    okResultCount++;
                    tsamplesSum += result.tsamples;
                    psamplesSum += result.psamples;
                    assHistogram[result.assessment.ordinal()]++;
                }
            }

            this.testName = testName;
            this.ntupMin = ntupMin;
            this.ntupMax = ntupMax;
            this.tsamplesAvg = okResultCount > 0 ? tsamplesSum / okResultCount : 0L;
            this.psamplesAvg = okResultCount > 0 ? psamplesSum / okResultCount : 0L;
            this.pvalueMin = pvalueMin;
            this.pvalueMax = pvalueMax;

            Arrays.sort(orderedAssessments, (a1, a2) -> assHistogram[a2.ordinal()] - assHistogram[a1.ordinal()]);
            StringBuilder assBuilder = new StringBuilder();
            for (Assessment ass : orderedAssessments) {
                if (assHistogram[ass.ordinal()] > 0) {
                    if (assBuilder.length() > 0) assBuilder.append(", ");
                    assBuilder.append(assHistogram[ass.ordinal()]).append("*").append(ass);
                }
            }
            this.assessments = assBuilder.toString();

            this.exception = exception;
        }

        public Assessment getOrderedAssessment(int index) {
            return orderedAssessments[index];
        }

        public Assessment[] getOrderedAssessments() {
            return orderedAssessments.clone();
        }

        public int getAssessmentOccurrences(Assessment assessment) {
            return assHistogram[assessment.ordinal()];
        }

        /**
         * Formats according to specified format the following arguments:
         * <pre>
         *    field name   typical format specifier
         *
         *    {@link #testId}       %1$3d
         *    {@link #testName}     %2$20s
         *    {@link #ntupMin}      %3$2d
         *    {@link #ntupMax}      %4$2d
         *    {@link #tsamplesAvg}  %5$8d
         *    {@link #psamplesAvg}  %6$8d
         *    {@link #pvalueMin}    %7$10.8f
         *    {@link #pvalueMax}    %8$10.8f
         *    {@link #assessments}  %9$s
         * </pre>
         *
         * @param format format to use
         * @return formatted string
         */
        public String format(String format) {
            return String.format(
                format,
                testId,     // 1$
                testName,   // 2$
                ntupMin,    // 3$
                ntupMax,    // 4$
                tsamplesAvg,// 5$
                psamplesAvg,// 6$
                pvalueMin,  // 7$
                pvalueMax,  // 8$
                assessments // 9$
            );
        }

        /**
         * @return the result of {@link #format(String) format}({@link #FORMAT})
         */
        @Override
        public String toString() {
            return format(FORMAT);
        }
    }
}
