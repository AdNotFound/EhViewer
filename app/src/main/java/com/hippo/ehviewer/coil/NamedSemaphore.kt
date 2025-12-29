/*
 * Copyright 2024 Moedog
 *
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
package com.hippo.ehviewer.coil

import androidx.collection.mutableScatterMapOf
import io.ktor.utils.io.pool.DefaultPool
import kotlinx.coroutines.sync.Semaphore

class SemaphoreTracker(semaphore: Semaphore, private var count: Int = 0) : Semaphore by semaphore {
    operator fun inc() = apply { count++ }
    operator fun dec() = apply { count-- }
    val isFree
        get() = count == 0
}

class SemaphorePool(val permits: Int) : DefaultPool<SemaphoreTracker>(capacity = 32) {
    override fun produceInstance() = SemaphoreTracker(semaphore = Semaphore(permits = permits))
    override fun validateInstance(instance: SemaphoreTracker) {
        check(instance.availablePermits == permits)
        check(instance.isFree)
    }
}

class NamedSemaphore<K>(val permits: Int) : LockPool<SemaphoreTracker, K> {
    val pool = SemaphorePool(permits = permits)
    val active = mutableScatterMapOf<K, SemaphoreTracker>()
    override fun acquire(key: K) = synchronized(active) { active.getOrPut(key) { pool.borrow() }.inc() }
    override fun release(key: K, lock: SemaphoreTracker) = synchronized(active) {
        lock.dec()
        if (lock.isFree) {
            active.remove(key)
            pool.recycle(lock)
        }
    }
    override suspend fun SemaphoreTracker.lock() = acquire()
    override fun SemaphoreTracker.tryLock() = tryAcquire()
    override fun SemaphoreTracker.unlock() = release()
}
