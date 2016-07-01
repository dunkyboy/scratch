package dunkyboy.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.text.NumberFormat.getNumberInstance;
import static java.util.Locale.US;
import static java.util.concurrent.TimeUnit.NANOSECONDS;


/**
 * Created by Duncan on 6/29/16.
 */
public class MemoryAllocator {

    public static void main(String[] args) {
//        new MemoryAllocator( new SequentialByteArrayBuilder(), 5000 ).start();
        new MemoryAllocator(
            () ->  // one per thread
                new NonThreadsafeRollingBufferAccumulatingByteArrayBuilder(
                    new SequentialByteArrayBuilder(),
                    1000000
                )
            ,
            50
        ).start();
    }

    @FunctionalInterface
    public interface ArrayBuilderFactory {
        ByteArrayBuilder getBuilder();
    }

    public interface ByteArrayBuilder {
        byte[] build(int size);
        long getCount();
    }

    static abstract class AbstractByteArrayBuilder implements ByteArrayBuilder {

        protected final AtomicLong count = new AtomicLong();

        @Override
        public long getCount() {
            return count.get();
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
            count.incrementAndGet();
            return bytes;
        }
    }

    static class SequentialByteArrayBuilder extends AbstractByteArrayBuilder {

        private byte theByte = 0;

        @Override
        public byte[] build(final int size) {
            byte[] bytes = new byte[size];
            for (int i = 0; i < bytes.length; i++)
                bytes[i] = theByte++;
            count.incrementAndGet();
            return bytes;
        }
    }

    static class RandomByteArrayBuilder extends AbstractByteArrayBuilder {

        private final Random rand = new Random();

        @Override
        public byte[] build(final int size) {
            byte[] bytes = new byte[size];
            rand.nextBytes(bytes);
            count.incrementAndGet();
            return bytes;
        }
    }

    static class AccumulatingByteArrayBuilder implements ByteArrayBuilder {

        private final Queue<byte[]> byteArrays = new ConcurrentLinkedQueue<>();  // basically a concurrent LinkedList

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

    static class RollingBufferAccumulatingByteArrayBuilder implements ByteArrayBuilder {

        private final Queue<byte[]> byteArrays = new ConcurrentLinkedQueue<>();  // basically a concurrent LinkedList

        private final ByteArrayBuilder builder;

        private final int maxSize;

        RollingBufferAccumulatingByteArrayBuilder(ByteArrayBuilder builder, int maxSize) {
            this.builder = builder;
            this.maxSize = maxSize;
        }

        @Override
        public byte[] build(final int size) {
            byte[] bytes = builder.build(size);
            byteArrays.add(bytes);

            while (byteArrays.size() > maxSize)
                byteArrays.remove();  // racy, but just needs to be "close enough", so don't care

            return bytes;
        }

        @Override
        public long getCount() {
            return builder.getCount();
        }
    }

    static class NonThreadsafeRollingBufferAccumulatingByteArrayBuilder implements ByteArrayBuilder {

        private final List<byte[]> byteArrays = new LinkedList<>();

        private final ByteArrayBuilder builder;

        private final int maxSize;

        NonThreadsafeRollingBufferAccumulatingByteArrayBuilder(ByteArrayBuilder builder, int maxSize) {
            this.builder = builder;
            this.maxSize = maxSize;
        }

        @Override
        public byte[] build(final int size) {
            byte[] bytes = builder.build(size);
            byteArrays.add(bytes);

            while (byteArrays.size() > maxSize)
                byteArrays.remove(0);

            return bytes;
        }

        @Override
        public long getCount() {
            return builder.getCount();
        }
    }


    private final ArrayBuilderFactory byteArrayBuilders;

    private final int byteArraySize;

    private final AtomicLong bytesAllocated = new AtomicLong();

    public MemoryAllocator(ArrayBuilderFactory byteArrayBuilders, int byteArraySize) {
        this.byteArrayBuilders = byteArrayBuilders;
        this.byteArraySize = byteArraySize;
    }

    public void start() {

        final int threadCount = Runtime.getRuntime().availableProcessors();

        System.out.println( "Generating byte arrays in " + threadCount + " threads" +
            "\n  builder:    " + byteArrayBuilders.getBuilder().getClass().getSimpleName() +
            "\n  array size: " + byteArraySize +
            "\n  heap max:   " + humanReadableByteCount(Runtime.getRuntime().maxMemory())
        );

        printGcInfo();
        System.out.println();


        final long startTimeNanos = System.nanoTime();

        final Lock printLock = new ReentrantLock();  // only one thread should print

        for (int i = 0; i < threadCount; i++) {
            new Thread("AllocatorThread-"+i) {
                @Override
                public void run() {

                    final boolean isPrinting = printLock.tryLock();
                    System.out.println("[" + Thread.currentThread().getName() + "] initialized - I am " + (isPrinting?"":"not ") + "printing");

                    if (isPrinting)
                        System.out.println();

                    final ByteArrayBuilder builder = byteArrayBuilders.getBuilder();  // one builder per thread

                    long currentSecond = 0;
                    while (true) {
                        byte[] bytes = builder.build(byteArraySize);
                        long currentBytesAllocated = bytesAllocated.addAndGet(bytes.length);

                        long arrayCount = builder.getCount();

                        long elapsedNanos = System.nanoTime() - startTimeNanos;
                        long elapsedSecs = NANOSECONDS.toSeconds(elapsedNanos);
                        if (elapsedSecs > currentSecond) {
                            currentSecond = elapsedSecs;

                            if (isPrinting) {
                                double elapsedSecondsExact = (double) (elapsedNanos / 1000000) / 1000.0d;
                                double throughputBytesPerSec = currentBytesAllocated / elapsedSecondsExact;

                                System.out.println(
                                    "arrays built: "   + getNumberInstance(US).format(arrayCount) + ", " +
                                        "elapsed: "    + elapsedSecs + " secs, " +
                                        "allocated: "  + humanReadableByteCount(currentBytesAllocated) + ", " +
                                        "heap: "       + humanReadableByteCount(Runtime.getRuntime().totalMemory()) + ", " +
                                        "throughput: " + humanReadableByteCount(throughputBytesPerSec) + " / sec"
                                );
                            }
                        }
                    }
                }
            }.start();
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
