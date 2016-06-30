package dunkyboy.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;

import static java.text.NumberFormat.getNumberInstance;
import static java.util.Locale.US;
import static java.util.concurrent.TimeUnit.NANOSECONDS;


/**
 * Created by Duncan on 6/29/16.
 */
public class MemoryAllocator {

    public static void main(String[] args) {
        new MemoryAllocator( new SequentialByteArrayBuilder(), 5000 ).start();
    }


    public interface ByteArrayBuilder {
        byte[] build(int size);
        long getCount();
    }

    static abstract class AbstractByteArrayBuilder implements ByteArrayBuilder {

        protected long count = 0;

        @Override
        public long getCount() {
            return count;
        }
    }

    static class IdenticalByteArrayBuilder extends AbstractByteArrayBuilder {

        private final byte theByte;

        IdenticalByteArrayBuilder(byte theByte) {
            this.theByte = theByte;
        }

        @Override
        public byte[] build(final int size) {
            byte[] bytes = new byte[size];
            for (int i = 0; i < bytes.length; i++)
                bytes[i] = theByte;
            count++;
            return bytes;
        }

        @Override
        public long getCount() {
            return 0;
        }
    }

    static class SequentialByteArrayBuilder extends AbstractByteArrayBuilder {

        private byte theByte = 0;

        @Override
        public byte[] build(final int size) {
            byte[] bytes = new byte[size];
            for (int i = 0; i < bytes.length; i++)
                bytes[i] = theByte++;
            count++;
            return bytes;
        }
    }

    static class RandomByteArrayBuilder extends AbstractByteArrayBuilder {

        private final Random rand = new Random();

        @Override
        public byte[] build(final int size) {
            byte[] bytes = new byte[size];
            rand.nextBytes(bytes);
            count++;
            return bytes;
        }
    }

    static class AccumulatingByteArrayBuilder implements ByteArrayBuilder {

        private final List<byte[]> byteArrays = new LinkedList<>();  // growing should cause steady heap increase, not jerky like ArrayList

        private final ByteArrayBuilder builder;

        AccumulatingByteArrayBuilder(ByteArrayBuilder builder) {
            this.builder = builder;
        }

        @Override
        public byte[] build(final int size) {
            byte[] bytes = builder.build(size);
            byteArrays.add(bytes);
            return bytes;
        }

        @Override
        public long getCount() {
            return byteArrays.size();
        }
    }


    private final ByteArrayBuilder byteArrayBuilder;

    private final int byteArraySize;

    private long bytesAllocated;  // force compiler to not optimize away my byte[]s!

    public MemoryAllocator(ByteArrayBuilder byteArrayBuilder, int byteArraySize) {
        this.byteArrayBuilder = byteArrayBuilder;
        this.byteArraySize = byteArraySize;
    }

    public void start() {
        System.out.println( "Generating byte arrays" +
            "\n  builder:    " + byteArrayBuilder.getClass().getSimpleName() +
            "\n  array size: " + byteArraySize +
            "\n  heap max:   " + humanReadableByteCount(Runtime.getRuntime().maxMemory())
        );

        printGcInfo();

        System.out.println();

        long startTimeNanos = System.nanoTime();

        long currentSecond = 0;

        while (true) {
            byte[] bytes = byteArrayBuilder.build(byteArraySize);
            bytesAllocated += bytes.length;

            long arrayCount = byteArrayBuilder.getCount();

            long elapsedNanos = System.nanoTime() - startTimeNanos;
            long elapsedSecs = NANOSECONDS.toSeconds(elapsedNanos);
            if (elapsedSecs > currentSecond) {
                currentSecond = elapsedSecs;

                double elapsedSeconds = (double) (elapsedNanos / 1000000) / 1000.0d;
                double throughput = bytesAllocated / elapsedSeconds;

                System.out.println(
                    "arrays built: " + getNumberInstance(US).format(arrayCount) + ", " +
                    "elapsed: "      + elapsedSeconds + " secs, " +
                    "allocated: "    + humanReadableByteCount(bytesAllocated) + ", " +
                    "heap: "         + humanReadableByteCount(Runtime.getRuntime().totalMemory()) + ", " +
                    "throughput: "   + humanReadableByteCount(throughput) + " / sec"
                );
            }
        }
    }

    private static void printGcInfo() {
        System.out.println("  garbage collectors:");
        for ( GarbageCollectorMXBean gcMxBean : ManagementFactory.getGarbageCollectorMXBeans() )
            System.out.println( "    " + gcMxBean.getName() + ": " + Arrays.toString(gcMxBean.getMemoryPoolNames()) );
    }

    private static String humanReadableByteCount(long bytes) {
        int unit = 1000;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "kMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private static String humanReadableByteCount(double bytes) {
        int unit = 1000;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "kMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
