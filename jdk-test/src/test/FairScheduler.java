/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.util.Arrays;

/**
* @author peter
*/
public class FairScheduler
{
    private final int[] priorities, accumulated;
    private final boolean[] runnable;

    public FairScheduler(int... priorities)
    {
        this.priorities = priorities.clone();
        this.accumulated = priorities.clone();
        this.runnable = new boolean[priorities.length];
        Arrays.fill(runnable, true);
    }

    public boolean isRunnable(int index) {
        return runnable[index];
    }

    public void setRunnable(int index, boolean runnable) {
        this.runnable[index] = runnable;
    }

    /**
     * @return the index of next queue to poll
     */
    public int next() {
        int maxIndex = 0;
        int minAcc, maxAcc = minAcc = accumulated[0];

        for (int i = 1; i < accumulated.length; i++) {
            int acc = accumulated[i];
            if (acc < minAcc) {
                minAcc = acc;
            }
            if (acc > maxAcc) {
                maxAcc = acc;
                maxIndex = i;
            }
        }

        for (int i = 0; i < accumulated.length; i++) {
            accumulated[i] += (i != maxIndex && runnable[i] ? priorities[i] : 0); // - minAcc;
        }

        return maxIndex;
    }
}
