package dunkyboy.interviews;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;

import java.util.concurrent.ThreadLocalRandom;


/**
 * Rumored Google interview question: Using only a function returning a random number between 1 and 5, implement a
 * function returning a random number between 1 and 7.
 */
public class GoogleRngQuestion {

    public static void main(String[] args) {
        testGoogleRNG();
    }


    private static void testGoogleRNG() {
        final Bag<Integer> distributions = new HashBag<>();
        for (int i = 0; i < 10000; i++) {
            final int rand = randBetween1And7();
            distributions.add(rand);
        }

        System.out.println("distributions: " + distributions);
    }

    private static int randBetween1And7() {
        while (true) {
            final int bit0 = randBinary();
            final int bit1 = randBinary();
            final int bit2 = randBinary();

            final int binary3Bits = (bit2 << 2) + (bit1 << 1) + bit0;
            if (binary3Bits != 7) {
                return binary3Bits + 1;
            }
        }
    }

    private static int randBinary() {
        while (true) {
            final int randBetween1And5 = randBetween1And5();
            if (randBetween1And5 != 5) {
                return randBetween1And5 % 2;
            }
        }
    }

    private static int randBetween1And5() {
        return ThreadLocalRandom.current().nextInt(5) + 1;
    }
}
