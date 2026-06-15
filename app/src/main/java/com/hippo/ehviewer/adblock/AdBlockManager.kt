/*
 * Copyright 2026 AdNotFound *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.hippo.ehviewer.adblock

import com.hippo.ehviewer.AppConfig
import com.hippo.util.launchIO
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object AdBlockManager {
    private const val CHUNK_BITS = 16
    private const val CHUNK_COUNT = 4
    private const val CHUNK_MASK = 0xFFFFL
    private const val MATCH_CACHE_SIZE = 512

    private val blockedHashes = HashSet<Long>()
    private val chunkIndexes = Array(CHUNK_COUNT) { hashMapOf<Int, MutableSet<Long>>() }
    private val matchCache = object : LinkedHashMap<Long, Boolean>(MATCH_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Boolean>?): Boolean = size > MATCH_CACHE_SIZE
    }
    private val file = File(AppConfig.getFilesDir("adblock"), "ad_blocked_hashes.txt")
    private val isSaving = AtomicBoolean(false)
    private val mutationVersion = AtomicLong(0)
    private val dataLock = Any()
    private val loadLock = Any()

    @Volatile
    private var isLoaded = false
    private var matchCacheVersion = 0L

    fun isEmpty(): Boolean {
        ensureLoaded()
        return synchronized(dataLock) {
            blockedHashes.isEmpty()
        }
    }

    fun clear() {
        ensureLoaded()
        val changed = synchronized(dataLock) {
            if (blockedHashes.isEmpty() && !file.exists()) {
                false
            } else {
                blockedHashes.clear()
                clearIndexesLocked()
                mutationVersion.incrementAndGet()
                invalidateMatchCacheLocked()
                true
            }
        }
        if (!changed) {
            return
        }
        save()
    }

    private fun ensureLoaded() {
        if (isLoaded) {
            return
        }
        synchronized(loadLock) {
            if (isLoaded) {
                return
            }
            if (file.exists()) {
                runCatching {
                    val loadedHashes = ArrayList<Long>()
                    FileInputStream(file).use { input ->
                        input.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                line.toLongOrNull()?.let(loadedHashes::add)
                            }
                        }
                    }
                    synchronized(dataLock) {
                        loadedHashes.forEach { addHashLocked(it) }
                    }
                }.onFailure { it.printStackTrace() }
            }
            isLoaded = true
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun save() {
        if (!isSaving.compareAndSet(false, true)) {
            return
        }
        launchIO {
            var savedVersion = -1L
            try {
                while (true) {
                    val version = mutationVersion.get()
                    val currentHashes = synchronized(dataLock) {
                        blockedHashes.toList()
                    }
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        output.bufferedWriter().use { writer ->
                            currentHashes.forEach { hash ->
                                writer.write(hash.toString())
                                writer.newLine()
                            }
                        }
                    }
                    savedVersion = version
                    if (mutationVersion.get() == version) {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSaving.set(false)
            }
            // Race window: a mutation may land between the last version check
            // and isSaving.set(false). That mutation's save() saw isSaving=true
            // and returned early. Re-check once to avoid lost writes.
            if (mutationVersion.get() != savedVersion) {
                save()
            }
        }
    }

    fun addHash(hash: Long) {
        if (hash == 0L) return
        ensureLoaded()
        val changed = synchronized(dataLock) {
            if (addHashLocked(hash)) {
                mutationVersion.incrementAndGet()
                invalidateMatchCacheLocked()
                true
            } else {
                false
            }
        }
        if (changed) {
            save()
        }
    }

    fun removeHash(hash: Long) {
        ensureLoaded()
        val changed = synchronized(dataLock) {
            if (removeHashLocked(hash)) {
                mutationVersion.incrementAndGet()
                invalidateMatchCacheLocked()
                true
            } else {
                false
            }
        }
        if (changed) {
            save()
        }
    }

    fun unblock(hash: Long) {
        if (hash == 0L) return
        ensureLoaded()
        val changed = synchronized(dataLock) {
            val toRemove = collectCandidateHashesLocked(hash)
                .filter { hammingDistance(it, hash) <= 2 }
            if (toRemove.isNotEmpty()) {
                toRemove.forEach { removeHashLocked(it) }
                mutationVersion.incrementAndGet()
                invalidateMatchCacheLocked()
                true
            } else {
                false
            }
        }
        if (changed) {
            save()
        }
    }

    fun isBlocked(hash: Long): Boolean {
        if (hash == 0L) return false
        ensureLoaded()
        return synchronized(dataLock) {
            refreshMatchCacheLocked()
            matchCache[hash]?.let { return@synchronized it }
            if (blockedHashes.contains(hash)) {
                cacheMatchResultLocked(hash, true)
                return@synchronized true
            }
            val candidates = collectCandidateHashesLocked(hash)
            val blocked = candidates.any { hammingDistance(it, hash) <= 2 }
            cacheMatchResultLocked(hash, blocked)
            blocked
        }
    }

    private fun addHashLocked(hash: Long): Boolean {
        if (!blockedHashes.add(hash)) {
            return false
        }
        repeat(CHUNK_COUNT) { index ->
            chunkIndexes[index]
                .getOrPut(chunkOf(hash, index)) { HashSet() }
                .add(hash)
        }
        return true
    }

    private fun removeHashLocked(hash: Long): Boolean {
        if (!blockedHashes.remove(hash)) {
            return false
        }
        repeat(CHUNK_COUNT) { index ->
            val key = chunkOf(hash, index)
            val bucket = chunkIndexes[index][key] ?: return@repeat
            bucket.remove(hash)
            if (bucket.isEmpty()) {
                chunkIndexes[index].remove(key)
            }
        }
        return true
    }

    private fun clearIndexesLocked() {
        chunkIndexes.forEach { it.clear() }
    }

    private fun refreshMatchCacheLocked() {
        val version = mutationVersion.get()
        if (matchCacheVersion != version) {
            matchCache.clear()
            matchCacheVersion = version
        }
    }

    private fun invalidateMatchCacheLocked() {
        matchCache.clear()
        matchCacheVersion = mutationVersion.get()
    }

    private fun cacheMatchResultLocked(hash: Long, blocked: Boolean) {
        refreshMatchCacheLocked()
        matchCache[hash] = blocked
    }

    private fun collectCandidateHashesLocked(hash: Long): Set<Long> {
        val candidates = HashSet<Long>()
        repeat(CHUNK_COUNT) { index ->
            chunkIndexes[index][chunkOf(hash, index)]?.let(candidates::addAll)
        }
        return candidates
    }

    private fun chunkOf(hash: Long, index: Int): Int = ((hash ushr (index * CHUNK_BITS)) and CHUNK_MASK).toInt()

    private fun hammingDistance(h1: Long, h2: Long): Int = java.lang.Long.bitCount(h1 xor h2)
}
