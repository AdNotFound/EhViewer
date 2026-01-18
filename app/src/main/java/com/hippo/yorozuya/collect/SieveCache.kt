/*
 * Copyright 2024 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.yorozuya.collect

/**
 * SIEVE is a simple and efficient eviction algorithm.
 * It uses a "visited" bit and a "hand" pointer to lazily evict items.
 * It is particularly efficient for "scan" patterns (like reading pages in order).
 */
open class SieveCache<K : Any, V : Any>(
    private val maxSize: Int,
    private val sizeOf: (K, V) -> Int = { _, _ -> 1 },
    private val onEntryRemoved: (K, V, V?, Boolean) -> Unit = { _, _, _, _ -> }
) {
    private val map = mutableMapOf<K, Node<K, V>>()
    private var size = 0
    private var head: Node<K, V>? = null
    private var tail: Node<K, V>? = null
    private var hand: Node<K, V>? = null

    private class Node<K, V>(val key: K, var value: V, var size: Int) {
        var prev: Node<K, V>? = null
        var next: Node<K, V>? = null
        var visited: Boolean = false
    }

    @Synchronized
    operator fun get(key: K): V? {
        val node = map[key] ?: return null
        node.visited = true
        return node.value
    }

    @Synchronized
    fun put(key: K, value: V): V? {
        val nodeSize = sizeOf(key, value)
        val old = map[key]
        if (old != null) {
            size -= old.size
            val oldValue = old.value
            old.value = value
            old.size = nodeSize
            size += nodeSize
            old.visited = true
            trimToSize(maxSize)
            onEntryRemoved(key, oldValue, value, false)
            return oldValue
        }

        val newNode = Node(key, value, nodeSize)
        map[key] = newNode
        addToHead(newNode)
        size += nodeSize
        trimToSize(maxSize)
        return null
    }

    @Synchronized
    operator fun set(key: K, value: V) {
        put(key, value)
    }

    @Synchronized
    fun remove(key: K): V? {
        val node = map.remove(key) ?: return null
        size -= node.size
        removeFromList(node)
        val value = node.value
        onEntryRemoved(key, value, null, false)
        return value
    }

    @Synchronized
    fun evictAll() {
        trimToSize(-1)
    }

    private fun trimToSize(limit: Int) {
        while (size > limit && map.isNotEmpty()) {
            val toEvict = findToEvict() ?: break
            map.remove(toEvict.key)
            size -= toEvict.size
            removeFromList(toEvict)
            onEntryRemoved(toEvict.key, toEvict.value, null, true)
        }
    }

    private fun findToEvict(): Node<K, V>? {
        var curr = hand ?: tail
        while (curr != null) {
            if (curr.visited) {
                curr.visited = false
                curr = curr.prev ?: tail
            } else {
                hand = curr.prev ?: tail
                return curr
            }
        }
        return null
    }

    private fun addToHead(node: Node<K, V>) {
        node.next = head
        node.prev = null
        head?.prev = node
        head = node
        if (tail == null) {
            tail = node
        }
    }

    private fun removeFromList(node: Node<K, V>) {
        if (hand == node) {
            hand = node.prev ?: tail
        }
        val prev = node.prev
        val next = node.next
        if (prev != null) {
            prev.next = next
        } else {
            head = next
        }
        if (next != null) {
            next.prev = prev
        } else {
            tail = prev
        }
        node.prev = null
        node.next = null
    }

    @Synchronized
    fun size(): Int = size
}
