package dunkyboy.scratch;

import dunkyboy.util.RunningAverage;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


/**
 * Try out random code snippets here
 *
 * Created by Duncan on 6/29/16.
 */
public class ScratchPad {

    public static void main(String[] args) throws Exception {

        testHashGenerationRuntime();


    }

    private static void testHashGenerationRuntime() throws NoSuchAlgorithmException, InvalidKeySpecException {

        long methodStartNanos = System.nanoTime();

        final int numHashAlgoIterations = 10000;
        final int numTestIterations = 10000;

        final String password = "e43b16b3a2fb8e8b63b57a6ab4c13da5";  // example real MMS key (cloud-dev)

        byte[] salt = new byte[16];
        ThreadLocalRandom.current().nextBytes(salt);

        // warm up the VM
        for (int i = 0; i < 1000; i++)
            generateHash(password, salt, numHashAlgoIterations, 160);

        List<byte[]> bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork = new ArrayList<>(1000);  // capped to keep heap usage reasonable

        RunningAverage averageElapsedNanos = new RunningAverage();
        IntStream.range(0, numTestIterations)
            //.parallel()
            .forEach( i -> {
                long startNanos = System.nanoTime();
                byte[] hash = generateHash(password, salt, numHashAlgoIterations, 160);
                long elapsedNanos = System.nanoTime() - startNanos;

                if (bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork.size() == 1000) {
                    bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork.clear();
                }
                bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork.add(hash);

                averageElapsedNanos.addValue(elapsedNanos);
            }
        );

        long totalElapsedNanos = System.nanoTime() - methodStartNanos;

        System.out.println("avg elapsed over " + numTestIterations +
            " iterations: " + (averageElapsedNanos.get()/1_000_000.0) + "ms" +
            " (total elapsed: " + (totalElapsedNanos/1_000_000.0) + "ms)");

        System.out.println("  (buffer size for compiler's benefit: " + bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork.size() + ")");
    }

    private static byte[] generateHash(String password, byte[] salt, int numIterations, int keyLength) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, numIterations, keyLength);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

            return f.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);  // sigh, Java
        }
    }
}
