package dunkyboy.util;

/**
 * Created by darmstrong on 2/10/17.
 */
public class ThreadsafeRunningAverage extends RunningAverage {

    public synchronized long addValue(long value) {
        return super.addValue(value);
    }

    public synchronized long get() {
        return super.get();
    }

    public synchronized long getCount() {
        return super.getCount();
    }
}
