package com.gaiascanner.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gaiascanner.app.core.ConvertDefaults
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.LibrarySort
import com.gaiascanner.app.core.LibraryView
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.core.SortField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class AppSettings(
    val imageFormat: ImageFormat = ImageFormat.JPG,
    val jpegQuality: Int = ConvertDefaults.JPEG_QUALITY,
    val dpi: Int = ConvertDefaults.DPI,
    val pageSize: PageSizeOption = PageSizeOption.FIT,
    val margin: MarginOption = MarginOption.NONE,
    /** Tree URI of the folder every result is also copied to, or null. */
    val outputFolderUri: String? = null,
    /** Library sort, one choice for every folder. Default: newest change first. */
    val librarySort: LibrarySort = LibrarySort(),
    val libraryView: LibraryView = LibraryView.LIST,
    /** The tutorial opens by itself until it has been shown once. */
    val tutorialSeen: Boolean = false,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences) = AppSettings(
        imageFormat = enumOrNull<ImageFormat>(p[FORMAT]) ?: ImageFormat.JPG,
        jpegQuality = (p[QUALITY] ?: ConvertDefaults.JPEG_QUALITY).coerceIn(10, 100),
        dpi = p[DPI]?.takeIf { it in ConvertDefaults.DPI_CHOICES } ?: ConvertDefaults.DPI,
        pageSize = enumOrNull<PageSizeOption>(p[PAGE_SIZE]) ?: PageSizeOption.FIT,
        margin = enumOrNull<MarginOption>(p[MARGIN]) ?: MarginOption.NONE,
        outputFolderUri = p[OUTPUT_FOLDER],
        librarySort = LibrarySort(
            field = enumOrNull<SortField>(p[SORT_FIELD]) ?: SortField.MODIFIED,
            ascending = p[SORT_ASCENDING] ?: false,
        ),
        libraryView = enumOrNull<LibraryView>(p[LIBRARY_VIEW]) ?: LibraryView.LIST,
        tutorialSeen = p[TUTORIAL_SEEN] ?: false,
    )

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[FORMAT] = s.imageFormat.name
        p[QUALITY] = s.jpegQuality
        p[DPI] = s.dpi
        p[PAGE_SIZE] = s.pageSize.name
        p[MARGIN] = s.margin.name
        if (s.outputFolderUri == null) p.remove(OUTPUT_FOLDER) else p[OUTPUT_FOLDER] = s.outputFolderUri
        p[SORT_FIELD] = s.librarySort.field.name
        p[SORT_ASCENDING] = s.librarySort.ascending
        p[LIBRARY_VIEW] = s.libraryView.name
        p[TUTORIAL_SEEN] = s.tutorialSeen
    }

    private companion object {
        val FORMAT = stringPreferencesKey("image_format")
        val QUALITY = intPreferencesKey("jpeg_quality")
        val DPI = intPreferencesKey("render_dpi")
        val PAGE_SIZE = stringPreferencesKey("page_size")
        val MARGIN = stringPreferencesKey("margin")
        val OUTPUT_FOLDER = stringPreferencesKey("output_folder_uri")
        val SORT_FIELD = stringPreferencesKey("library_sort_field")
        val SORT_ASCENDING = booleanPreferencesKey("library_sort_ascending")
        val LIBRARY_VIEW = stringPreferencesKey("library_view")
        val TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")

        inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }
    }
}
