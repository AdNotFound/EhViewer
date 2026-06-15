/*
 * Copyright 2016 Hippo Seven
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
package com.hippo.ehviewer

import android.content.Context
import android.net.Uri
import androidx.paging.PagingSource
import androidx.room.Room.databaseBuilder
import com.hippo.ehviewer.EhApplication.Companion.ehDatabase
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.dao.BasicDao
import com.hippo.ehviewer.dao.BookmarkInfo
import com.hippo.ehviewer.dao.DownloadDirname
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.dao.DownloadLabel
import com.hippo.ehviewer.dao.EhDatabase
import com.hippo.ehviewer.dao.Filter
import com.hippo.ehviewer.dao.HistoryInfo
import com.hippo.ehviewer.dao.LocalFavoriteInfo
import com.hippo.ehviewer.dao.QuickSearch
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.unifile.UniFile
import com.hippo.util.sendTo
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object EhDB {
    private val db = ehDatabase
    private val dbLock = ReentrantLock()

    private inline fun <T> accessDb(crossinline block: () -> T): T =
        dbLock.withLock { block() }

    // Fix state
    val allDownloadInfo: List<DownloadInfo>
        get() = accessDb {
            db.downloadsDao().list().onEach {
                if (it.state == DownloadInfo.STATE_WAIT || it.state == DownloadInfo.STATE_DOWNLOAD) {
                    it.state = DownloadInfo.STATE_NONE
                }
            }
        }

    fun updateDownloadInfo(downloadInfos: List<DownloadInfo>) = accessDb {
        val dao = db.downloadsDao()
        dao.update(downloadInfos)
    }

    fun putDownloadInfo(downloadInfo: DownloadInfo) = accessDb {
        db.downloadsDao().run {
            if (load(downloadInfo.gid) != null) {
                update(downloadInfo)
            } else {
                insert(downloadInfo)
            }
        }
    }

    fun removeDownloadInfo(downloadInfo: DownloadInfo) = accessDb {
        db.downloadsDao().delete(downloadInfo)
    }

    val allDownloadDirname: List<DownloadDirname>
        get() = accessDb { db.downloadDirnameDao().list() }

    fun getDownloadDirname(gid: Long): String? = accessDb {
        val dao = db.downloadDirnameDao()
        val raw = dao.load(gid)
        raw?.dirname
    }

    fun putDownloadDirname(gid: Long, dirname: String?) = accessDb {
        val dao = db.downloadDirnameDao()
        var raw = dao.load(gid)
        if (raw != null) {
            raw.dirname = dirname
            dao.update(raw)
        } else {
            raw = DownloadDirname(gid, dirname)
            dao.insert(raw)
        }
    }

    fun removeDownloadDirname(gid: Long) = accessDb {
        val dao = db.downloadDirnameDao()
        dao.deleteByKey(gid)
    }

    val allDownloadLabelList: List<DownloadLabel>
        get() = accessDb { db.downloadLabelDao().list() }

    fun addDownloadLabel(label: String): DownloadLabel = accessDb {
        val dao = db.downloadLabelDao()
        val raw = DownloadLabel()
        raw.label = label
        raw.time = System.currentTimeMillis()
        raw.id = dao.insert(raw)
        raw
    }

    fun addDownloadLabel(raw: DownloadLabel): DownloadLabel = accessDb {
        // Reset id
        raw.id = null
        val dao = db.downloadLabelDao()
        raw.id = dao.insert(raw)
        raw
    }

    fun updateDownloadLabel(raw: DownloadLabel?) = accessDb {
        val dao = db.downloadLabelDao()
        dao.update(raw!!)
    }

    fun moveDownloadLabel(fromPosition: Int, toPosition: Int) = accessDb {
        if (fromPosition != toPosition) {
            val reverse = fromPosition > toPosition
            val offset = if (reverse) toPosition else fromPosition
            val limit = if (reverse) fromPosition - toPosition + 1 else toPosition - fromPosition + 1
            val dao = db.downloadLabelDao()
            val list = dao.list(offset, limit)
            val step = if (reverse) 1 else -1
            val start = if (reverse) limit - 1 else 0
            val end = if (reverse) 0 else limit - 1
            val toTime = list[end].time
            var i = end
            while (if (reverse) i < start else i > 0) {
                val aTime = list[i].time
                val bTime = list[i + step].time
                list[i].time = if (aTime == bTime) bTime + step else bTime
                i += step
            }
            list[start].time = toTime
            dao.update(list)
        }
    }

    fun removeDownloadLabel(raw: DownloadLabel?) = accessDb {
        val dao = db.downloadLabelDao()
        dao.delete(raw!!)
    }

    val allLocalFavorites: List<GalleryInfo>
        get() = accessDb {
            val dao = db.localFavoritesDao()
            val list = dao.list()
            ArrayList<GalleryInfo>(list)
        }

    fun searchLocalFavorites(query: String): List<GalleryInfo> = accessDb {
        val dao = db.localFavoritesDao()
        val list = dao.list("%$query%")
        ArrayList<GalleryInfo>(list)
    }

    fun removeLocalFavorites(gid: Long) = accessDb {
        db.localFavoritesDao().deleteByKey(gid)
    }

    fun removeLocalFavorites(gidArray: LongArray) = accessDb {
        val dao = db.localFavoritesDao()
        for (gid in gidArray) {
            dao.deleteByKey(gid)
        }
    }

    fun containLocalFavorites(gid: Long): Boolean = accessDb {
        val dao = db.localFavoritesDao()
        dao.contains(gid)
    }

    fun putLocalFavorites(galleryInfo: GalleryInfo) = accessDb {
        val dao = db.localFavoritesDao()
        if (null == dao.load(galleryInfo.gid)) {
            val info: LocalFavoriteInfo
            if (galleryInfo is LocalFavoriteInfo) {
                info = galleryInfo
            } else {
                info = LocalFavoriteInfo(galleryInfo)
                info.time = System.currentTimeMillis()
            }
            dao.insert(info)
        }
    }

    fun putLocalFavorites(galleryInfoList: List<GalleryInfo>) = accessDb {
        for (gi in galleryInfoList) {
            putLocalFavorites(gi)
        }
    }

    val allQuickSearch: List<QuickSearch>
        get() = accessDb {
            val dao = db.quickSearchDao()
            dao.list()
        }

    fun insertQuickSearch(quickSearch: QuickSearch) = accessDb {
        val dao = db.quickSearchDao()
        quickSearch.id = null
        quickSearch.time = System.currentTimeMillis()
        quickSearch.id = dao.insert(quickSearch)
    }

    fun importQuickSearch(quickSearchList: List<QuickSearch?>) = accessDb {
        val dao = db.quickSearchDao()
        for (quickSearch in quickSearchList) {
            dao.insert(quickSearch!!)
        }
    }

    fun deleteQuickSearch(quickSearch: QuickSearch?) {
        quickSearch ?: return
        accessDb {
            val dao = db.quickSearchDao()
            dao.delete(quickSearch)
        }
    }

    fun moveQuickSearch(fromPosition: Int, toPosition: Int) = accessDb {
        if (fromPosition != toPosition) {
            val reverse = fromPosition > toPosition
            val offset = if (reverse) toPosition else fromPosition
            val limit = if (reverse) fromPosition - toPosition + 1 else toPosition - fromPosition + 1
            val dao = db.quickSearchDao()
            val list = dao.list(offset, limit)
            val step = if (reverse) 1 else -1
            val start = if (reverse) limit - 1 else 0
            val end = if (reverse) 0 else limit - 1
            val toTime = list[end].time
            var i = end
            while (if (reverse) i < start else i > 0) {
                val aTime = list[i].time
                val bTime = list[i + step].time
                list[i].time = if (aTime == bTime) bTime + step else bTime
                i += step
            }
            list[start].time = toTime
            dao.update(list)
        }
    }

    val historyLazyList: PagingSource<Int, HistoryInfo>
        get() = accessDb { db.historyDao().listLazy() }

    fun putHistoryInfo(galleryInfo: GalleryInfo) = accessDb {
        val dao = db.historyDao()
        val info = galleryInfo as? HistoryInfo ?: HistoryInfo(galleryInfo)
        info.time = System.currentTimeMillis()
        if (null != dao.load(info.gid)) {
            dao.update(info)
        } else {
            dao.insert(info)
        }
    }

    fun updateHistoryFavSlot(gid: Long, slot: Int) = accessDb {
        val dao = db.historyDao()
        val info = dao.load(gid)
        if (null != info) {
            info.favoriteSlot = slot
            dao.update(info)
        }
    }

    fun putHistoryInfo(historyInfoList: List<HistoryInfo>) = accessDb {
        val dao = db.historyDao()
        for (info in historyInfoList) {
            if (null == dao.load(info.gid)) {
                dao.insert(info)
            }
        }
    }

    fun deleteHistoryInfo(info: HistoryInfo?) = accessDb {
        val dao = db.historyDao()
        dao.delete(info!!)
    }

    fun clearHistoryInfo() = accessDb {
        val dao = db.historyDao()
        dao.deleteAll()
    }

    val allFilter: List<Filter>
        get() = accessDb { db.filterDao().list() }

    fun addFilter(filter: Filter): Boolean = accessDb {
        val existFilter: Filter? = try {
            db.filterDao().load(filter.text!!, filter.mode)
        } catch (_: Exception) {
            null
        }
        if (existFilter == null) {
            filter.id = null
            filter.id = db.filterDao().insert(filter)
            true
        } else {
            false
        }
    }

    fun deleteFilter(filter: Filter) = accessDb {
        db.filterDao().delete(filter)
    }

    fun triggerFilter(filter: Filter) = accessDb {
        filter.enable = filter.enable?.not() == true
        db.filterDao().update(filter)
    }

    private fun <T> copyDao(from: BasicDao<T>, to: BasicDao<T>) {
        val list = from.list()
        for (item in list) to.insert(item)
    }

    fun exportDB(context: Context, uri: Uri): Boolean = accessDb {
        val ehExportName = "eh.export.db"
        runCatching {
            // Delete old export db
            context.deleteDatabase(ehExportName)
            val newDb =
                databaseBuilder(context, EhDatabase::class.java, ehExportName).build()

            // Copy data to a export db
            copyDao(db.downloadsDao(), newDb.downloadsDao())
            copyDao(db.downloadLabelDao(), newDb.downloadLabelDao())
            copyDao(db.downloadDirnameDao(), newDb.downloadDirnameDao())
            copyDao(db.historyDao(), newDb.historyDao())
            copyDao(db.quickSearchDao(), newDb.quickSearchDao())
            copyDao(db.localFavoritesDao(), newDb.localFavoritesDao())
            copyDao(db.filterDao(), newDb.filterDao())
            copyDao(db.bookmarksBao(), newDb.bookmarksBao())

            // Close export db so we can copy it
            newDb.close()

            // Copy export db to data dir
            val dbFile = context.getDatabasePath(ehExportName)
            UniFile.fromFile(dbFile)!! sendTo UniFile.fromUri(context, uri)!!
            true
        }.onFailure {
            it.printStackTrace()
        }.getOrDefault(false)
    }

    /**
     * @return error string, null for no error
     */
    fun importDB(context: Context, uri: Uri): String? = accessDb {
        val tmpDBName = "tmp.db"
        val errorList = mutableListOf<String>()
        var oldDB: EhDatabase? = null
        var result: String? = null
        runCatching {
            oldDB = databaseBuilder(context, EhDatabase::class.java, tmpDBName)
                .createFromInputStream { context.contentResolver.openInputStream(uri) }.build()
                
            // Force Room to open the database and validate the schema immediately.
            // If the schema is invalid (e.g. from another branch), this will throw an exception
            // and correctly fall back to the raw SQLite strategy.
            oldDB!!.openHelper.readableDatabase
            
            // Download label
            val importDb = oldDB!!
            val manager = DownloadManager
            runCatching {
                val downloadLabelList = importDb.downloadLabelDao().list()
                manager.addDownloadLabel(downloadLabelList)
            }.onFailure { errorList.add("Download Label: " + it.message) }
            // Downloads
            runCatching {
                val downloadInfoList = importDb.downloadsDao().list()
                manager.addDownload(downloadInfoList, false)
            }.onFailure { errorList.add("Downloads: " + it.message) }
            // Download dirname
            runCatching {
                importDb.downloadDirnameDao().list().forEach {
                    putDownloadDirname(it.gid, it.dirname)
                }
            }.onFailure { errorList.add("Download dirname: " + it.message) }
            // History
            runCatching {
                val historyInfoList = importDb.historyDao().list()
                putHistoryInfo(historyInfoList)
            }.onFailure { errorList.add("History: " + it.message) }
            // QuickSearch
            runCatching {
                val quickSearchList = importDb.quickSearchDao().list()
                val currentQuickSearchList = db.quickSearchDao().list()
                val importList = quickSearchList.mapNotNull { newQS ->
                    newQS.takeIf { currentQuickSearchList.find { it.name == newQS.name } == null }
                }
                importQuickSearch(importList)
            }.onFailure { errorList.add("QuickSearch: " + it.message) }
            // LocalFavorites
            runCatching {
                importDb.localFavoritesDao().list().forEach {
                    putLocalFavorites(it)
                }
            }.onFailure { errorList.add("LocalFavorites: " + it.message) }
            // Filter
            runCatching {
                val filterList = importDb.filterDao().list()
                val currentFilterList = db.filterDao().list()
                filterList.forEach {
                    if (it !in currentFilterList) addFilter(it)
                }
            }.onFailure { errorList.add("Filter: " + it.message) }
            
            // Bookmarks
            runCatching {
                val bookmarksList = importDb.bookmarksBao().list()
                val currentBookmarks = db.bookmarksBao().list()
                bookmarksList.forEach {
                    if (currentBookmarks.find { current -> current.gid == it.gid } == null) {
                        db.bookmarksBao().insert(it)
                    }
                }
            }.onFailure { errorList.add("Bookmarks: " + it.message) }
            importDb.close()
            context.deleteDatabase(tmpDBName)
        }.onFailure { originalException ->
            originalException.printStackTrace()
            // If the database structure is different or corrupt, we catch the fatal error here
            // Clear previous Room failures from errorList because we are starting fallback
            errorList.clear()
            // Fallback: Try reading via raw SQLite
            try {
                // Close the Room database to release the file lock
                runCatching { oldDB?.close() }
                val dbFile = context.getDatabasePath(tmpDBName)
                val rawDB = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                rawDB.use {

                // Common function to safely get string from cursor
                fun android.database.Cursor.getStringOrNull(columnName: String): String? {
                    val index = getColumnIndex(columnName)
                    return if (index != -1 && !isNull(index)) getString(index) else null
                }
                
                fun android.database.Cursor.getLongOrNull(columnName: String): Long? {
                    val index = getColumnIndex(columnName)
                    return if (index != -1 && !isNull(index)) getLong(index) else null
                }

                fun android.database.Cursor.getIntOrNull(columnName: String): Int? {
                    val index = getColumnIndex(columnName)
                    return if (index != -1 && !isNull(index)) getInt(index) else null
                }

                // 1. Download Labels
                runCatching {
                    val cursor = rawDB.rawQuery("SELECT * FROM DOWNLOAD_LABELS", null)
                    val existingLabels = db.downloadLabelDao().list()
                    cursor.use { c ->
                        while (c.moveToNext()) {
                            val label = c.getStringOrNull("LABEL")
                            if (label != null && existingLabels.find { it.label == label } == null) {
                                db.downloadLabelDao().insert(DownloadLabel(label = label, time = c.getLongOrNull("TIME") ?: 0L))
                            }
                        }
                    }
                }.onFailure { errorList.add("Fallback Download Labels: " + it.message) }

                // 2. Downloads
                runCatching {
                    val manager = DownloadManager
                    val cursor = rawDB.rawQuery("SELECT * FROM DOWNLOADS", null)
                    cursor.use { c ->
                        val list = mutableListOf<DownloadInfo>()
                        while (c.moveToNext()) {
                            val gid = c.getLongOrNull("GID") ?: continue
                            if (!manager.containDownloadInfo(gid)) {
                                val thumb = c.getStringOrNull("THUMB")
                                if (thumb.isNullOrEmpty()) continue
                                val info = DownloadInfo()
                                info.gid = gid
                                info.token = c.getStringOrNull("TOKEN")
                                info.title = c.getStringOrNull("TITLE")
                                info.titleJpn = c.getStringOrNull("TITLE_JPN")
                                info.thumb = thumb
                                info.category = c.getIntOrNull("CATEGORY") ?: 0
                                info.posted = c.getStringOrNull("POSTED")
                                info.uploader = c.getStringOrNull("UPLOADER")
                                info.rating = c.getIntOrNull("RATING")?.toFloat() ?: 0f
                                info.simpleLanguage = c.getStringOrNull("SIMPLE_LANGUAGE")
                                info.state = c.getIntOrNull("STATE") ?: 0
                                info.legacy = c.getIntOrNull("LEGACY") ?: 0
                                info.time = c.getLongOrNull("TIME") ?: 0L
                                info.label = c.getStringOrNull("LABEL")
                                list.add(info)
                            }
                        }
                        if (list.isNotEmpty()) {
                            manager.addDownload(list, false)
                        }
                    }
                }.onFailure { errorList.add("Fallback Downloads: " + it.message) }

                // 3. History
                runCatching {
                    val cursor = rawDB.rawQuery("SELECT * FROM HISTORY", null)
                    cursor.use { c ->
                        val list = mutableListOf<HistoryInfo>()
                        while (c.moveToNext()) {
                            val gid = c.getLongOrNull("GID") ?: continue
                            val thumb = c.getStringOrNull("THUMB")
                            if (thumb.isNullOrEmpty()) continue
                            val info = HistoryInfo()
                            info.gid = gid
                            info.token = c.getStringOrNull("TOKEN")
                            info.title = c.getStringOrNull("TITLE")
                            info.titleJpn = c.getStringOrNull("TITLE_JPN")
                            info.thumb = thumb
                            info.category = c.getIntOrNull("CATEGORY") ?: 0
                            info.posted = c.getStringOrNull("POSTED")
                            info.uploader = c.getStringOrNull("UPLOADER")
                            info.rating = c.getIntOrNull("RATING")?.toFloat() ?: 0f
                            info.simpleLanguage = c.getStringOrNull("SIMPLE_LANGUAGE")
                            info.time = c.getLongOrNull("TIME") ?: 0L
                            info.favoriteSlotBackingField = c.getIntOrNull("MODE") ?: 0
                            list.add(info)
                        }
                        if (list.isNotEmpty()) {
                            putHistoryInfo(list)
                        }
                    }
                }.onFailure { errorList.add("Fallback History: " + it.message) }

                // 4. Local Favorites
                runCatching {
                    val cursor = rawDB.rawQuery("SELECT * FROM LOCAL_FAVORITES", null)
                    cursor.use { c ->
                        while (c.moveToNext()) {
                            val gid = c.getLongOrNull("GID") ?: continue
                            if (!containLocalFavorites(gid)) {
                                val thumb = c.getStringOrNull("THUMB")
                                if (thumb.isNullOrEmpty()) continue
                                val info = LocalFavoriteInfo()
                                info.gid = gid
                                info.token = c.getStringOrNull("TOKEN")
                                info.title = c.getStringOrNull("TITLE")
                                info.titleJpn = c.getStringOrNull("TITLE_JPN")
                                info.thumb = thumb
                                info.category = c.getIntOrNull("CATEGORY") ?: 0
                                info.posted = c.getStringOrNull("POSTED")
                                info.uploader = c.getStringOrNull("UPLOADER")
                                info.rating = c.getIntOrNull("RATING")?.toFloat() ?: 0f
                                info.simpleLanguage = c.getStringOrNull("SIMPLE_LANGUAGE")
                                info.time = c.getLongOrNull("TIME") ?: 0L
                                putLocalFavorites(info)
                            }
                        }
                    }
                }.onFailure { errorList.add("Fallback LocalFavorites: " + it.message) }

                // 5. QuickSearch
                runCatching {
                    val cursor = rawDB.rawQuery("SELECT * FROM QUICK_SEARCH", null)
                    val currentQuickSearchList = db.quickSearchDao().list()
                    cursor.use { c ->
                        val importList = mutableListOf<QuickSearch>()
                        while (c.moveToNext()) {
                            val name = c.getStringOrNull("NAME")
                            if (name != null && currentQuickSearchList.find { it.name == name } == null) {
                                val qs = QuickSearch(
                                    name = name,
                                    mode = c.getIntOrNull("MODE") ?: 0,
                                    category = c.getIntOrNull("CATEGORY") ?: 0,
                                    keyword = c.getStringOrNull("KEYWORD"),
                                    advanceSearch = c.getIntOrNull("ADVANCE_SEARCH") ?: 0,
                                    minRating = c.getIntOrNull("MIN_RATING") ?: 0,
                                    pageFrom = c.getIntOrNull("PAGE_FROM") ?: 0,
                                    pageTo = c.getIntOrNull("PAGE_TO") ?: 0,
                                    time = c.getLongOrNull("TIME") ?: 0L,
                                )
                                importList.add(qs)
                            }
                        }
                        if (importList.isNotEmpty()) {
                            importQuickSearch(importList)
                        }
                    }
                }.onFailure { errorList.add("Fallback QuickSearch: " + it.message) }

                // 6. Download Dirname
                runCatching {
                    val cursor = rawDB.rawQuery("SELECT * FROM DOWNLOAD_DIRNAME", null)
                    cursor.use { c ->
                        while (c.moveToNext()) {
                            val gid = c.getLongOrNull("GID") ?: continue
                            val dirname = c.getStringOrNull("DIRNAME")
                            if (dirname != null) {
                                putDownloadDirname(gid, dirname)
                            }
                        }
                    }
                }.onFailure { errorList.add("Fallback DownloadDirname: " + it.message) }

                // 7. Filter
                runCatching {
                    val cursor = rawDB.rawQuery("SELECT * FROM FILTER", null)
                    val currentFilterList = db.filterDao().list()
                    cursor.use { c ->
                        while (c.moveToNext()) {
                            val filter = Filter(
                                mode = c.getIntOrNull("MODE") ?: 0,
                                text = c.getStringOrNull("TEXT"),
                                enable = c.getIntOrNull("ENABLE")?.let { it != 0 },
                            )
                            if (filter !in currentFilterList) {
                                addFilter(filter)
                            }
                        }
                    }
                }.onFailure { errorList.add("Fallback Filter: " + it.message) }

                // 8. Bookmarks
                runCatching {
                    val cursor = rawDB.rawQuery("SELECT * FROM BOOKMARKS", null)
                    val currentBookmarks = db.bookmarksBao().list()
                    cursor.use { c ->
                        while (c.moveToNext()) {
                            val gid = c.getLongOrNull("GID") ?: continue
                            if (currentBookmarks.find { current -> current.gid == gid } == null) {
                                val thumb = c.getStringOrNull("THUMB")
                                if (thumb.isNullOrEmpty()) continue
                                val info = BookmarkInfo()
                                info.gid = gid
                                info.token = c.getStringOrNull("TOKEN")
                                info.title = c.getStringOrNull("TITLE")
                                info.titleJpn = c.getStringOrNull("TITLE_JPN")
                                info.thumb = thumb
                                info.category = c.getIntOrNull("CATEGORY") ?: 0
                                info.posted = c.getStringOrNull("POSTED")
                                info.uploader = c.getStringOrNull("UPLOADER")
                                info.rating = c.getIntOrNull("RATING")?.toFloat() ?: 0f
                                info.simpleLanguage = c.getStringOrNull("SIMPLE_LANGUAGE")
                                info.page = c.getIntOrNull("PAGE") ?: 0
                                info.time = c.getLongOrNull("TIME") ?: 0L
                                db.bookmarksBao().insert(info)
                            }
                        }
                    }
                }.onFailure { errorList.add("Fallback Bookmarks: " + it.message) }

                } // rawDB.use
                context.deleteDatabase(tmpDBName)
                
                // If fallback completed but accumulated some individual errors, report them
                result = if (errorList.isNotEmpty()) {
                    "Fallback Import partially succeeded with errors:\n" + errorList.joinToString("\n")
                } else {
                    null
                }
            } catch (fallbackException: Exception) {
                fallbackException.printStackTrace()
                result = context.getString(R.string.settings_advanced_import_data_cant_read) + "\n" + originalException.message + "\nFallback failed: " + fallbackException.message
            }
        }

        result ?: if (errorList.isNotEmpty()) {
            errorList.joinToString("\n")
        } else {
            null
        }
    }
}
