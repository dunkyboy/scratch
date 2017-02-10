package dunkyboy.util;

/**
 * Created by darmstrong on 2/10/17.
 */
public class ThreadsafeRunningAverage {
    private long valueCount = 0;
    private long runningAverage = 0;

    public synchronized long addValue(long value) {

        long currentTotal = valueCount * runningAverage;
        long newTotal = currentTotal + value;

        valueCount++;
        runningAverage = newTotal / valueCount;

        return runningAverage;
    }

    public synchronized long get() {
        return runningAverage;
    }

    public synchronized long getCount() {
        return valueCount;
    }
}
