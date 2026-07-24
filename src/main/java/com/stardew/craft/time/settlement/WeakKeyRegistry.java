package com.stardew.craft.time.settlement;

import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Function;

final class WeakKeyRegistry<K, V> {
    private final WeakHashMap<K, V> entries = new WeakHashMap<>();

    V getOrCreate(K key, Function<? super K, ? extends V> factory) {
        return entries.computeIfAbsent(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(factory, "factory"));
    }

    V get(K key) {
        return entries.get(key);
    }

    void remove(K key) {
        entries.remove(key);
    }

    int size() {
        return entries.size();
    }
}
