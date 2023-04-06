package com.kanglong.m3u8.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.kanglong.m3u8.util.Preconditions.checkNonNegative;
import static com.kanglong.m3u8.util.Preconditions.checkNotNull;

public final class CollUtil {

    private CollUtil() {
    }

    public static <T> boolean addAll(Collection<T> addTo, Iterator<? extends T> iterator) {
        checkNotNull(addTo);
        checkNotNull(iterator);
        boolean wasModified = false;
        while (iterator.hasNext()) {
            wasModified |= addTo.add(iterator.next());
        }
        return wasModified;
    }

    public static <E> ArrayList<E> newArrayList() {
        return new ArrayList<>();
    }

    @SafeVarargs
    public static <E> ArrayList<E> newArrayList(E... elements) {
        checkNotNull(elements);
        int capacity = computeArrayListCapacity(elements.length);
        ArrayList<E> list = new ArrayList<>(capacity);
        Collections.addAll(list, elements);
        return list;
    }

    public static <E> ArrayList<E> newArrayList(Iterator<? extends E> elements) {
        ArrayList<E> list = newArrayList();
        addAll(list, elements);
        return list;
    }

    @SuppressWarnings("unchecked")
    public static <E> ArrayList<E> newArrayList(Iterable<? extends E> elements) {
        checkNotNull(elements);
        return (elements instanceof Collection) ? new ArrayList<>((Collection<? extends E>) elements)
                : newArrayList(elements.iterator());
    }

    public static <E> ArrayList<E> newArrayListWithCapacity(int initialArraySize) {
        checkNonNegative(initialArraySize, "initialArraySize");
        return new ArrayList<>(initialArraySize);
    }

    public static <E> CopyOnWriteArrayList<E> newCopyOnWriteArrayList() {
        return new CopyOnWriteArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static <E> CopyOnWriteArrayList<E> newCopyOnWriteArrayList(Iterable<? extends E> elements) {
        Collection<? extends E> elementsCollection = (elements instanceof Collection)
                ? (Collection<? extends E>) elements : newArrayList(elements);
        return new CopyOnWriteArrayList<>(elementsCollection);
    }

    public static <E> ArrayDeque<E> newArrayDeque() {
        return new ArrayDeque<>();
    }

    public static <E> ArrayDeque<E> newArrayDequeWithCapacity(int initialQueueSize) {
        checkNonNegative(initialQueueSize, "initialQueueSize");
        return new ArrayDeque<>(initialQueueSize);
    }

    @SuppressWarnings("unchecked")
    public static <E> ArrayDeque<E> newArrayDeque(Iterable<? extends E> elements) {
        if (elements instanceof Collection) {
            return new ArrayDeque<>((Collection<? extends E>) elements);
        }
        ArrayDeque<E> deque = new ArrayDeque<>();
        addAll(deque, elements.iterator());
        return deque;
    }

    public static <E> HashSet<E> newHashSet() {
        return new HashSet<>();
    }

    public static <K, V> HashMap<K, V> newHashMap() {
        return new HashMap<>();
    }

    public static <K, V> LinkedHashMap<K, V> newLinkedHashMap() {
        return new LinkedHashMap<>();
    }

    public static <K, V> ConcurrentMap<K, V> newConcurrentMap() {
        return new ConcurrentHashMap<>();
    }

    public static <E> Set<E> newConcurrentHashSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    private static int computeArrayListCapacity(int arraySize) {
        checkNonNegative(arraySize, "arraySize");
        return saturatedCast(5L + arraySize + (arraySize / 10));
    }

    private static int saturatedCast(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

}
