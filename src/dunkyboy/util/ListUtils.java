package dunkyboy.util;

import java.util.*;


/**
 * Implementation alternative for https://github.com/10gen/mms/pull/9761/files/8819188d7071ebf987421db1b1692ce7ff313b0d#diff-dd0e91c1cce53d6ef3490d8a6bb649b4
 *
 * Created by darmstrong on 4/17/17.
 */
public class ListUtils {

    public static <T extends Comparable<T>> List<T> mergeSortedLists(List<List<T>> pLists) {
        return mergeSortedLists(
            pLists,
            Comparator.naturalOrder()
        );
    }

    public static <T> List<T> mergeSortedLists(List<List<T>> pLists, final Comparator<T> pComparator) {

        final TreeSet<T> treeSet = new TreeSet<T>(pComparator);

        final Map<T, Long> counts = new HashMap<>();

        for (final List<T> thisList : pLists) {
            treeSet.addAll(thisList);
            for (T thisElement : thisList) {
                treeSet.add(thisElement);  // O(log treeSet.size)

                Long count = counts.get(thisElement);
                if (count == null) {
                    count = 0L;
                }
                counts.put(thisElement, ++count);
            }
        }

        final List<T> mergedList = new LinkedList<>();

        for (T element : treeSet) {
            final long count = counts.get(element);
            for (long i = 0; i < count; i++) {
                mergedList.add(element);
            }
        }

        return mergedList;
    }
}
