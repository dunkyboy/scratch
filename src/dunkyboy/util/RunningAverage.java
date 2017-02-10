package dunkyboy.util;

/**
 * Created by darmstrong on 2/10/17.
 */
public class RunningAverage {
    private long valueCount = 0;
    private long runningAverage = 0;

    public long addValue(long value) {

        long currentTotal = valueCount * runningAverage;
        long newTotal = currentTotal + value;

        valueCount++;
        runningAverage = newTotal / valueCount;

        return runningAverage;
    }

    public long get() {
        return runningAverage;
    }

    public long getCount() {
        return valueCount;
    }
}
