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
import com.hippo.util.withIOContext
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(DelicateCoroutinesApi::class)
object AdBlockManager {
    private val blockedHashes = CopyOnWriteArraySet<Long>()
    private val file = File(AppConfig.getFilesDir("adblock"), "ad_blocked_hashes.txt")
    private val isSaving = AtomicBoolean(false)

    fun isEmpty() = blockedHashes.isEmpty()

    fun clear() {
        blockedHashes.clear()
        save()
    }

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
                    while (true) {
                        val currentHashes = blockedHashes.toList()
                        withIOContext {
                            FileOutputStream(file).use { output ->
                                output.bufferedWriter().use { writer ->
                                    currentHashes.forEach { hash ->
                                        writer.write(hash.toString())
                                        writer.newLine()
                                    }
                                }
                            }
                        }
                        if (blockedHashes.size == currentHashes.size) break
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

    private fun hammingDistance(h1: Long, h2: Long): Int = java.lang.Long.bitCount(h1 xor h2)
}
