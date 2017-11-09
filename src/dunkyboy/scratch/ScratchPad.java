package dunkyboy.scratch;

import dunkyboy.util.ThreadsafeRunningAverage;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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


        testRandomSaltBase64();
//        testHashGenerationRuntime();


    }

    private static void testRandomSaltBase64() {

        byte[] saltBytes = new byte[8];
        new SecureRandom().nextBytes(saltBytes);
        String base64 = Base64.encodeBase64String(saltBytes);

        System.out.println("input: " + new String(saltBytes) + ", output: " + base64);

    }

    private static void testHashGenerationRuntime() throws NoSuchAlgorithmException, InvalidKeySpecException {

        long methodStartNanos = System.nanoTime();

        final int numHashAlgoIterations = 10_000;
        final int numTestIterations = 10_000;

        final String password = "e43b16b3a2fb8e8b63b57a6ab4c13da5";  // example real MMS key (cloud-dev)

        byte[] salt = new byte[8];
        new SecureRandom().nextBytes(salt);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");

        // warm up the VM
        for (int i = 0; i < 1_000; i++)
            generateHash(factory, password, salt, numHashAlgoIterations, 160);

        List<byte[]> bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork = new ArrayList<>(1_000);  // capped to keep heap usage reasonable

        ThreadsafeRunningAverage averageElapsedNanos = new ThreadsafeRunningAverage();
        IntStream.range(0, numTestIterations)
//            .parallel()
            .forEach( i -> {
                long startNanos = System.nanoTime();
                byte[] hash = generateHash(factory, password, salt, numHashAlgoIterations, 160);
                long elapsedNanos = System.nanoTime() - startNanos;

                if (bufferOfHashResultsToEnsureCompilerDoesntOptimizeMyWork.size() == 1_000) {
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

    private static byte[] generateHash(SecretKeyFactory factory, String password, byte[] salt, int numIterations, int keyLength) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, numIterations, keyLength);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);  // sigh, Java
        }
    }
}
