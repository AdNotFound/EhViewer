package com.hippo.ehviewer.adblock

import com.hippo.ehviewer.AppConfig
import com.hippo.util.launchIO
import com.hippo.util.withIOContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object AdBlockManager {
    private val blockedHashes = CopyOnWriteArraySet<Long>()
    private val file = File(AppConfig.getFilesDir("adblock"), "ad_blocked_hashes.txt")
    private val isSaving = AtomicBoolean(false)

    init {
        launchIO {
            load()
        }
    }

    private suspend fun load() = withIOContext {
        if (!file.exists()) return@withIOContext
        runCatching {
            FileInputStream(file).use { input ->
                input.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        line.toLongOrNull()?.let { blockedHashes.add(it) }
                    }
                }
            }
        }.onFailure { it.printStackTrace() }
    }

    private fun save() {
        if (isSaving.compareAndSet(false, true)) {
            launchIO {
                try {
                    withIOContext {
                        FileOutputStream(file).use { output ->
                            output.bufferedWriter().use { writer ->
                                blockedHashes.forEach { hash ->
                                    writer.write(hash.toString())
                                    writer.newLine()
                                }
                            }
                        }
                    }
                } finally {
                    isSaving.set(false)
                }
            }
        }
    }

    fun addHash(hash: Long) {
        if (hash == 0L) return
        if (blockedHashes.add(hash)) {
            save()
        }
    }

    fun removeHash(hash: Long) {
        if (blockedHashes.remove(hash)) {
            save()
        }
    }

    fun unblock(hash: Long) {
        if (hash == 0L) return
        val toRemove = blockedHashes.filter { hammingDistance(it, hash) <= 2 }
        if (toRemove.isNotEmpty()) {
            blockedHashes.removeAll(toRemove.toSet())
            save()
        }
    }

    fun isBlocked(hash: Long): Boolean {
        if (hash == 0L) return false
        // Exact match first
        if (blockedHashes.contains(hash)) return true

        // Fuzzy match via Hamming distance (2-bit tolerance)
        return blockedHashes.any { hammingDistance(it, hash) <= 2 }
    }

    private fun hammingDistance(h1: Long, h2: Long): Int {
        var x = h1 xor h2
        var count = 0
        while (x != 0L) {
            x = x and (x - 1)
            count++
        }
        return count
    }
}
