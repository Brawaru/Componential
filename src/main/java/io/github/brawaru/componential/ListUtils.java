package io.github.brawaru.componential;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class ListUtils {
    private ListUtils() {}

    /**
     * Returns an unmodifiable list copy of the collection
     * @param collection collection to copy
     * @param <T> type of elements in collection
     * @return unmodifiable list copy of the collection
     */
    public static <T> List<T> copyOf(Collection<T> collection) {
        return Collections.unmodifiableList(new ArrayList<>(collection));
    }
}
