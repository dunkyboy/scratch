package dunkyboy.crypto;

import dunkyboy.util.ThreadsafeRunningAverage;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;


/**
 * Standalone key hashing benchmark created for https://jira.mongodb.org/browse/CLOUDP-21375
 *
 * Created by darmstrong on 3/24/17.
 */
public class KeyFactoryBenchmark {

    public static void main(String[] args) throws NoSuchAlgorithmException {

        final String password = "e43b16b3a2fb8e8b63b57a6ab4c13da5";  // example real MMS key (cloud-dev)

        final String keyAlgo;
        final int numHashAlgoIterations;
        final int numTestIterations;
        try {
            keyAlgo = args[0];  // e.g. "PBKDF2WithHmacSHA512"

            numHashAlgoIterations = Integer.parseInt(args[1]);  // e.g. 1_000
            numTestIterations     = Integer.parseInt(args[2]);  // e.g. 10_000

        } catch (final Exception e) {
            System.out.println("Expected 3 args: keyAlgo (String), numHashAlgoIteations (int), numTestIterations (int)");
            System.exit(1);
            return;
        }

        System.out.println("Starting benchmark with params at " + new Date() + ":");
        System.out.println("  key algo:        " + keyAlgo);
        System.out.println("  algo iterations: " + numHashAlgoIterations);
        System.out.println("  test iterations: " + numTestIterations);

        final long startNanos = System.nanoTime();

        final byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        SecretKeyFactory factory = SecretKeyFactory.getInstance(keyAlgo);

        // warm up the VM
        for (int i = 0; i < 1_000; i++)
            generateHash(factory, password, salt, numHashAlgoIterations, 160);

        List<byte[]> bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork = new ArrayList<>(1_000);  // capped to keep heap usage reasonable

        ThreadsafeRunningAverage averageElapsedNanos = new ThreadsafeRunningAverage();
        IntStream.range(0, numTestIterations)
//            .parallel()
            .forEach( i -> {
                    long iterationStartNanos = System.nanoTime();
                    byte[] hash = generateHash(factory, password, salt, numHashAlgoIterations, 160);
                    long elapsedNanos = System.nanoTime() - iterationStartNanos;

                    if (bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork.size() == 1_000) {
                        bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork.clear();
                    }
                    bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork.add(hash);

                    averageElapsedNanos.addValue(elapsedNanos);
                }
            );

        long totalElapsedNanos = System.nanoTime() - startNanos;

        System.out.println("avg elapsed over " + numTestIterations +
            " iterations: " + (averageElapsedNanos.get()/1_000_000.0) + "ms" +
            " (total elapsed: " + (totalElapsedNanos/1_000_000.0) + "ms)");

        System.out.println("  (buffer size for compiler's benefit: " + bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork.size() + ")");
    }

    private static byte[] generateHash(SecretKeyFactory factory, String password, byte[] salt, int numIterations, int keyLength) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, numIterations, keyLength);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);  // sigh, Java
        }
    }
}
