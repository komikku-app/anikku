/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Code is a mix between PlayerViewModel from mpvKt and the former
 * PlayerViewModel from Aniyomi.
 */

package eu.kanade.tachiyomi.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.episode.model.toDbEpisode
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.database.models.Episode
import eu.kanade.tachiyomi.data.database.models.toDomainEpisode
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.player.PlayerActivity.Companion.MPV_DIR
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.getChangedAt
import eu.kanade.tachiyomi.ui.player.domain.AudioManager
import eu.kanade.tachiyomi.ui.player.domain.BrightnessManager
import eu.kanade.tachiyomi.ui.player.domain.TrackSelect
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.utils.AniSkipApi
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils.Companion.getStringRes
import eu.kanade.tachiyomi.ui.reader.SaveImageNotifier
import eu.kanade.tachiyomi.util.chapter.filterDownloaded
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import eu.kanade.tachiyomi.util.editBackground
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.editThumbnail
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import exh.source.MERGED_SOURCE_ID
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.service.getEpisodeSort
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.InputStream
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import eu.kanade.domain.track.interactor.TrackChapter as TrackEpisode
import tachiyomi.domain.episode.model.Episode as DomainEpisode

class PlayerViewModel @JvmOverloads constructor(
    private val context: Application,
    private val savedState: SavedStateHandle,
    private val json: Json = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val trackEpisode: TrackEpisode = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val upsertHistory: UpsertHistory = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val setAnimeViewerFlags: SetAnimeViewerFlags = Injekt.get(),
    internal val playerPreferences: PlayerPreferences = Injekt.get(),
    private val audioPreferences: AudioPreferences = Injekt.get(),
    private val gesturePreferences: GesturePreferences = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val getCustomButtons: GetCustomButtons = Injekt.get(),
    private val trackSelect: TrackSelect = Injekt.get(),
    private val audioManager: AudioManager = Injekt.get(),
    brightnessManager: BrightnessManager = Injekt.get(),
    // SY -->
    uiPreferences: UiPreferences = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId = Injekt.get(),
    // SY <--
    // ANK -->
    private val getIncognitoState: GetIncognitoState = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val syncPreferences: SyncPreferences = Injekt.get(),
    // ANK <--
) : AndroidViewModel(context) {

    val cachePath: String = context.applicationContext.cacheDir.path
    val mpv = MPV(context.applicationContext) {
        it.setOptionString("config", "yes")
        it.setOptionString("config-dir", context.filesDir.resolve(MPV_DIR).toString())
        it.setOptionString("gpu-shader-cache-dir", cachePath)
        it.setOptionString("icc-cache-dir", cachePath)
        it.setOptionString("keep-open", "yes")
    }

    private val _isStopped = MutableStateFlow(false)
    val isStopped = _isStopped.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<ImmutableList<Episode>>(persistentListOf())
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _hasPreviousEpisode = MutableStateFlow(false)
    val hasPreviousEpisode = _hasPreviousEpisode.asStateFlow()

    private val _hasNextEpisode = MutableStateFlow(false)
    val hasNextEpisode = _hasNextEpisode.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode = _currentEpisode.asStateFlow()

    private val _currentAnime = MutableStateFlow<Anime?>(null)
    val currentAnime = _currentAnime.asStateFlow()

    /**
     * The anime loaded in the player. It can be null when instantiated for a short time.
     */
    val anime: Anime?
        get() = currentAnime.value

    private val _currentSource = MutableStateFlow<AnimeSource?>(null)
    val currentSource = _currentSource.asStateFlow()

    private val _isEpisodeOnline = MutableStateFlow(false)
    val isEpisodeOnline = _isEpisodeOnline.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)
    val isLoadingEpisode = _isLoadingEpisode.asStateFlow()

    val mediaTitle = MutableStateFlow("")
    val animeTitle = MutableStateFlow("")

    val isLoading = MutableStateFlow(true)
    val hasLoadedTracks = MutableStateFlow(false)
    val hasLoadedSubs = MutableStateFlow(false)
    val hasLoadedAudio = MutableStateFlow(false)

    private val _externalSubtitleTracks = MutableStateFlow<List<VideoTrack.External>>(emptyList())
    val externalSubtitleTracks = _externalSubtitleTracks.asStateFlow()

    private val _externalAudioTracks = MutableStateFlow<List<VideoTrack.External>>(emptyList())
    val externalAudioTracks = _externalAudioTracks.asStateFlow()

    val isCasting = MutableStateFlow(false)
    // ANK -->
    private var castPos: Int = 0
    // ANK <--

    private val _hosterList = MutableStateFlow<List<Hoster>>(emptyList())
    val hosterList = _hosterList.asStateFlow()
    private val _isLoadingHosters = MutableStateFlow(true)
    val isLoadingHosters = _isLoadingHosters.asStateFlow()
    private val _hosterState = MutableStateFlow<List<HosterState>>(emptyList())
    val hosterState = _hosterState.asStateFlow()
    private val _hosterExpandedList = MutableStateFlow<List<Boolean>>(emptyList())
    val hosterExpandedList = _hosterExpandedList.asStateFlow()
    private val _selectedHosterVideoIndex = MutableStateFlow(Pair(-1, -1))
    val selectedHosterVideoIndex = _selectedHosterVideoIndex.asStateFlow()
    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    private val _pausedState = MutableStateFlow<Boolean?>(false)
    val pausedState = _pausedState.asStateFlow()

    private val netflixTimeout = MutableStateFlow<Int?>(null)

    // Start mpvKt

    private val _customButtons = MutableStateFlow<ImmutableList<CustomButton>>(persistentListOf())
    val customButtons = _customButtons.asStateFlow()

    private val _primaryButtonTitle = MutableStateFlow("")
    val primaryButtonTitle = _primaryButtonTitle.asStateFlow()

    private val _primaryButton = MutableStateFlow<CustomButton?>(null)
    val primaryButton = _primaryButton.asStateFlow()

    val paused by mpv.propFlow<Boolean>("pause").collectAsState(viewModelScope)
    val pos by mpv.propFlow<Int>("time-pos").collectAsState(viewModelScope)
    val duration by mpv.propFlow<Int>("duration").collectAsState(viewModelScope)

    val currentVolume = MutableStateFlow(audioManager.getVolume())
    private val volumeBoostCap by mpv.propFlow<Int>("volume-max").collectAsState(viewModelScope)

    val subtitleTracks = mpv.propFlow<MPVNode>("track-list")
        .map { node ->
            (
                node?.toObject<List<TrackNode>>(json)
                    ?.filter { it.isSubtitle }
                    ?.filterNot { it.title?.startsWith(VideoTrack.TRACK_TITLE_TAG) == true }
                    ?: persistentListOf()
                ).toImmutableList()
        }

    val audioTracks = mpv.propFlow<MPVNode>("track-list")
        .map { node ->
            (
                node?.toObject<List<TrackNode>>(json)
                    ?.filter { it.isAudio }
                    ?.filterNot { it.title?.startsWith(VideoTrack.TRACK_TITLE_TAG) == true }
                    ?: persistentListOf()
                ).toImmutableList()
        }

    val chapters = mpv.propFlow<MPVNode>("chapter-list")
        .map { (it?.toObject<List<ChapterNode>>(json) ?: persistentListOf()).map { it.toSegment() }.toImmutableList() }

    private val _skipIntroText = MutableStateFlow<String?>(null)
    val skipIntroText = _skipIntroText.asStateFlow()

    private val _controlsShown = MutableStateFlow(true)
    val controlsShown = _controlsShown.asStateFlow()
    private val _seekBarShown = MutableStateFlow(true)
    val seekBarShown = _seekBarShown.asStateFlow()
    private val _areControlsLocked = MutableStateFlow(false)
    val areControlsLocked = _areControlsLocked.asStateFlow()

    val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
    val isBrightnessSliderShown = MutableStateFlow(false)
    val isVolumeSliderShown = MutableStateFlow(false)
    val currentBrightness = MutableStateFlow(brightnessManager.getCurrentBrightness())

    val sheetShown = MutableStateFlow(Sheets.None)
    val panelShown = MutableStateFlow(Panels.None)
    val dialogShown = MutableStateFlow<Dialogs>(Dialogs.None)
    private val _dismissSheet = MutableStateFlow(false)
    val dismissSheet = _dismissSheet.asStateFlow()

    // Pair(startingPosition, seekAmount)
    val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)

    private val _seekText = MutableStateFlow<String?>(null)
    val seekText = _seekText.asStateFlow()
    private val _doubleTapSeekAmount = MutableStateFlow(0)
    val doubleTapSeekAmount = _doubleTapSeekAmount.asStateFlow()
    private val _isSeekingForwards = MutableStateFlow(false)
    val isSeekingForwards = _isSeekingForwards.asStateFlow()

    private var timerJob: Job? = null
    private val _remainingTime = MutableStateFlow(0)
    val remainingTime = _remainingTime.asStateFlow()

    // ANK -->
    private val unfilteredEpisodeList by lazy {
        val anime = anime!!
        runBlocking {
            // KMK -->
            if (anime.source == MERGED_SOURCE_ID) {
                getMergedChaptersByMangaId.await(anime.id, dedupe = false, applyFilter = false)
            } else {
                getEpisodesByAnimeId.await(anime.id, applyFilter = false)
            }
            // KMK <--
        }
    }
    // ANK <--

    init {
        mpv.propFlow<Long>("time-pos")
            .filterNotNull()
            .onEach(::onSecondReached)
            .launchIn(viewModelScope)

        mpv.propFlow<Int>("chapter")
            .onEach(::onChapterChanged)
            .launchIn(viewModelScope)

        mpv.propFlow<MPVNode>("sid")
            .onEach { onSubtitleTrackSelectChange() }
            .launchIn(viewModelScope)

        mpv.propFlow<MPVNode>("secondary-sid")
            .onEach { onSubtitleTrackSelectChange() }
            .launchIn(viewModelScope)

        mpv.propFlow<Long>("user-data/current-anime/intro-length")
            .filterNotNull()
            .onEach(::setAnimeSkipIntroLength)
            .launchIn(viewModelScope)

        mpv.propFlow<MPVNode>("track-list")
            .filterNotNull()
            .onEach { onTrackListChanged(it) }
            .launchIn(viewModelScope)
    }

    /**
     * Starts a sleep timer/cancels the current timer if [seconds] is less than 1.
     */
    fun startTimer(seconds: Int) {
        timerJob?.cancel()
        _remainingTime.value = seconds
        if (seconds < 1) return
        timerJob = viewModelScope.launch {
            for (time in seconds downTo 0) {
                _remainingTime.value = time
                delay(1000)
            }
            mpv.setPropertyBoolean("pause", true)
            eventChannel.send(Event.ShowToast(AYMR.strings.toast_sleep_timer_ended))
        }
    }

    suspend fun getCustomButtons(): List<CustomButton> {
        return getCustomButtons.getAll()
    }

    fun setCustomButtons(buttons: List<CustomButton>) {
        _customButtons.update { _ -> buttons.toPersistentList() }
        buttons.firstOrNull { it.isFavorite }?.let {
            _primaryButton.update { _ -> it }
            if (primaryButtonTitle.value.isEmpty()) {
                setPrimaryCustomButtonTitle(it)
            }
        }
    }

    fun isEpisodeOnline(): Boolean? {
        val anime = anime ?: return null
        val episode = currentEpisode.value ?: return null
        val source = currentSource.value ?: return null
        return source is HttpSource &&
            !EpisodeLoader.isDownload(
                episode.toDomainEpisode()!!,
                anime,
            )
    }

    fun clearTracks() {
        hasLoadedTracks.update { _ -> false }
        _externalAudioTracks.update { _ -> emptyList() }
        _externalSubtitleTracks.update { _ -> emptyList() }
    }

    fun updateIsLoadingEpisode(value: Boolean) {
        _isLoadingEpisode.update { _ -> value }
    }

    private fun updateEpisodeList(episodeList: List<Episode>) {
        _currentPlaylist.update { _ ->
            // ANK -->
            episodeList.toPersistentList()
            // ANK <--
        }
    }

    /**
     * When all subtitle/audio tracks are loaded, select the preferred one based on preferences,
     * or select the first one in the list if trackSelect fails.
     */
    fun onTrackListChanged(tracks: MPVNode) {
        val tracks = tracks.toObject<List<TrackNode>>(json).ifEmpty { return }
        if (hasLoadedTracks.value) {
            onTrackAdded(tracks)
        } else {
            hasLoadedTracks.update { _ -> true }
            onTracksLoaded(tracks)
        }
    }

    private fun onTrackAdded(tracks: List<TrackNode>) {
        val externalSubtitle = tracks.filter {
            it.isSubtitle && it.title?.startsWith(VideoTrack.TRACK_TITLE_TAG) == true
        }
        val externalAudio = tracks.filter {
            it.isAudio && it.title?.startsWith(VideoTrack.TRACK_TITLE_TAG) == true
        }

        externalSubtitle.forEach { track ->
            val idx = track.title!!.split("=")[1].toInt()
            val external = externalSubtitleTracks.value
                // ANK -->
                .getOrNull(idx) ?: return@forEach
            // ANK <--

            if (external.id != null) {
                // External subtitle has already been added
                return@forEach
            }

            updateSubtitleTrackAt(idx) {
                it.copy(id = track.id, state = TrackState.Loaded)
            }
            hasLoadedSubs.update { _ -> true }
            checkFileLoaded()
            selectSubById(track.id)
        }

        externalAudio.forEach { track ->
            val idx = track.title!!.split("=")[1].toInt()
            val external = externalAudioTracks.value
                // ANK -->
                .getOrNull(idx) ?: return@forEach
            // ANK <--

            if (external.id != null) {
                // External audio has already been added
                return@forEach
            }

            updateAudioTrackAt(idx) {
                it.copy(id = track.id, state = TrackState.Loaded)
            }
            hasLoadedAudio.update { _ -> true }
            checkFileLoaded()
            selectAudioById(track.id)
        }
    }

    /**
     * Called when embedded tracks are first loaded
     */
    private fun onTracksLoaded(tracks: List<TrackNode>) {
        // ANK -->
        // Drop whatever selection mpv carried over from the previous file before choosing this
        // file's tracks, instead of calling `selectAudio`/`selectSub` with a `force` flag:
        // selectSubById()/selectAudioById() toggle a track off when it is already the selected
        // one, so a carried-over id would turn the track below off instead of on -- as would the
        // onTrackAdded() selections that follow each `sub-add`/`audio-add` for this file.
        //
        // This has to happen here, on the file whose tracks are being selected, and not before
        // the `loadfile`: these properties change the `selected` field of the *outgoing* file's
        // track list, so mpv emits a `track-list` event for it that races the `loadfile`. Losing
        // that race routes the outgoing file's tracks into this function while hasLoadedTracks is
        // still false, and the new file's own list then arrives at onTrackAdded() and never gets
        // a preferred-track selection at all.
        mpv.setPropertyBoolean("sid", false)
        mpv.setPropertyBoolean("secondary-sid", false)
        mpv.setPropertyBoolean("aid", false)
        // ANK <--
        val embeddedSubs = tracks.filter { it.isSubtitle }
        val embeddedAudio = tracks.filter { it.isAudio }
        val externalSubs = currentVideo.value?.subtitleTracks.orEmpty().distinctBy { it.url }
            .mapIndexed { idx, track -> VideoTrack.External(track, idx) }
        val externalAudio = currentVideo.value?.audioTracks.orEmpty().distinctBy { it.url }
            .mapIndexed { idx, track -> VideoTrack.External(track, idx) }

        _externalSubtitleTracks.update { _ -> externalSubs }
        _externalAudioTracks.update { _ -> externalAudio }

        val preferredSubtitle = trackSelect.getPreferredTrackIndex(
            tracks = embeddedSubs.map { VideoTrack.Internal(it) } + externalSubs,
            subtitle = true,
        )
        if (preferredSubtitle == null) {
            hasLoadedSubs.update { _ -> true }
        } else {
            selectSub(preferredSubtitle)
        }

        val preferredAudio = trackSelect.getPreferredTrackIndex(
            tracks = embeddedAudio.map { VideoTrack.Internal(it) } + externalAudio,
            subtitle = false,
        )
        if (preferredAudio == null) {
            hasLoadedAudio.update { _ -> true }
        } else {
            selectAudio(preferredAudio)
        }
    }

    fun addAudio(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(context.applicationContext) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(context.applicationContext) else null
        if (name == null) {
            mpv.command("audio-add", path, "cached")
        } else {
            mpv.command("audio-add", path, "cached", name)
        }
    }

    fun addSubtitle(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(context.applicationContext) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(context.applicationContext) else null
        if (name == null) {
            mpv.command("sub-add", path, "cached")
        } else {
            mpv.command("sub-add", path, "cached", name)
        }
    }

    fun selectSub(track: VideoTrack) {
        when (track) {
            is VideoTrack.External -> {
                if (track.id == null) {
                    updateSubtitleTrackAt(track.index) {
                        it.copy(state = TrackState.Loading)
                    }
                    viewModelScope.launchIO {
                        mpv.command(
                            "sub-add",
                            track.data.url,
                            "auto",
                            "${VideoTrack.TRACK_TITLE_TAG}=${track.index}",
                        )
                    }
                } else {
                    hasLoadedSubs.update { _ -> true }
                    checkFileLoaded()
                    selectSubById(track.id)
                }
            }
            is VideoTrack.Internal -> {
                hasLoadedSubs.update { _ -> true }
                checkFileLoaded()
                selectSubById(track.data.id)
            }
        }
    }

    fun selectAudio(track: VideoTrack) {
        when (track) {
            is VideoTrack.External -> {
                if (track.id == null) {
                    updateAudioTrackAt(track.index) {
                        it.copy(state = TrackState.Loading)
                    }
                    viewModelScope.launchIO {
                        mpv.command(
                            "audio-add",
                            track.data.url,
                            "auto",
                            "${VideoTrack.TRACK_TITLE_TAG}=${track.index}",
                        )
                    }
                } else {
                    hasLoadedAudio.update { _ -> true }
                    checkFileLoaded()
                    selectAudioById(track.id)
                }
            }
            is VideoTrack.Internal -> {
                hasLoadedAudio.update { _ -> true }
                checkFileLoaded()
                selectAudioById(track.data.id)
            }
        }
    }

    fun onTrackLoadedFailure(url: String) {
        val subtitleIdx = externalSubtitleTracks.value.indexOfFirst {
            it.data.url == url
        }
        if (subtitleIdx != -1) {
            updateSubtitleTrackAt(subtitleIdx) {
                it.copy(state = TrackState.Error)
            }
            hasLoadedSubs.update { _ -> true }
            checkFileLoaded()
        }
        val audioIdx = externalAudioTracks.value.indexOfFirst {
            it.data.url == url
        }
        if (audioIdx != -1) {
            updateAudioTrackAt(audioIdx) {
                it.copy(state = TrackState.Error)
            }
            hasLoadedAudio.update { _ -> true }
            checkFileLoaded()
        }
    }

    fun onSubtitleTrackSelectChange() {
        val id = mpv.getPropertyInt("sid")
        val sid = mpv.getPropertyInt("secondary-sid")

        _externalSubtitleTracks.update { subtitleTracks ->
            subtitleTracks.map {
                it.copy(
                    mainSelection = when (it.id) {
                        null -> -1
                        id -> 0
                        sid -> 1
                        else -> -1
                    },
                )
            }
        }
    }

    private fun selectSubById(id: Int) {
        val selectedSubs = Pair(mpv.getPropertyInt("sid"), mpv.getPropertyInt("secondary-sid"))
        when (id) {
            selectedSubs.first -> Pair(selectedSubs.second, null)
            selectedSubs.second -> Pair(selectedSubs.first, null)
            else -> if (selectedSubs.first != null) Pair(selectedSubs.first, id) else Pair(id, null)
        }.let {
            it.second?.let { mpv.setPropertyInt("secondary-sid", it) }
                ?: mpv.setPropertyBoolean("secondary-sid", false)
            it.first?.let { mpv.setPropertyInt("sid", it) } ?: mpv.setPropertyBoolean("sid", false)
        }
    }

    private fun selectAudioById(id: Int) {
        // ANK: `force` was removed since we reset audio ID in `onTracksLoaded()`
        if (id == mpv.getPropertyInt("aid")) {
            mpv.setPropertyBoolean("aid", false)
        } else {
            mpv.setPropertyInt("aid", id)
        }
    }

    private fun updateSubtitleTrackAt(index: Int, transform: (VideoTrack.External) -> VideoTrack.External) {
        _externalSubtitleTracks.update { externalSubtitles ->
            externalSubtitles.toMutableList().apply {
                this[index] = transform(this[index])
            }
        }
    }

    private fun updateAudioTrackAt(index: Int, transform: (VideoTrack.External) -> VideoTrack.External) {
        _externalAudioTracks.update { externalAudio ->
            externalAudio.toMutableList().apply {
                this[index] = transform(this[index])
            }
        }
    }

    fun getChapterCount(): Int {
        return mpv.getPropertyInt("chapter-list/count") ?: 0
    }

    fun addTimestamps(timestamps: List<TimeStamp>) {
        if (timestamps.isEmpty()) return
        val current = (
            mpv.getPropertyNode("chapter-list")
                ?.toObject<List<ChapterNode>>(json) ?: emptyList()
            )
            .map {
                IndexedSegment(
                    name = it.chapterTitle,
                    start = it.time,
                    index = 0,
                    // ANK -->
                    chapterType = it.chapterType,
                    // ANK <--
                )
            }
        val merged = ChapterUtils.mergeChapters(current, timestamps, duration)

        val node = MPVNode.ArrayNode(
            merged.map { c ->
                MPVNode.MapNode(
                    value = mapOf(
                        "time" to MPVNode.DoubleNode(c.start.toDouble()),
                        "title" to MPVNode.StringNode(c.name),
                    ),
                )
            }.toTypedArray(),
        )
        mpv.setPropertyNode("chapter-list", node)
    }

    private fun updatePausedState() {
        if (pausedState.value == null) {
            _pausedState.update { _ -> paused }
        }
    }

    /**
     * Check when file has loaded and see if the player can be (un)paused.
     *
     * If external subs/audio tracks was selected, wait until mpv has fetched them.
     */
    fun checkFileLoaded() {
        if (isLoadingEpisode.value && hasLoadedSubs.value && hasLoadedAudio.value) {
            _isLoadingEpisode.update { _ -> false }
            pausedState.value?.let {
                if (it) {
                    pause()
                } else {
                    unpause()
                }
                _pausedState.update { _ -> null }
            }
        }
    }

    fun pauseUnpause() = mpv.command("cycle", "pause")
    fun pause() = mpv.setPropertyBoolean("pause", true)
    fun unpause() = mpv.setPropertyBoolean("pause", false)

    private val showStatusBar = playerPreferences.showSystemStatusBar().get()
    fun showControls() {
        if (sheetShown.value != Sheets.None ||
            panelShown.value != Panels.None ||
            dialogShown.value != Dialogs.None
        ) {
            return
        }
        if (showStatusBar) {
            viewModelScope.launch {
                eventChannel.send(Event.SetStatusBar(true))
            }
        }
        _controlsShown.update { true }
    }

    fun hideControls() {
        viewModelScope.launch {
            eventChannel.send(Event.SetStatusBar(false))
        }
        _controlsShown.update { false }
    }

    fun hideSeekBar() {
        _seekBarShown.update { false }
    }

    fun showSeekBar() {
        if (sheetShown.value != Sheets.None) return
        _seekBarShown.update { true }
    }

    fun lockControls() {
        _areControlsLocked.update { true }
    }

    fun unlockControls() {
        _areControlsLocked.update { false }
    }

    fun dismissSheet() {
        _dismissSheet.update { _ -> true }
    }

    private fun resetDismissSheet() {
        _dismissSheet.update { _ -> false }
    }

    fun showSheet(sheet: Sheets) {
        sheetShown.update { sheet }
        if (sheet == Sheets.None) {
            resetDismissSheet()
            showControls()
        } else {
            hideControls()
            panelShown.update { Panels.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showPanel(panel: Panels) {
        panelShown.update { panel }
        if (panel == Panels.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showDialog(dialog: Dialogs) {
        dialogShown.update { dialog }
        if (dialog == Dialogs.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            panelShown.update { Panels.None }
        }
    }

    fun seekBy(offset: Int, precise: Boolean = false) {
        mpv.command("seek", offset.toString(), if (precise) "relative+exact" else "relative")
    }

    fun seekTo(position: Int, precise: Boolean = true) {
        if (position !in 0..(mpv.getPropertyInt("duration") ?: 0)) return
        mpv.command("seek", position.toString(), if (precise) "absolute" else "absolute+keyframes")
    }

    fun changeBrightnessTo(
        brightness: Float,
    ) {
        currentBrightness.update { _ -> brightness.coerceIn(-0.75f, 1f) }
        viewModelScope.launch {
            eventChannel.send(Event.SetBrightness(brightness.coerceIn(0f, 1f)))
        }
    }

    fun displayBrightnessSlider() {
        isBrightnessSliderShown.update { true }
    }

    val maxVolume = audioManager.getMaxVolume()
    fun changeVolumeBy(change: Int) {
        val mpvVolume = mpv.getPropertyInt("volume")
        if ((volumeBoostCap ?: audioPreferences.volumeBoostCap().get()) > 0 && currentVolume.value == maxVolume) {
            if (mpvVolume == 100 && change < 0) changeVolumeTo(currentVolume.value + change)
            val finalMPVVolume = (mpvVolume?.plus(change))?.coerceAtLeast(100)
                // ANK -->
                ?: (currentVolume.value + change)
            // ANK <--
            if (finalMPVVolume in 100..(volumeBoostCap ?: audioPreferences.volumeBoostCap().get()) + 100) {
                changeMPVVolumeTo(finalMPVVolume)
                return
            }
        }
        changeVolumeTo(currentVolume.value + change)
    }

    fun changeVolumeTo(volume: Int) {
        val newVolume = volume.coerceIn(0..maxVolume)
        audioManager.setVolume(newVolume)
        currentVolume.update { newVolume }
    }

    fun changeMPVVolumeTo(volume: Int) {
        mpv.setPropertyInt("volume", volume)
    }

    fun displayVolumeSlider() {
        isVolumeSliderShown.update { true }
    }

    fun setAutoPlay(value: Boolean) {
        val textRes = if (value) {
            AYMR.strings.enable_auto_play
        } else {
            AYMR.strings.disable_auto_play
        }
        playerUpdate.update { PlayerUpdates.ShowTextResource(textRes) }
        playerPreferences.autoplayEnabled().set(value)
    }

    @Suppress("DEPRECATION")
    fun changeVideoAspect(aspect: VideoAspect) {
        viewModelScope.launch {
            eventChannel.send(Event.ChangeVideoAspect(aspect))
        }
    }

    fun setAspect(aspect: VideoAspect, pan: Double, ratio: Double) {
        mpv.setPropertyDouble("panscan", pan)
        mpv.setPropertyDouble("video-aspect-override", ratio)
        playerPreferences.aspectState().set(aspect)
        playerUpdate.update { PlayerUpdates.AspectRatio }
    }

    fun cycleScreenRotations() {
        viewModelScope.launch {
            eventChannel.send(Event.CycleRotations)
        }
    }

    fun handleLuaInvocation(property: String, value: String) {
        val data = value
            .removePrefix("\"")
            .removeSuffix("\"")
            .ifEmpty { return }

        when (property.substringAfterLast("/")) {
            "show_text" -> playerUpdate.update { PlayerUpdates.ShowText(data) }
            "toggle_ui" -> {
                when (data) {
                    "show" -> showControls()
                    "toggle" -> {
                        if (controlsShown.value) hideControls() else showControls()
                    }
                    "hide" -> {
                        sheetShown.update { Sheets.None }
                        panelShown.update { Panels.None }
                        dialogShown.update { Dialogs.None }
                        hideControls()
                    }
                }
            }
            "show_panel" -> {
                when (data) {
                    "subtitle_settings" -> showPanel(Panels.SubtitleSettings)
                    "subtitle_delay" -> showPanel(Panels.SubtitleDelay)
                    "audio_delay" -> showPanel(Panels.AudioDelay)
                    "video_filters" -> showPanel(Panels.VideoFilters)
                }
            }
            "set_button_title" -> {
                _primaryButtonTitle.update { _ -> data }
            }
            "reset_button_title" -> {
                _customButtons.value.firstOrNull { it.isFavorite }?.let {
                    setPrimaryCustomButtonTitle(it)
                }
            }
            "switch_episode" -> {
                when (data) {
                    "n" -> changeEpisode(false)
                    "p" -> changeEpisode(true)
                }
            }
            "launch_int_picker" -> {
                val (title, nameFormat, start, stop, step, pickerProperty) = data.split("|")
                val defaultValue = mpv.getPropertyInt(pickerProperty)
                    // ANK -->
                    ?: return
                // ANK <--
                showDialog(
                    Dialogs.IntegerPicker(
                        defaultValue = defaultValue,
                        minValue = start.toInt(),
                        maxValue = stop.toInt(),
                        step = step.toInt(),
                        nameFormat = nameFormat,
                        title = title,
                        onChange = { mpv.setPropertyInt(pickerProperty, it) },
                        onDismissRequest = { showDialog(Dialogs.None) },
                    ),
                )
            }
            "show_seek_text" -> {
                val (forward, text) = data.split("|", limit = 2)
                showSeekText(forward == "true", text)
            }
            "pause" -> {
                when (data) {
                    "pause" -> pause()
                    "unpause" -> unpause()
                    "pauseunpause" -> pauseUnpause()
                }
            }
            "seek_to_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekToWithText(seekValue.toInt(), text)
            }
            "seek_by_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekByWithText(seekValue.toInt(), text)
            }
            "seek_by" -> seekByWithText(data.toInt(), null)
            "seek_to" -> seekToWithText(data.toInt(), null)
            "toggle_button" -> {
                fun showButton() {
                    if (_primaryButton.value == null) {
                        _primaryButton.update {
                            customButtons.value.firstOrNull { it.isFavorite }
                        }
                    }
                }

                when (data) {
                    "show" -> showButton()
                    "hide" -> _primaryButton.update { null }
                    "toggle" -> if (_primaryButton.value == null) showButton() else _primaryButton.update { null }
                }
            }
            "software_keyboard" -> {
                viewModelScope.launch {
                    when (data) {
                        "show" -> eventChannel.send(Event.SetKeyboard(true))
                        "hide" -> eventChannel.send(Event.SetKeyboard(false))
                        "toggle" -> eventChannel.send(Event.ToggleKeyboard)
                    }
                }
            }
        }

        mpv.setPropertyString(property, "")
    }

    private operator fun <T> List<T>.component6(): T = get(5)

    private val doubleTapToSeekDuration = gesturePreferences.skipLengthPreference().get()
    private val preciseSeek = gesturePreferences.playerSmoothSeek().get()
    private val showSeekBar = gesturePreferences.showSeekBar().get()

    private fun showSeekText(isForward: Boolean, text: String) {
        _seekText.update { _ -> text }
        _isSeekingForwards.update { _ -> isForward }
        _doubleTapSeekAmount.update { _ -> if (isForward) 1 else -1 }
        if (showSeekBar) showSeekBar()
    }

    private fun seekToWithText(seekValue: Int, text: String?) {
        _isSeekingForwards.value = seekValue > 0
        _doubleTapSeekAmount.value = seekValue - (pos ?: return)
        _seekText.update { _ -> text }
        seekTo(seekValue, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    private fun seekByWithText(value: Int, text: String?) {
        _doubleTapSeekAmount.update {
            if ((value < 0 && it < 0) || (pos ?: return) + value > (duration ?: return)) 0 else it + value
        }
        _seekText.update { text }
        _isSeekingForwards.value = value > 0
        seekBy(value, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun updateSeekAmount(amount: Int) {
        _doubleTapSeekAmount.update { _ -> amount }
    }

    fun updateSeekText(value: String?) {
        _seekText.update { _ -> value }
    }

    fun leftSeek() {
        if ((pos ?: return) > 0) _doubleTapSeekAmount.value -= doubleTapToSeekDuration
        _isSeekingForwards.value = false
        seekBy(-doubleTapToSeekDuration, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun rightSeek() {
        if ((pos ?: return) < (duration ?: return)) {
            _doubleTapSeekAmount.value += doubleTapToSeekDuration
        }
        _isSeekingForwards.value = true
        seekBy(doubleTapToSeekDuration, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun resetHosterState() {
        _pausedState.update { _ -> false }
        _hosterState.update { _ -> emptyList() }
        _hosterList.update { _ -> emptyList() }
        _hosterExpandedList.update { _ -> emptyList() }
        _selectedHosterVideoIndex.update { _ -> Pair(-1, -1) }
    }

    fun changeEpisode(previous: Boolean, autoPlay: Boolean = false) {
        viewModelScope.launch {
            if (previous && !hasPreviousEpisode.value) {
                eventChannel.send(Event.ShowToast(AYMR.strings.no_prev_episode))
                return@launch
            }

            if (!previous && !hasNextEpisode.value) {
                eventChannel.send(Event.ShowToast(AYMR.strings.no_next_episode))
                return@launch
            }

            eventChannel.send(
                Event.ChangeEpisode(
                    episodeId = getAdjacentEpisodeId(previous = previous),
                    autoPlay = autoPlay,
                ),
            )
        }
    }

    fun handleLeftDoubleTap() {
        when (gesturePreferences.leftDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                leftSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                mpv.command("keypress", CustomKeyCodes.DoubleTapLeft.keyCode)
            }
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> changeEpisode(true)
        }
    }

    fun handleCenterDoubleTap() {
        when (gesturePreferences.centerDoubleTapGesture().get()) {
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                mpv.command("keypress", CustomKeyCodes.DoubleTapCenter.keyCode)
            }
            SingleActionGesture.Seek -> {}
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> {}
        }
    }

    fun handleRightDoubleTap() {
        when (gesturePreferences.rightDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                rightSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                mpv.command("keypress", CustomKeyCodes.DoubleTapRight.keyCode)
            }
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> changeEpisode(false)
        }
    }

    override fun onCleared() {
        if (currentEpisode.value != null) {
            saveWatchingProgress(currentEpisode.value!!)
            episodeToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }

        // ANK -->
        // `mpv` is owned by this (potentially retained) ViewModel, so it must only be
        // closed when the ViewModel itself is cleared, not on every activity destruction
        // (e.g. config changes), otherwise a recreated activity gets a dead MPV instance.
        mpv.close()
        // ANK <--
    }

    fun updateCastProgress(position: Float) {
        // ANK -->
        castPos = position.toInt()
        // ANK <--
    }

    fun resumeFromCast() {
        // ANK -->
        val lastPosition = castPos
        // ANK <--

        logcat { "Reanudando el video local desde: $lastPosition segundos" }

        if (lastPosition > 0) {
            seekTo(lastPosition) // Move the local player to the last position
        }
    }

    // ====== OLD ======

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    val incognitoMode: Boolean by lazy { getIncognitoState.await(currentSource.value?.id) }
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileReading().get()

    internal val relativeTime = uiPreferences.relativeTime().get()
    internal val dateFormat = uiPreferences.dateFormat().get()

    /**
     * The position in the current video. Used to restore from process kill.
     */
    private var episodePosition = savedState.get<Long>("episode_position")
        set(value) {
            savedState["episode_position"] = value
            field = value
        }

    /**
     * The current video's quality index. Used to restore from process kill.
     */
    private var qualityIndex = savedState.get<Pair<Int, Int>>("quality_index") ?: Pair(-1, -1)
        set(value) {
            savedState["quality_index"] = value
            field = value
        }

    /**
     * The episode id of the currently loaded episode. Used to restore from process kill.
     */
    private var episodeId = savedState.get<Long>("episode_id") ?: -1L
        set(value) {
            savedState["episode_id"] = value
            field = value
        }

    private var episodeToDownload: Download? = null

    private fun filterEpisodeList(
        // ANK -->
        anime: Anime,
        episodes: List<DomainEpisode>,
        animeMap: Map<Long, Anime>?,
        selectedEpisode: DomainEpisode,
    ): List<DomainEpisode> {
        fun isEpisodeDownloaded(episode: DomainEpisode): Boolean {
            val episodeAnime = animeMap?.get(episode.animeId) ?: anime
            return downloadManager.isEpisodeDownloaded(
                episodeName = episode.name,
                episodeScanlator = episode.scanlator,
                animeTitle = episodeAnime.ogTitle,
                sourceId = episodeAnime.source,
            )
        }

        val skipSeen = playerPreferences.skipSeen().get()
        val skipFiltered = playerPreferences.skipFiltered().get()

        return when {
            (skipSeen || skipFiltered) -> {
                // ANK <--
                val filteredEpisodes = episodes.filterNot {
                    // ANK -->
                    when {
                        skipSeen && it.seen -> true
                        skipFiltered -> {
                            // ANK <--
                            (anime.unseenFilterRaw == Anime.EPISODE_SHOW_SEEN && !it.seen) ||
                                (anime.unseenFilterRaw == Anime.EPISODE_SHOW_UNSEEN && it.seen) ||
                                // SY -->
                                (anime.downloadedFilterRaw == Anime.EPISODE_SHOW_DOWNLOADED && !isEpisodeDownloaded(it)) ||
                                (anime.downloadedFilterRaw == Anime.EPISODE_SHOW_NOT_DOWNLOADED && isEpisodeDownloaded(it)) ||
                                // SY <--
                                (anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_BOOKMARKED && !it.bookmark) ||
                                (anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_NOT_BOOKMARKED && it.bookmark) ||
                                (anime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_FILLERMARKED && !it.fillermark) ||
                                (anime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_NOT_FILLERMARKED && it.fillermark)
                            // ANK -->
                        }
                        else -> false
                    }
                }

                if (filteredEpisodes.any { it.id == selectedEpisode.id }) {
                    filteredEpisodes
                } else {
                    filteredEpisodes + listOf(selectedEpisode)
                }
            }
            else -> episodes
        }
        // ANK <--
    }

    fun getCurrentEpisodeIndex(): Int {
        return currentPlaylist.value.indexOfFirst { currentEpisode.value?.id == it.id }
    }

    private fun getAdjacentEpisodeId(previous: Boolean): Long {
        val newIndex = if (previous) getCurrentEpisodeIndex() - 1 else getCurrentEpisodeIndex() + 1

        return when {
            previous && getCurrentEpisodeIndex() == 0 -> -1L
            !previous && currentPlaylist.value.lastIndex == getCurrentEpisodeIndex() -> -1L
            else -> currentPlaylist.value.getOrNull(newIndex)?.id ?: -1L
        }
    }

    fun updateHasNextEpisode(value: Boolean) {
        _hasNextEpisode.update { _ -> value }
    }

    fun updateHasPreviousEpisode(value: Boolean) {
        _hasPreviousEpisode.update { _ -> value }
    }

    fun showEpisodeListDialog() {
        if (anime != null) {
            showDialog(Dialogs.EpisodeList)
        }
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active episode.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentEpisode = currentEpisode.value ?: return
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(currentEpisode)
        }
    }

    // ====== Initialize anime, episode, hoster, and video list ======

    fun updateIsLoadingHosters(value: Boolean) {
        _isLoadingHosters.update { _ -> value }
    }

    /**
     * Whether this viewModel is initialized with the correct episode.
     */
    private fun needsInit(animeId: Long, episodeId: Long): Boolean {
        return anime?.id != animeId || currentEpisode.value?.id != episodeId
    }

    data class InitResult(
        val hosterList: List<Hoster>?,
        val videoIndex: Pair<Int, Int>,
        val position: Long?,
    )

    private var currentHosterList: List<Hoster>? = null

    class ExceptionWithStringResource(
        message: String,
        val stringResource: StringResource,
    ) : Exception(message)

    suspend fun init(
        animeId: Long,
        initialEpisodeId: Long,
        hostList: String,
        hostIndex: Int,
        vidIndex: Int,
    ): Pair<InitResult, Result<Boolean>> {
        val defaultResult = InitResult(currentHosterList, qualityIndex, null)
        if (!needsInit(animeId, initialEpisodeId)) return Pair(defaultResult, Result.success(true))
        return try {
            val anime = getAnime.await(animeId)
            if (anime != null) {
                // SY -->
                sourceManager.isInitialized.first { it }
                val source = sourceManager.getOrStub(anime.source)
                val mergedReferences = if (source is MergedSource) {
                    getMergedReferencesById.await(anime.id)
                } else {
                    emptyList()
                }
                val mergedManga = if (source is MergedSource) {
                    getMergedMangaById.await(anime.id)
                        .associateBy { it.id }
                } else {
                    emptyMap()
                }
                _currentAnime.update { _ -> anime }
                animeTitle.update { _ -> anime.title }
                sourceManager.isInitialized.first { it }
                episodeId = initialEpisodeId

                updateEpisodeList(initEpisodeList(anime))

                val episode = currentPlaylist.value.first { it.id == episodeId }

                _currentEpisode.update { _ -> episode }
                _currentSource.update { _ -> source }

                updateEpisode(episode)

                _hasPreviousEpisode.update { _ -> getCurrentEpisodeIndex() != 0 }
                _hasNextEpisode.update { _ -> getCurrentEpisodeIndex() != currentPlaylist.value.size - 1 }

                // Write to mpv table
                val parentTitle = anime.parentId?.let { getAnime.await(it)?.title } ?: ""
                mpv.setPropertyString("user-data/current-anime/anime-title", anime.title)
                mpv.setPropertyString("user-data/current-anime/parent-title", parentTitle)
                mpv.setPropertyInt("user-data/current-anime/intro-length", getAnimeSkipIntroLength())
                mpv.setPropertyString(
                    "user-data/current-anime/category",
                    getCategories.await(anime.id).joinToString {
                        it.name
                    },
                )

                val currentEp = currentEpisode.value
                    ?: throw ExceptionWithStringResource("No episode loaded", AYMR.strings.no_episode_loaded)
                if (hostList.isNotBlank()) {
                    currentHosterList = hostList.toHosterList().ifEmpty {
                        currentHosterList = null
                        throw ExceptionWithStringResource(
                            "Hoster selected from empty list",
                            AYMR.strings.select_hoster_from_empty_list,
                        )
                    }
                    qualityIndex = Pair(hostIndex, vidIndex)
                } else {
                    EpisodeLoader.getHosters(
                        episode = currentEp.toDomainEpisode()!!,
                        anime = anime,
                        source = source,
                        sourceManager = sourceManager,
                        mergedReferences = mergedReferences,
                        mergedManga = mergedManga,
                    )
                        .takeIf { it.isNotEmpty() }
                        ?.also { currentHosterList = it }
                        ?: run {
                            currentHosterList = null
                            throw ExceptionWithStringResource("Hoster list is empty", AYMR.strings.no_hosters)
                        }
                }

                val result = InitResult(
                    hosterList = currentHosterList,
                    videoIndex = qualityIndex,
                    position = episodePosition,
                )
                Pair(result, Result.success(true))
            } else {
                // Unlikely but okay
                Pair(defaultResult, Result.success(false))
            }
        } catch (e: Throwable) {
            Pair(defaultResult, Result.failure(e))
        }
    }

    private fun updateEpisode(episode: Episode) {
        mediaTitle.update { _ -> episode.name }
        _isEpisodeOnline.update { _ -> isEpisodeOnline() == true }
        mpv.setPropertyDouble("user-data/current-anime/episode-number", episode.episode_number.toDouble())
    }

    /**
     * Episode list for the active anime. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private fun initEpisodeList(anime: Anime): List<Episode> {
        // ANK -->
        val (episodes, animeMap) = runBlocking {
            if (anime.source == MERGED_SOURCE_ID) {
                getMergedChaptersByMangaId.await(anime.id, applyFilter = true) to
                    getMergedMangaById.await(anime.id)
                        .associateBy { it.id }
            } else {
                getEpisodesByAnimeId.await(anime.id, applyFilter = true) to null
            }
        }

        val selectedEpisode = episodes.find { it.id == episodeId }
            ?: error("Requested episode of id $episodeId not found in episode list")

        val episodesForPlayer = filterEpisodeList(anime, episodes, animeMap, selectedEpisode)
        // ANK <--

        return episodesForPlayer
            .sortedWith(getEpisodeSort(anime, sortDescending = false))
            // ANK -->
            .run {
                if (playerPreferences.skipDupe().get()) {
                    removeDuplicates(selectedEpisode)
                } else {
                    this
                }
            }
            // ANK <--
            .run {
                if (basePreferences.downloadedOnly().get()) {
                    filterDownloaded(anime, animeMap)
                } else {
                    this
                }
            }
            .map { it.toDbEpisode() }
    }

    private var getHosterVideoLinksJob: Job? = null

    fun cancelHosterVideoLinksJob() {
        getHosterVideoLinksJob?.cancel()
    }

    // ANK -->
    /**
     * Set when a video selection had to be postponed because every ready candidate was
     * exhausted while some hosters were still resolving. The selection is retried as
     * those hosters settle, so playback doesn't stay idle forever.
     */
    private val hasPendingVideoFallback = AtomicBoolean(false)

    /**
     * Serializes the pending fallback attempts, which can be triggered concurrently by
     * every hoster that finishes resolving.
     */
    private val pendingVideoFallbackMutex = Mutex()

    /**
     * Postpone a video selection until the hosters that are still resolving have settled.
     *
     * @return true if the fallback was recorded, false if there is nothing left to wait
     * for and the caller has to handle the failure itself.
     */
    private fun recordPendingVideoFallback(source: AnimeSource?): Boolean {
        if (_hosterState.value.none { it is HosterState.Loading }) return false

        hasPendingVideoFallback.set(true)
        // The hoster may have settled between the check above and the flag being set,
        // so always kick off an attempt instead of relying on a later notification.
        viewModelScope.launchIO { retryPendingVideoFallback(source) }
        return true
    }

    /**
     * Retry a selection recorded by [recordPendingVideoFallback]. Called whenever a
     * hoster finishes resolving; once none are left, the failure is surfaced to the
     * player instead of being postponed again.
     */
    private suspend fun retryPendingVideoFallback(source: AnimeSource?) {
        pendingVideoFallbackMutex.withLock {
            if (!hasPendingVideoFallback.get()) return

            val (hosterIdx, videoIdx) = HosterLoader.selectBestVideo(hosterState.value)
            val loaded = if (hosterIdx == -1) {
                false
            } else {
                val video = (hosterState.value[hosterIdx] as HosterState.Ready).videoList[videoIdx]
                try {
                    loadVideo(source, video, hosterIdx, videoIdx)
                } catch (e: ExceptionWithStringResource) {
                    hasPendingVideoFallback.set(false)
                    eventChannel.send(Event.SetVideoLoadError(e))
                    return
                }
            }

            when {
                loaded -> hasPendingVideoFallback.set(false)
                // Some hosters haven't settled yet, so keep waiting for them
                _hosterState.value.any { it is HosterState.Loading } -> {}
                else -> {
                    hasPendingVideoFallback.set(false)
                    eventChannel.send(
                        Event.SetVideoLoadError(
                            ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos),
                        ),
                    )
                }
            }
        }
    }
    // ANK <--

    /**
     * Set the video list for hosters.
     */
    fun loadHosters(source: AnimeSource, hosterList: List<Hoster>, hosterIndex: Int, videoIndex: Int) {
        val hasFoundPreferredVideo = AtomicBoolean(false)
        // ANK --> Don't carry a postponed selection over from the previous episode
        hasPendingVideoFallback.set(false)
        // ANK <--

        _hosterList.update { _ -> hosterList }
        _hosterExpandedList.update { _ ->
            List(hosterList.size) { true }
        }

        getHosterVideoLinksJob?.cancel()
        getHosterVideoLinksJob = viewModelScope.launchIO {
            _hosterState.update { _ ->
                hosterList.map { hoster ->
                    if (hoster.lazy) {
                        HosterState.Idle(hoster.hosterName)
                    } else if (hoster.videoList == null) {
                        HosterState.Loading(hoster.hosterName)
                    } else {
                        val videoList = hoster.videoList!!
                        HosterState.Ready(
                            hoster.hosterName,
                            videoList,
                            List(videoList.size) { Video.State.Queue },
                        )
                    }
                }
            }

            try {
                coroutineScope {
                    hosterList.mapIndexed { hosterIdx, hoster ->
                        async {
                            val hosterState = EpisodeLoader.loadHosterVideos(source, hoster)

                            _hosterState.updateAt(hosterIdx, hosterState)

                            if (hosterState is HosterState.Ready) {
                                if (hosterIdx == hosterIndex) {
                                    hosterState.videoList.getOrNull(videoIndex)?.let {
                                        hasFoundPreferredVideo.set(true)
                                        val success = loadVideo(source, it, hosterIndex, videoIndex)
                                        if (!success) {
                                            hasFoundPreferredVideo.set(false)
                                        }
                                    }
                                }

                                val prefIndex = hosterState.videoList.indexOfFirst { it.preferred }
                                if (prefIndex != -1 && hosterIndex == -1) {
                                    if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                                        if (selectedHosterVideoIndex.value == Pair(-1, -1)) {
                                            val success =
                                                loadVideo(
                                                    source,
                                                    hosterState.videoList[prefIndex],
                                                    hosterIdx,
                                                    prefIndex,
                                                )
                                            if (!success) {
                                                hasFoundPreferredVideo.set(false)
                                            }
                                        }
                                    }
                                }
                            }

                            // ANK --> A postponed selection may be waiting on this hoster
                            retryPendingVideoFallback(source)
                            // ANK <--
                        }
                    }.awaitAll()

                    if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                        val (hosterIdx, videoIdx) = HosterLoader.selectBestVideo(hosterState.value)
                        if (hosterIdx == -1) {
                            throw ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos)
                        }

                        val video = (hosterState.value[hosterIdx] as HosterState.Ready).videoList[videoIdx]

                        loadVideo(source, video, hosterIdx, videoIdx)
                    }

                    // ANK --> Nothing left to wait for; resolve any postponed selection
                    retryPendingVideoFallback(source)
                    // ANK <--
                }
            } catch (e: CancellationException) {
                _hosterState.update { _ ->
                    hosterList.map { HosterState.Idle(it.hosterName) }
                }

                throw e
            }
        }
    }

    fun setIsStopped(value: Boolean) {
        _isStopped.update { _ -> value }
    }

    fun setCurrentVideoError() {
        val (hosterIdx, videoIdx) = selectedHosterVideoIndex.value
        val currentHosterState = (hosterState.value[hosterIdx] as? HosterState.Ready) ?: return
        val currentVideo = currentHosterState.videoList[videoIdx]

        _hosterState.updateAt(
            hosterIdx,
            currentHosterState.getChangedAt(
                videoIdx,
                currentVideo,
                // ANK -->
                Video.State.Error(Exception("MPV Player video error")),
                // ANK <--
            ),
        )
    }

    fun loadBestVideo(): Boolean {
        val source = currentSource.value ?: return false
        val (hosterIdx, videoIdx) = HosterLoader.selectBestVideo(hosterState.value)
        if (hosterIdx == -1) {
            // ANK -->
            // A hoster still resolving (Loading) might still produce a usable candidate,
            // so postpone the selection instead of reporting failure. Returning true
            // without recording it would leave playback idle forever, since loadHosters()
            // already made its own selection and won't make another one.
            return recordPendingVideoFallback(source)
            // ANK <--
        }
        val newVideo = (hosterState.value[hosterIdx] as HosterState.Ready).videoList[videoIdx]
        viewModelScope.launchIO {
            // ANK -->
            // loadVideo() can still exhaust every fallback candidate deep in its own
            // recursion and throw; make sure that surfaces as a graceful close instead
            // of an uncaught exception in this coroutine.
            try {
                if (!loadVideo(source, newVideo, hosterIdx, videoIdx)) {
                    // loadVideo() bailed out because other hosters are still resolving
                    recordPendingVideoFallback(source)
                }
            } catch (e: ExceptionWithStringResource) {
                eventChannel.send(Event.SetVideoLoadError(e))
            }
            // ANK <--
        }
        return true
    }

    private suspend fun loadVideo(source: AnimeSource?, video: Video, hosterIndex: Int, videoIndex: Int): Boolean {
        val selectedHosterState = (_hosterState.value[hosterIndex] as? HosterState.Ready) ?: return false
        updateIsLoadingEpisode(true)

        val oldSelectedIndex = _selectedHosterVideoIndex.value
        _selectedHosterVideoIndex.update { _ -> Pair(hosterIndex, videoIndex) }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, video, Video.State.LoadVideo),
        )

        // Pause until everything has loaded
        updatePausedState()
        pause()

        val resolvedVideo = if (selectedHosterState.videoState[videoIndex] != Video.State.Ready) {
            HosterLoader.getResolvedVideo(source, video)
        } else {
            video
        }

        if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
            if (currentVideo.value == null) {
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.Error(ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos))),
                )

                val (newHosterIdx, newVideoIdx) = HosterLoader.selectBestVideo(hosterState.value)
                if (newHosterIdx == -1) {
                    if (_hosterState.value.any { it is HosterState.Loading }) {
                        _selectedHosterVideoIndex.update { _ -> Pair(-1, -1) }
                        return false
                    } else {
                        throw ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos)
                    }
                }

                val newVideo = (hosterState.value[newHosterIdx] as HosterState.Ready).videoList[newVideoIdx]

                return loadVideo(source, newVideo, newHosterIdx, newVideoIdx)
            } else {
                _selectedHosterVideoIndex.update { _ -> oldSelectedIndex }
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.Error(ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos))),
                )
                return false
            }
        }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, resolvedVideo, Video.State.Ready),
        )

        _currentVideo.update { _ -> resolvedVideo }
        if (hasLoadedTracks.value) {
            clearTracks()
        }

        qualityIndex = Pair(hosterIndex, videoIndex)

        // ANK --> Playback resumed, so drop any postponed selection
        hasPendingVideoFallback.set(false)
        // ANK <--

        eventChannel.send(Event.SetVideo(resolvedVideo))
        return true
    }

    fun onVideoClicked(hosterIndex: Int, videoIndex: Int) {
        val hosterState = _hosterState.value[hosterIndex] as? HosterState.Ready
        val video = hosterState?.videoList
            ?.getOrNull(videoIndex)
            ?: return // Shouldn't happen, but just in case™

        val videoState = hosterState.videoState
            .getOrNull(videoIndex)
            ?: return

        if (videoState is Video.State.Error) {
            return
        }

        viewModelScope.launchIO {
            val success = loadVideo(currentSource.value, video, hosterIndex, videoIndex)
            if (success) {
                if (sheetShown.value == Sheets.QualityTracks) {
                    dismissSheet()
                }
            }
        }
    }

    fun onHosterClicked(index: Int) {
        when (hosterState.value[index]) {
            is HosterState.Ready -> {
                _hosterExpandedList.updateAt(index, !_hosterExpandedList.value[index])
            }
            is HosterState.Idle -> {
                val hosterName = hosterList.value[index].hosterName
                _hosterState.updateAt(index, HosterState.Loading(hosterName))

                viewModelScope.launchIO {
                    val hosterState = EpisodeLoader.loadHosterVideos(
                        source = currentSource.value!!,
                        hoster = hosterList.value[index],
                        force = true,
                    )
                    _hosterState.updateAt(index, hosterState)

                    // ANK --> A postponed selection may be waiting on this hoster
                    retryPendingVideoFallback(currentSource.value)
                    // ANK <--
                }
            }
            is HosterState.Loading, is HosterState.Error -> {}
        }
    }

    private fun <T> MutableStateFlow<List<T>>.updateAt(index: Int, newValue: T) {
        this.update { values ->
            values.toMutableList().apply {
                this[index] = newValue
            }
        }
    }

    data class EpisodeLoadResult(
        val hosterList: List<Hoster>?,
        val episodeTitle: String,
        val source: AnimeSource,
    )

    suspend fun loadEpisode(episodeId: Long?): EpisodeLoadResult? {
        val anime = anime ?: return null
        val source = sourceManager.getOrStub(anime.source)

        val chosenEpisode = currentPlaylist.value.firstOrNull { ep -> ep.id == episodeId } ?: return null

        _currentEpisode.update { _ -> chosenEpisode }
        updateEpisode(chosenEpisode)

        return withIOContext {
            try {
                val currentEpisode =
                    currentEpisode.value
                        ?: throw ExceptionWithStringResource("No episode loaded", AYMR.strings.no_episode_loaded)
                currentHosterList = EpisodeLoader.getHosters(
                    currentEpisode.toDomainEpisode()!!,
                    anime,
                    source,
                )

                this@PlayerViewModel.episodeId = currentEpisode.id!!
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { e.message ?: "Error getting links" }
            }

            EpisodeLoadResult(
                hosterList = currentHosterList,
                episodeTitle = anime.title + " - " + chosenEpisode.name,
                source = source,
            )
        }
    }

    /**
     * Called every time a second is reached in the player. Used to mark the flag of episode being
     * seen, update tracking services, enqueue downloaded episode deletion and download next episode.
     */
    fun onSecondReached(position: Long) {
        // ANK -->
        val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
        val isSyncEnabled = syncPreferences.isSyncEnabled()
        // ANK <--

        if (isLoadingEpisode.value) return
        val currentEp = currentEpisode.value ?: return
        if (episodeId == -1L) return
        val dur = duration ?: return
        if (dur == 0) return

        // Set netflix-style timeout
        netflixTimeout.value?.let {
            if (it > 0) {
                netflixTimeout.value = it - 1
            } else {
                onSkipIntro()
            }
        }

        val seconds = position * 1000L
        val totalSeconds = dur * 1000L
        currentEp.total_seconds = totalSeconds

        episodePosition = seconds

        // ANK -->
        if (!incognitoMode) {
            // Save last second seen and mark as seen if needed
            currentEp.last_second_seen = seconds

            val progress = playerPreferences.progressPreference().get()
            if (seconds >= totalSeconds * progress) {
                updateEpisodeProgressOnComplete(currentEp)

                // SY -->
                // Check if syncing is enabled for episode seen:
                if (isSyncEnabled && syncTriggerOpt.syncOnEpisodeSeen) {
                    SyncDataJob.startNow(Injekt.get<Application>())
                }
                // SY <--
            }
            // ANK <--

            saveWatchingProgress(currentEp)

            // SY -->
            // Check if syncing is enabled for episode open:
            if (isSyncEnabled && syncTriggerOpt.syncOnEpisodeOpen && currentEp.last_second_seen == 0L) {
                SyncDataJob.startNow(Injekt.get<Application>())
            }
            // SY <--
        }

        val inDownloadRange = seconds.toDouble() / totalSeconds > 0.35
        if (inDownloadRange) {
            downloadNextEpisodes()
        }
    }

    private fun updateEpisodeProgressOnComplete(currentEp: Episode) {
        currentEp.seen = true
        updateTrackEpisodeSeen(currentEp)
        deleteEpisodeIfNeeded(currentEp)

        val markDuplicateAsSeen = libraryPreferences.markDuplicateReadChapterAsRead().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!markDuplicateAsSeen) return

        val duplicateUnseenEpisodes = unfilteredEpisodeList
            .mapNotNull { episode ->
                if (
                    !episode.seen &&
                    episode.isRecognizedNumber &&
                    episode.episodeNumber.toFloat() == currentEp.episode_number
                ) {
                    EpisodeUpdate(id = episode.id, read = true)
                        // KMK -->
                        .also { deleteDupChapterIfNeeded(episode.copy(read = true).toDbEpisode()) }
                    // KMK <--
                } else {
                    null
                }
            }

        if (duplicateUnseenEpisodes.isNotEmpty()) {
            viewModelScope.launchNonCancellable {
                updateEpisode.awaitAll(duplicateUnseenEpisodes)
            }
        }
    }

    private fun downloadNextEpisodes() {
        if (downloadAheadAmount == 0) return
        val anime = anime ?: return

        // Only download ahead if current + next episode is already downloaded too to avoid jank
        if (getCurrentEpisodeIndex() == currentPlaylist.value.lastIndex) return
        val currentEpisode = currentEpisode.value ?: return

        val nextEpisode = currentPlaylist.value[getCurrentEpisodeIndex() + 1]

        viewModelScope.launchIO {
            val episodesAreDownloaded =
                EpisodeLoader.isDownload(currentEpisode.toDomainEpisode()!!, anime) &&
                    EpisodeLoader.isDownload(nextEpisode.toDomainEpisode()!!, anime)

            if (!episodesAreDownloaded) return@launchIO

            val episodesToDownload = getNextChapters.await(anime.id, nextEpisode.id!!)
                .run {
                    if (playerPreferences.skipDupe().get()) {
                        removeDuplicates(nextEpisode.toDomainEpisode()!!)
                    } else {
                        this
                    }
                }
                .take(downloadAheadAmount)
            downloadManager.downloadEpisodes(anime, episodesToDownload)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last episode actually exists.
     * If both conditions are satisfied enqueues episode for delete.
     *
     * This deletes chapters from reading list (filtered, unduplicated if any set).
     *
     * @param chosenEpisode current episode, which is going to be marked as seen.
     */
    private fun deleteEpisodeIfNeeded(chosenEpisode: Episode) {
        // Determine which episode should be deleted and enqueue
        val currentEpisodePosition = currentPlaylist.value.indexOf(chosenEpisode)
        val removeAfterSeenSlots = downloadPreferences.removeAfterReadSlots().get()
        val episodeToDelete = currentPlaylist.value.getOrNull(
            currentEpisodePosition - removeAfterSeenSlots,
        )
        // If episode is completely seen no need to download it
        episodeToDownload = null

        // Check if deleting option is enabled and episode exists
        if (removeAfterSeenSlots != -1 && episodeToDelete != null) {
            enqueueDeleteSeenEpisodes(episodeToDelete)
        }
    }

    // KMK -->
    /**
     * Deletes duplicate chapters when `removeAfterReadSlots` = "Last read chapter" (0).
     *
     * Ignore the case where `removeAfterReadSlots` > 0 while `skipDupe` = true as we don't know
     * where the chapters to be deleted are in the filtered [filterEpisodeList].
     *
     * For the case where `skipDupe` = false, chapters at should be deleted normally by [deleteEpisodeIfNeeded]
     * based on the `removeAfterReadSlots` offset while the user is reading sequentially.
     */
    private fun deleteDupChapterIfNeeded(chosenEpisode: Episode) {
        val removeAfterSeenSlots = downloadPreferences.removeAfterReadSlots().get()
        if (removeAfterSeenSlots != 0) return
        enqueueDeleteSeenEpisodes(chosenEpisode)
    }
    // KMK <--

    fun saveCurrentEpisodeWatchingProgress() {
        currentEpisode.value?.let { saveWatchingProgress(it) }
    }

    /**
     * Called when episode is changed in player or when activity is paused.
     */
    private fun saveWatchingProgress(episode: Episode) {
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(episode)
            saveEpisodeHistory(episode)
        }
    }

    /**
     * Saves this [episode] progress (last second seen and whether it's seen).
     * If incognito mode isn't on
     */
    private suspend fun saveEpisodeProgress(episode: Episode) {
        if (!incognitoMode) {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episode.id!!,
                    read = episode.seen,
                    bookmark = episode.bookmark,
                    fillermark = episode.fillermark,
                    lastPageRead = episode.last_second_seen,
                    totalPages = episode.total_seconds,
                ),
            )
        }
    }

    /**
     * Saves this [episode] last seen history if incognito mode isn't on.
     */
    private suspend fun saveEpisodeHistory(episode: Episode) {
        if (!incognitoMode) {
            val episodeId = episode.id!!
            val seenAt = Date()
            upsertHistory.await(
                HistoryUpdate(episodeId, seenAt, 0),
            )
        }
    }

    /**
     * Bookmarks the currently active episode.
     */
    fun bookmarkEpisode(episodeId: Long?, bookmarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId!!,
                    bookmark = bookmarked,
                ),
            )
        }
    }

    /**
     * Fillermarks the currently active episode.
     */
    fun fillermarkEpisode(episodeId: Long?, fillermarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId!!,
                    fillermark = fillermarked,
                ),
            )
        }
    }

    fun takeScreenshot(cachePath: String, showSubtitles: Boolean): InputStream? {
        val filename = cachePath + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
        val subtitleFlag = if (showSubtitles) "subtitles" else "video"

        mpv.command("screenshot-to-file", filename, subtitleFlag)
        val tempFile = File(filename).takeIf { it.exists() } ?: return null
        val newFile = File("$cachePath/mpv_screenshot.png")

        newFile.delete()
        tempFile.renameTo(newFile)
        return newFile.takeIf { it.exists() }?.inputStream()
    }

    /**
     * Saves the screenshot on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = anime ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        // Pictures directory.
        val relativePath = if (playerPreferences.folderPerAnime().get()) {
            DiskUtil.buildValidFilename(anime.title)
        } else {
            ""
        }

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Pictures.create(relativePath),
                    ),
                )
                notifier.onComplete(uri)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the screenshot and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file, and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = anime ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        try {
            viewModelScope.launchIO {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(Event.ShareImage(uri, seconds))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Sets the screenshot as art and notifies the UI of the result.
     */
    fun setAsCover(artType: ArtType, imageStream: () -> InputStream) {
        val anime = anime ?: return
        val episode = currentEpisode.value ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                when (artType) {
                    ArtType.Cover -> anime.editCover(Injekt.get(), imageStream())
                    ArtType.Background -> anime.editBackground(Injekt.get(), imageStream())
                    ArtType.Thumbnail -> episode.editThumbnail(anime, Injekt.get(), imageStream())
                }

                if (anime.isLocal() || anime.favorite) {
                    SetAsCover.Success
                } else {
                    SetAsCover.AddToLibraryFirst
                }
            } catch (_: Exception) {
                SetAsCover.Error
            }
            eventChannel.send(Event.SetCoverResult(result, artType))
        }
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    private fun updateTrackEpisodeSeen(episode: Episode) {
        if (incognitoMode) return
        if (!trackPreferences.autoUpdateTrack().get()) return

        val anime = anime ?: return
        val context = Injekt.get<Application>()

        viewModelScope.launchNonCancellable {
            trackEpisode.await(context, anime.id, episode.episode_number.toDouble())
        }
    }

    /**
     * Enqueues this [episode] to be deleted when [deletePendingEpisodes] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteSeenEpisodes(episode: Episode) {
        if (!episode.seen) return
        val anime = anime ?: return
        viewModelScope.launchNonCancellable {
            downloadManager.enqueueEpisodesToDelete(listOf(episode.toDomainEpisode()!!), anime)
        }
    }

    /**
     * Deletes all the pending episodes. This operation will run in a background thread and errors
     * are ignored.
     */
    fun deletePendingEpisodes() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingEpisodes()
        }
    }

    /**
     * Returns the skipIntroLength used by this anime or the default one.
     */
    private fun getAnimeSkipIntroLength(): Int {
        val default = gesturePreferences.defaultIntroLength().get()
        val anime = anime ?: return default
        val skipIntroLength = anime.skipIntroLength
        val skipIntroDisable = anime.skipIntroDisable
        return when {
            skipIntroDisable -> 0
            skipIntroLength <= 0 -> default
            else -> anime.skipIntroLength
        }
    }

    /**
     * Updates the skipIntroLength for the open anime.
     */
    fun setAnimeSkipIntroLength(skipIntroLength: Long) {
        val anime = anime ?: return
        if (!anime.favorite) return
        // Skip unnecessary database operation
        if (skipIntroLength == getAnimeSkipIntroLength().toLong()) return
        viewModelScope.launchIO {
            setAnimeViewerFlags.awaitSetSkipIntroLength(anime.id, skipIntroLength)
            _currentAnime.update { _ -> getAnime.await(anime.id) }
        }
    }

    /**
     * Generate a filename for the given [anime] and [timePos]
     */
    private fun generateFilename(
        anime: Anime,
        timePos: String,
    ): String? {
        val episode = currentEpisode.value ?: return null
        val filenameSuffix = " - $timePos"
        return DiskUtil.buildValidFilename(
            "${anime.title} - ${episode.name}".takeBytes(
                DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
            ),
        ) + filenameSuffix
    }

    /**
     * Returns the response of the AniSkipApi for this episode.
     * just works if tracking is enabled.
     */
    suspend fun aniSkipResponse(playerDuration: Int?): List<TimeStamp>? {
        val animeId = anime?.id ?: return null
        val trackerManager = Injekt.get<TrackerManager>()
        var malId: Long?
        val episodeNumber = currentEpisode.value?.episode_number?.toInt() ?: return null
        if (getTracks.await(animeId).isEmpty()) {
            logcat { "AniSkip: No tracks found for anime $animeId" }
            return null
        }

        getTracks.await(animeId).forEach { track ->
            val tracker = trackerManager.get(track.trackerId)
            malId = when (tracker) {
                is MyAnimeList -> track.remoteId
                is Anilist -> AniSkipApi().getMalIdFromAL(track.remoteId)
                else -> null
            }
            val duration = playerDuration ?: return null
            return malId?.let {
                AniSkipApi().getResult(it.toInt(), episodeNumber, duration.toLong())
            }
        }
        return null
    }

    val introSkipEnabled = playerPreferences.enableSkipIntro().get()
    private val autoSkip = playerPreferences.autoSkipIntro().get()
    private val netflixStyle = playerPreferences.enableNetflixStyleIntroSkip().get()

    private val defaultWaitingTime = playerPreferences.waitingTimeIntroSkip().get()

    fun onChapterChanged(chapterIndex: Int?) {
        if (chapterIndex == null) return
        if (!introSkipEnabled) return

        val chapterList = (mpv.getPropertyNode("chapter-list")?.toObject<List<ChapterNode>>(json) ?: emptyList())
        val chapter = chapterList.getOrNull(chapterIndex) ?: return
        val chapterType = chapter.chapterType

        if (chapterType == ChapterType.Other) {
            _skipIntroText.update { _ -> null }
            netflixTimeout.update { _ -> null }
        } else {
            if (netflixStyle) {
                // show a toast with the seconds before the skip
                eventChannel.trySend(
                    Event.ShowToastString(
                        "Skip Intro: ${context.applicationContext.stringResource(
                            AYMR.strings.player_aniskip_dontskip_toast,
                            chapter.chapterTitle,
                            defaultWaitingTime,
                        )}",
                    ),
                )
                _skipIntroText.update { _ ->
                    context.applicationContext.stringResource(AYMR.strings.player_aniskip_dontskip)
                }
                netflixTimeout.update { _ -> defaultWaitingTime }
            } else if (autoSkip) {
                skipIntro(chapter.chapterTitle)
            } else {
                updateSkipIntroButton(chapterType)
            }
        }
    }

    private fun skipIntro(chapterName: String) {
        mpv.command("add", "chapter", "1")
        showSeekText(true, context.applicationContext.stringResource(AYMR.strings.player_intro_skipped, chapterName))
    }

    private fun updateSkipIntroButton(chapterType: ChapterType) {
        val skipButtonString = chapterType.getStringRes()

        _skipIntroText.update { _ ->
            skipButtonString?.let {
                context.applicationContext.stringResource(
                    AYMR.strings.player_skip_action,
                    context.applicationContext.stringResource(skipButtonString),
                )
            }
        }
    }

    fun onSkipIntro() {
        val chapterIndex = mpv.getPropertyInt("chapter") ?: return
        val chapterList = (mpv.getPropertyNode("chapter-list")?.toObject<List<ChapterNode>>(json) ?: emptyList())
        val chapter = chapterList.getOrNull(chapterIndex) ?: return

        if ((netflixTimeout.value ?: 0) > 0 && netflixStyle) {
            netflixTimeout.update { _ -> null }
            updateSkipIntroButton(chapter.chapterType)
            return
        }

        netflixTimeout.update { _ -> null }
        skipIntro(chapter.chapterTitle)
    }

    fun setPrimaryCustomButtonTitle(button: CustomButton) {
        _primaryButtonTitle.update { _ -> button.name }
    }

    sealed class Event {
        data class SetCoverResult(val result: SetAsCover, val artType: ArtType) : Event()
        data class SavedImage(val result: SaveImageResult) : Event()
        data class ShareImage(val uri: Uri, val seconds: String) : Event()
        data class ShowToast(val stringResource: StringResource) : Event()
        data class ShowToastString(val string: String) : Event()
        data class ChangeEpisode(val episodeId: Long, val autoPlay: Boolean) : Event()
        data class SetVideo(val video: Video?) : Event()
        data class SetStatusBar(val show: Boolean) : Event()
        data class SetBrightness(val brightness: Float) : Event()
        data class ChangeVideoAspect(val aspect: VideoAspect) : Event()
        data object CycleRotations : Event()
        data object ToggleKeyboard : Event()
        data class SetKeyboard(val show: Boolean) : Event()
        // ANK -->
        data class SetVideoLoadError(val error: Throwable) : Event()
        // ANK <--
    }
}

fun CustomButton.execute(mpv: MPV) {
    mpv.command("script-message", "call_button_$id")
}

fun CustomButton.executeLongPress(mpv: MPV) {
    mpv.command("script-message", "call_button_${id}_long")
}

fun Float.normalize(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    return (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
}
