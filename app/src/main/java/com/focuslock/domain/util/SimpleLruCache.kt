package com.focuslock.domain.util

class SimpleLruCache<K, V>(private val maxSize: Int) {
    private val cache = LinkedHashMap<K, V>()

    @Synchronized
    fun get(key: K): V? {
        val value = cache[key]
        if (value != null) {
            // Move to end (most recently used)
            cache.remove(key)
            cache[key] = value
        }
        return value
    }

    @Synchronized
    fun put(key: K, value: V) {
        if (cache.containsKey(key)) {
            cache.remove(key)
        }
        cache[key] = value
        
        if (cache.size > maxSize) {
            val firstKey = cache.keys.firstOrNull()
            if (firstKey != null) {
                cache.remove(firstKey)
            }
        }
    }

    @Synchronized
    fun remove(key: K) {
        cache.remove(key)
    }

    @Synchronized
    fun containsKey(key: K): Boolean {
        return cache.containsKey(key)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}
