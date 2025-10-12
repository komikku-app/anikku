package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.util.editBackground
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaCoverScreenModel(
    private val mangaId: Long,
    private val getManga: GetManga = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    // AY -->
    private val backgroundCache: BackgroundCache = Injekt.get(),
    // <-- AY
    private val updateManga: UpdateManga = Injekt.get(),

    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    // AY -->
    val pagerState: PagerState = PagerState(pageCount = { 2 }),
    // <-- AY
) : StateScreenModel<Manga?>(null) {

    // AY -->
    private val isCover: Boolean
        get() = pagerState.currentPage != 1
    // <-- AY

    init {
        screenModelScope.launchIO {
            getManga.subscribe(mangaId)
                .collect { newManga -> mutableState.update { newManga } }
        }
    }

    fun saveCover(context: Context) {
        // AY -->
        val savedStringResource = if (isCover) {
            MR.strings.cover_saved
        } else {
            AYMR.strings.background_saved
        }
        val errorSavingStringResource = if (isCover) {
            MR.strings.error_saving_cover
        } else {
            AYMR.strings.error_saving_background
        }
        // <-- AY
        screenModelScope.launch {
            try {
                saveCoverInternal(context, temp = false)
                snackbarHostState.showSnackbar(
                    context.stringResource(savedStringResource),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(errorSavingStringResource),
                    withDismissAction = true,
                )
            }
        }
    }

    fun shareCover(context: Context) {
        // AY -->
        val errorSharingStringResource = if (isCover) {
            MR.strings.error_sharing_cover
        } else {
            AYMR.strings.error_sharing_background
        }
        // <-- AY
        screenModelScope.launch {
            try {
                val uri = saveCoverInternal(context, temp = true) ?: return@launch
                withUIContext {
                    context.startActivity(uri.toShareIntent(context))
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(errorSharingStringResource),
                    withDismissAction = true,
                )
            }
        }
    }

    /**
     * Save manga cover Bitmap to picture or temporary share directory.
     *
     * @param context The context for building and executing the ImageRequest
     * @return the uri to saved file
     */
    private suspend fun saveCoverInternal(context: Context, temp: Boolean): Uri? {
        val manga = state.value ?: return null
        val req = ImageRequest.Builder(context)
            .data(manga)
            .size(Size.ORIGINAL)
            .build()

        return withIOContext {
            val result = context.imageLoader.execute(req).image?.asDrawable(context.resources)

            // TODO: Handle animated cover
            val bitmap = result?.getBitmapOrNull() ?: return@withIOContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    // AY -->
                    name = if (isCover) "${manga.title}-cover" else "${manga.title}-background",
                    // <-- AY
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                ),
            )
        }
    }

    /**
     * Update cover with local file.
     *
     * @param context Context.
     * @param data uri of the cover resource.
     */
    fun editCover(context: Context, data: Uri) {
        val manga = state.value ?: return
        screenModelScope.launchIO {
            context.contentResolver.openInputStream(data)?.use {
                try {
                    // AY -->
                    if (isCover) {
                        manga.editCover(Injekt.get(), it, updateManga, coverCache)
                    } else {
                        manga.editBackground(Injekt.get(), it, updateManga, backgroundCache)
                    }
                    // <-- AY
                    notifyCoverUpdated(context)
                } catch (e: Exception) {
                    notifyFailedImageUpdate(context, e)
                }
            }
        }
    }

    fun deleteCustomCover(context: Context) {
        val mangaId = state.value?.id ?: return
        screenModelScope.launchIO {
            try {
                // AY -->
                if (isCover) {
                    coverCache.deleteCustomCover(mangaId)
                    updateManga.awaitUpdateCoverLastModified(mangaId)
                } else {
                    backgroundCache.deleteCustomBackground(mangaId)
                    updateManga.awaitUpdateBackgroundLastModified(mangaId)
                }
                // <-- AY
                notifyCoverUpdated(context)
            } catch (e: Exception) {
                notifyFailedImageUpdate(context, e)
            }
        }
    }

    private fun notifyCoverUpdated(context: Context) {
        // AY -->
        val updatedStringResource = if (isCover) {
            MR.strings.cover_updated
        } else {
            AYMR.strings.background_updated
        }
        // <-- AY
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(updatedStringResource),
                withDismissAction = true,
            )
        }
    }

    private fun notifyFailedImageUpdate(context: Context, e: Throwable) {
        // AY -->
        val updateFailedStringResource = if (isCover) {
            MR.strings.notification_cover_update_failed
        } else {
            AYMR.strings.notification_background_update_failed
        }
        // <-- AY
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(updateFailedStringResource),
                withDismissAction = true,
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
