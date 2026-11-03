package com.gaiascanner.app

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.gaiascanner.app.data.Exporter
import com.gaiascanner.app.data.FileStore
import com.gaiascanner.app.data.LibraryRepository
import com.gaiascanner.app.data.SettingsRepository
import com.gaiascanner.app.data.db.AppDatabase
import com.gaiascanner.app.engine.ImageDecoding
import com.gaiascanner.app.engine.PdfFiles
import com.gaiascanner.app.work.ConversionRunner
import com.gaiascanner.app.work.JobRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Hand-wired dependencies. One instance per process, owned by [GaiaApp]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.create(appContext)
    val files = FileStore(appContext)
    val settings = SettingsRepository(appContext)
    val library = LibraryRepository(database, files)
    val exporter = Exporter(appContext)
    val jobs = JobRepository(appContext, database)
    val conversionRunner = ConversionRunner(appContext, database, files, library, settings, exporter)
    val thumbnails = ThumbnailCache()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** One-off messages for the snackbar, for example after files are shared to the app. */
    val messages: SharedFlow<String> = _messages

    fun postMessage(text: String) {
        _messages.tryEmit(text)
    }
}

/** Small in-memory cache of list thumbnails. */
class ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun image(path: String, rotation: Int, maxSide: Int): Bitmap? =
        load("img|$path|$rotation|$maxSide|${File(path).lastModified()}") {
            ImageDecoding.decodeThumbnail(File(path), maxSide, rotation)
        }

    suspend fun pdf(path: String, maxWidth: Int): Bitmap? =
        load("pdf|$path|$maxWidth|${File(path).lastModified()}") {
            PdfFiles.renderFirstPage(File(path), maxWidth)
        }

    private suspend fun load(key: String, block: () -> Bitmap?): Bitmap? {
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { block() } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private companion object {
        const val MAX_BYTES = 24 * 1024 * 1024
    }
}
