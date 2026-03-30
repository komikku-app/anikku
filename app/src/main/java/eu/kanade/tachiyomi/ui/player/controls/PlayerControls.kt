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

package eu.kanade.tachiyomi.ui.player.controls

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.graphics.toColorInt
import eu.kanade.presentation.theme.playerRippleConfiguration
import eu.kanade.tachiyomi.ui.player.CastManager
import eu.kanade.tachiyomi.ui.player.DebandSettings
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.Decoder.Companion.getDecoderFromValue
import eu.kanade.tachiyomi.ui.player.Dialogs
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.PlayerUpdates
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.VideoAspect
import eu.kanade.tachiyomi.ui.player.VideoFilters
import eu.kanade.tachiyomi.ui.player.VideoTrack
import eu.kanade.tachiyomi.ui.player.cast.components.CastSheet
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessOverlay
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessSlider
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.components.SeekbarWithTimers
import eu.kanade.tachiyomi.ui.player.controls.components.TextPlayerUpdate
import eu.kanade.tachiyomi.ui.player.controls.components.VolumeSlider
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubColorType
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubtitlesBorderStyle
import eu.kanade.tachiyomi.ui.player.controls.components.panels.resetColors
import eu.kanade.tachiyomi.ui.player.controls.components.panels.resetTypography
import eu.kanade.tachiyomi.ui.player.controls.components.panels.toColorHexString
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.toFixed
import eu.kanade.tachiyomi.ui.player.execute
import eu.kanade.tachiyomi.ui.player.executeLongPress
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioChannels
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitleAssOverride
import eu.kanade.tachiyomi.ui.player.settings.SubtitleJustification
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.deleteAndGet
import tachiyomi.core.common.preference.minusAssign
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

@Suppress("CompositionLocalAllowlist")
val LocalPlayerButtonsClickEvent = staticCompositionLocalOf { {} }

@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    castManager: CastManager,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCastSheet by remember { mutableStateOf(false) }
    val castState by castManager.castState.collectAsState()

    val spacing = MaterialTheme.padding
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val advancedPreferences = remember { Injekt.get<AdvancedPlayerPreferences>() }
    val decoderPreferences = remember { Injekt.get<DecoderPreferences>() }
    val gesturePreferences = remember { Injekt.get<GesturePreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }
    val interactionSource = remember { MutableInteractionSource() }

    val controlsShown by viewModel.controlsShown.collectAsState()
    val areControlsLocked by viewModel.areControlsLocked.collectAsState()
    val seekBarShown by viewModel.seekBarShown.collectAsState()
    val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsState()
    val isStopped by viewModel.isStopped.collectAsState()
    val pausedForCache by viewModel.mpv.propFlow<Boolean>("paused-for-cache").collectAsState()
    val coreIdle by viewModel.mpv.propFlow<Boolean>("core-idle").collectAsState()
    val paused by viewModel.mpv.propFlow<Boolean>("pause").collectAsState()
    val duration by viewModel.mpv.propFlow<Int>("duration").collectAsState()
    val position by viewModel.mpv.propFlow<Int>("time-pos").collectAsState()
    val playbackSpeed by viewModel.mpv.propFlow<Float>("speed").collectAsState()
    val gestureSeekAmount by viewModel.gestureSeekAmount.collectAsState()
    val doubleTapSeekAmount by viewModel.doubleTapSeekAmount.collectAsState()
    val seekText by viewModel.seekText.collectAsState()
    val currentChapter by viewModel.mpv.propFlow<Int>("chapter").collectAsState()
    val mpvDecoder by viewModel.mpv.propFlow<String>("hwdec-current").collectAsState()
    val decoder by remember { derivedStateOf { getDecoderFromValue(mpvDecoder ?: "auto") } }
    val chapters by viewModel.chapters.collectAsState(persistentListOf())
    val currentBrightness by viewModel.currentBrightness.collectAsState()

    val playerTimeToDisappear by playerPreferences.playerTimeToDisappear().collectAsState()
    var isSeeking by remember { mutableStateOf(false) }
    var resetControls by remember { mutableStateOf(true) }

    val customButtons by viewModel.customButtons.collectAsState()
    val customButton by viewModel.primaryButton.collectAsState()

    LaunchedEffect(
        controlsShown,
        paused,
        isSeeking,
        resetControls,
    ) {
        if (controlsShown && paused == false && !isSeeking) {
            delay(playerTimeToDisappear.toLong())
            viewModel.hideControls()
        }
    }

    val transparentOverlay by animateFloatAsState(
        if (controlsShown && !areControlsLocked) .8f else 0f,
        animationSpec = playerControlsExitAnimationSpec(),
        label = "controls_transparent_overlay",
    )
    GestureHandler(
        viewModel = viewModel,
        interactionSource = interactionSource,
    )
    DoubleTapToSeekOvals(doubleTapSeekAmount, seekText, interactionSource)
    CompositionLocalProvider(
        LocalRippleConfiguration provides playerRippleConfiguration,
        LocalPlayerButtonsClickEvent provides { resetControls = !resetControls },
        LocalContentColor provides Color.White,
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            ConstraintLayout(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            Pair(0f, Color.Black),
                            Pair(.2f, Color.Transparent),
                            Pair(.7f, Color.Transparent),
                            Pair(1f, Color.Black),
                        ),
                        alpha = transparentOverlay,
                    )
                    .padding(horizontal = MaterialTheme.padding.medium),
            ) {
                val (
                    topLeftControls, topRightControls, castButton,
                    volumeSlider, brightnessSlider,
                    unlockControlsButton,
                    bottomRightControls, bottomLeftControls,
                    centerControls, seekbar, playerUpdates,
                ) = createRefs()

                val hasPreviousEpisode by viewModel.hasPreviousEpisode.collectAsState()
                val hasNextEpisode by viewModel.hasNextEpisode.collectAsState()
                val isBrightnessSliderShown by viewModel.isBrightnessSliderShown.collectAsState()
                val isVolumeSliderShown by viewModel.isVolumeSliderShown.collectAsState()
                val brightness by viewModel.currentBrightness.collectAsState()
                val volume by viewModel.currentVolume.collectAsState()
                val mpvVolume by viewModel.mpv.propFlow<Int>("volume").collectAsState()
                val swapVolumeAndBrightness by gesturePreferences.swapVolumeBrightness().collectAsState()
                val reduceMotion by playerPreferences.reduceMotion().collectAsState()

                LaunchedEffect(volume, mpvVolume, isVolumeSliderShown) {
                    delay(2000)
                    if (isVolumeSliderShown) viewModel.isVolumeSliderShown.update { false }
                }
                LaunchedEffect(brightness, isBrightnessSliderShown) {
                    delay(2000)
                    if (isBrightnessSliderShown) viewModel.isBrightnessSliderShown.update { false }
                }
                AnimatedVisibility(
                    isBrightnessSliderShown,
                    enter =
                    if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) {
                            if (swapVolumeAndBrightness) -it else it
                        } +
                            fadeIn(
                                playerControlsEnterAnimationSpec(),
                            )
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit =
                    if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) {
                            if (swapVolumeAndBrightness) -it else it
                        } +
                            fadeOut(
                                playerControlsExitAnimationSpec(),
                            )
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(brightnessSlider) {
                        if (swapVolumeAndBrightness) {
                            start.linkTo(parent.start, spacing.medium)
                        } else {
                            end.linkTo(parent.end, spacing.medium)
                        }
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    BrightnessSlider(
                        brightness = brightness,
                        positiveRange = 0f..1f,
                        negativeRange = 0f..0.75f,
                    )
                }

                AnimatedVisibility(
                    isVolumeSliderShown,
                    enter =
                    if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) {
                            if (swapVolumeAndBrightness) it else -it
                        } +
                            fadeIn(
                                playerControlsEnterAnimationSpec(),
                            )
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit =
                    if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) {
                            if (swapVolumeAndBrightness) it else -it
                        } +
                            fadeOut(
                                playerControlsExitAnimationSpec(),
                            )
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(volumeSlider) {
                        if (swapVolumeAndBrightness) {
                            end.linkTo(parent.end, spacing.medium)
                        } else {
                            start.linkTo(parent.start, spacing.medium)
                        }
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    val boostCap by audioPreferences.volumeBoostCap().collectAsState()
                    val displayVolumeAsPercentage by playerPreferences.displayVolPer().collectAsState()
                    VolumeSlider(
                        volume = volume,
                        mpvVolume = mpvVolume ?: 100,
                        range = 0..viewModel.maxVolume,
                        boostRange = if (boostCap > 0) 0..audioPreferences.volumeBoostCap().get() else null,
                        displayAsPercentage = displayVolumeAsPercentage,
                    )
                }

                val currentPlayerUpdate by viewModel.playerUpdate.collectAsState()
                val aspectRatio by playerPreferences.aspectState().collectAsState()
                LaunchedEffect(currentPlayerUpdate, aspectRatio) {
                    if (currentPlayerUpdate is PlayerUpdates.DoubleSpeed || currentPlayerUpdate is PlayerUpdates.None) {
                        return@LaunchedEffect
                    }
                    delay(2000)
                    viewModel.playerUpdate.update { PlayerUpdates.None }
                }
                AnimatedVisibility(
                    currentPlayerUpdate !is PlayerUpdates.None,
                    enter = fadeIn(playerControlsEnterAnimationSpec()),
                    exit = fadeOut(playerControlsExitAnimationSpec()),
                    modifier = Modifier.constrainAs(playerUpdates) {
                        linkTo(parent.start, parent.end)
                        linkTo(parent.top, parent.bottom, bias = 0.2f)
                    },
                ) {
                    when (currentPlayerUpdate) {
                        // is PlayerUpdates.DoubleSpeed -> DoubleSpeedPlayerUpdate()
                        is PlayerUpdates.AspectRatio -> TextPlayerUpdate(stringResource(aspectRatio.titleRes))
                        is PlayerUpdates.ShowText -> TextPlayerUpdate(
                            (currentPlayerUpdate as PlayerUpdates.ShowText).value,
                        )
                        is PlayerUpdates.ShowTextResource -> TextPlayerUpdate(
                            stringResource((currentPlayerUpdate as PlayerUpdates.ShowTextResource).textResource),
                        )
                        else -> {}
                    }
                }

                AnimatedVisibility(
                    controlsShown && areControlsLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.constrainAs(unlockControlsButton) {
                        top.linkTo(parent.top, spacing.medium)
                        start.linkTo(parent.start, spacing.medium)
                    },
                ) {
                    ControlsButton(
                        Icons.Filled.Lock,
                        onClick = { viewModel.unlockControls() },
                    )
                }
                // ANK -->
                val isLoading = pausedForCache == true || (coreIdle == true && paused == false)
                // ANK <--
                AnimatedVisibility(
                    visible =
                    // AY -->
                    ((controlsShown && !areControlsLocked) || gestureSeekAmount != null) ||
                        // <-- AY
                        // ANK -->
                        isLoading ||
                        // ANK <--
                        isLoadingEpisode,
                    enter = fadeIn(playerControlsEnterAnimationSpec()),
                    exit = fadeOut(playerControlsExitAnimationSpec()),
                    modifier = Modifier.constrainAs(centerControls) {
                        end.linkTo(parent.absoluteRight)
                        start.linkTo(parent.absoluteLeft)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    val showLoadingCircle by playerPreferences.showLoadingCircle().collectAsState()
                    MiddlePlayerControls(
                        hasPrevious = hasPreviousEpisode,
                        onSkipPrevious = { viewModel.changeEpisode(true) },
                        hasNext = hasNextEpisode,
                        onSkipNext = { viewModel.changeEpisode(false) },
                        isStopped = isStopped,
                        // ANK -->
                        isLoading = isLoading,
                        // ANK <--
                        isLoadingEpisode = isLoadingEpisode,
                        controlsShown = controlsShown,
                        areControlsLocked = areControlsLocked,
                        showLoadingCircle = showLoadingCircle,
                        paused = paused == true,
                        gestureSeekAmount = gestureSeekAmount,
                        onPlayPauseClick = viewModel::pauseUnpause,
                        enter = fadeIn(playerControlsEnterAnimationSpec()),
                        exit = fadeOut(playerControlsExitAnimationSpec()),
                    )
                }
                AnimatedVisibility(
                    visible = (controlsShown || seekBarShown) && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(seekbar) {
                        bottom.linkTo(parent.bottom, spacing.medium)
                    },
                ) {
                    val invertDuration by playerPreferences.invertDuration().collectAsState()
                    val readAhead by viewModel.mpv.propFlow<Float>("demuxer-cache-time").collectAsState()
                    val remaining by viewModel.mpv.propFlow<Float>("playtime-remaining").collectAsState()
                    val preciseSeeking by gesturePreferences.playerSmoothSeek().collectAsState()
                    SeekbarWithTimers(
                        position = position?.toFloat() ?: 0f,
                        duration = duration?.toFloat() ?: 0f,
                        remaining = remaining ?: 0f,
                        readAheadValue = readAhead ?: 0f,
                        onValueChange = {
                            isSeeking = true
                            viewModel.seekTo(it.roundToInt(), preciseSeeking)
                        },
                        onValueChangeFinished = { isSeeking = false },
                        timersInverted = Pair(false, invertDuration),
                        durationTimerOnCLick = { playerPreferences.invertDuration().set(!invertDuration) },
                        positionTimerOnClick = {},
                        chapters = chapters,
                    )
                }
                val mediaTitle by viewModel.mediaTitle.collectAsState()
                val animeTitle by viewModel.animeTitle.collectAsState()
                AnimatedVisibility(
                    controlsShown && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(topLeftControls) {
                        top.linkTo(parent.top, spacing.medium)
                        start.linkTo(parent.start)
                        width = Dimension.fillToConstraints
                        end.linkTo(topRightControls.start)
                    },
                ) {
                    TopLeftPlayerControls(
                        animeTitle = animeTitle,
                        mediaTitle = mediaTitle,
                        onTitleClick = { viewModel.showEpisodeListDialog() },
                        onBackClick = onBackPress,
                    )
                }
                // Top right controls
                val autoPlayEnabled by playerPreferences.autoplayEnabled().collectAsState()
                val isEpisodeOnline by viewModel.isEpisodeOnline.collectAsState()
                AnimatedVisibility(
                    controlsShown && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(topRightControls) {
                        top.linkTo(parent.top, spacing.medium)
                        end.linkTo(parent.end)
                    },
                ) {
                    TopRightPlayerControls(
                        autoPlayEnabled = autoPlayEnabled,
                        onToggleAutoPlay = { viewModel.setAutoPlay(it) },
                        onSubtitlesClick = { viewModel.showSheet(Sheets.SubtitleTracks) },
                        onSubtitlesLongClick = { viewModel.showPanel(Panels.SubtitleSettings) },
                        onAudioClick = { viewModel.showSheet(Sheets.AudioTracks) },
                        onAudioLongClick = { viewModel.showPanel(Panels.AudioDelay) },
                        onQualityClick = { viewModel.showSheet(Sheets.QualityTracks) },
                        isEpisodeOnline = isEpisodeOnline,
                        onMoreClick = { viewModel.showSheet(Sheets.More) },
                        onMoreLongClick = { viewModel.showPanel(Panels.VideoFilters) },
                        castState = castState,
                        onCastClick = { showCastSheet = true },
                        isCastEnabled = { playerPreferences.enableCast().get() },
                    )
                }
                // Bottom right controls
                val skipIntroButton by viewModel.skipIntroText.collectAsState()
                val customButtonTitle by viewModel.primaryButtonTitle.collectAsState()
                AnimatedVisibility(
                    controlsShown && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(bottomRightControls) {
                        bottom.linkTo(seekbar.top)
                        end.linkTo(seekbar.end)
                    },
                ) {
                    val activity = LocalActivity.current as PlayerActivity
                    BottomRightPlayerControls(
                        customButton = customButton,
                        customButtonTitle = customButtonTitle,
                        skipIntroButton = skipIntroButton,
                        onPressSkipIntroButton = viewModel::onSkipIntro,
                        isPipAvailable = activity.isPipSupportedAndEnabled,
                        onPipClick = {
                            if (!viewModel.isLoadingEpisode.value) {
                                activity.enterPictureInPictureMode(activity.createPipParams())
                            }
                        },
                        onCustomButtonClick = {
                            customButton?.execute(viewModel.mpv)
                        },
                        onCustomButtonLongClick = {
                            customButton?.executeLongPress(viewModel.mpv)
                        },
                        onAspectClick = {
                            viewModel.changeVideoAspect(
                                when (aspectRatio) {
                                    VideoAspect.Fit -> VideoAspect.Stretch
                                    VideoAspect.Stretch -> VideoAspect.Crop
                                    VideoAspect.Crop -> VideoAspect.Fit
                                },
                            )
                        },
                    )
                }
                // Bottom left controls
                AnimatedVisibility(
                    controlsShown && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(bottomLeftControls) {
                        bottom.linkTo(seekbar.top)
                        start.linkTo(seekbar.start)
                        width = Dimension.fillToConstraints
                        end.linkTo(bottomRightControls.start)
                    },
                ) {
                    val showChapterIndicator by playerPreferences.showCurrentChapter().collectAsState()
                    BottomLeftPlayerControls(
                        playbackSpeed = playbackSpeed ?: playerPreferences.playerSpeed().get(),
                        showChapterIndicator = showChapterIndicator,
                        currentChapter = chapters.getOrNull(currentChapter ?: 0),
                        onLockControls = viewModel::lockControls,
                        onCycleRotation = viewModel::cycleScreenRotations,
                        onPlaybackSpeedChange = {
                            viewModel.mpv.setPropertyFloat("speed", it)
                            playerPreferences.playerSpeed().set(it)
                        },
                        onOpenSheet = viewModel::showSheet,
                    )
                }
            }
        }

        val sheetShown by viewModel.sheetShown.collectAsState()
        val dismissSheet by viewModel.dismissSheet.collectAsState()
        val isLoadingHosters by viewModel.isLoadingHosters.collectAsState()
        val hosterState by viewModel.hosterState.collectAsState()
        val expandedState by viewModel.hosterExpandedList.collectAsState()
        val selectedHosterVideoIndex by viewModel.selectedHosterVideoIndex.collectAsState()
        val sleepTimerTimeRemaining by viewModel.remainingTime.collectAsState()
        val speedPresets by playerPreferences.speedPresets().collectAsState()

        val showSubtitles by subtitlePreferences.screenshotSubtitles().collectAsState()
        val currentSource by viewModel.currentSource.collectAsState()
        val showFailedHosters by playerPreferences.showFailedHosters().collectAsState()
        val emptyHosters by playerPreferences.showEmptyHosters().collectAsState()

        val internalSubtitles by viewModel.subtitleTracks.collectAsState(persistentListOf())
        val externalSubtitles by viewModel.externalSubtitleTracks.collectAsState()
        val subtitles = remember(internalSubtitles, externalSubtitles) {
            internalSubtitles.map { VideoTrack.Internal(it) } + externalSubtitles
        }

        val audioChannels by audioPreferences.audioChannels().collectAsState()
        val pitchCorrection by audioPreferences.enablePitchCorrection().collectAsState()
        val mpvAudioPitchCorrection by viewModel.mpv.propFlow<Boolean>("audio-pitch-correction").collectAsState()
        val internalAudioTracks by viewModel.audioTracks.collectAsState(persistentListOf())
        val externalAudioTracks by viewModel.externalAudioTracks.collectAsState()
        val audioTracks = remember(internalAudioTracks, externalAudioTracks) {
            internalAudioTracks.map { VideoTrack.Internal(it) } + externalAudioTracks
        }

        val statisticsPage by advancedPreferences.playerStatisticsPage().collectAsState()

        PlayerSheets(
            sheetShown = sheetShown,
            subtitles = subtitles.toImmutableList(),
            onAddSubtitle = viewModel::addSubtitle,
            onSelectSubtitle = viewModel::selectSub,
            audioTracks = audioTracks.toImmutableList(),
            onAddAudio = viewModel::addAudio,
            onSelectAudio = viewModel::selectAudio,

            isLoadingHosters = isLoadingHosters,

            hosterState = hosterState.toPersistentList(),
            expandedState = expandedState.toPersistentList(),
            selectedVideoIndex = selectedHosterVideoIndex,
            onClickHoster = viewModel::onHosterClicked,
            onClickVideo = viewModel::onVideoClicked,
            displayHosters = Pair(showFailedHosters, emptyHosters),

            chapter = chapters.getOrNull(currentChapter ?: 0),
            chapters = chapters,
            onSeekToChapter = {
                viewModel.mpv.setPropertyInt("chapter", it)
                viewModel.dismissSheet()
                viewModel.unpause()
            },
            decoder = decoder,
            onUpdateDecoder = { viewModel.mpv.setPropertyString("hwdec", it.value) },

            speed = playbackSpeed ?: playerPreferences.playerSpeed().get(),
            speedPresets = speedPresets.map { it.toFloat() }.sorted().toPersistentList(),
            onSpeedChange = { viewModel.mpv.setPropertyFloat("speed", it.toFixed(2)) },
            onMakeDefaultSpeed = { playerPreferences.playerSpeed().set(it.toFixed(2)) },
            onAddSpeedPreset = { playerPreferences.speedPresets() += it.toFixed(2).toString() },
            onRemoveSpeedPreset = { playerPreferences.speedPresets() -= it.toFixed(2).toString() },
            onResetSpeedPresets = playerPreferences.speedPresets()::delete,
            onResetDefaultSpeed = {
                viewModel.mpv.setPropertyFloat("speed", playerPreferences.playerSpeed().deleteAndGet().toFixed(2))
            },

            // More sheet state
            statisticsPage = statisticsPage,
            audioChannels = audioChannels,
            sleepTimerTimeRemaining = sleepTimerTimeRemaining,
            onStartSleepTimer = viewModel::startTimer,
            onStatisticsPageChange = { page ->
                if ((page == 0) xor
                    (statisticsPage == 0)
                ) {
                    viewModel.mpv.command("script-binding", "stats/display-stats-toggle")
                }
                if (page != 0) viewModel.mpv.command("script-binding", "stats/display-page-$page")
                advancedPreferences.playerStatisticsPage().set(page)
            },
            onAudioChannelsChange = {
                audioPreferences.audioChannels().set(it)
                if (it == AudioChannels.ReverseStereo) {
                    viewModel.mpv.setPropertyString(AudioChannels.AutoSafe.property, AudioChannels.AutoSafe.value)
                } else {
                    viewModel.mpv.setPropertyString(AudioChannels.ReverseStereo.property, "")
                }
                viewModel.mpv.setPropertyString(it.property, it.value)
            },
            onCustomButtonClick = { it.execute(viewModel.mpv) },
            onCustomButtonLongClick = { it.executeLongPress(viewModel.mpv) },
            buttons = customButtons,
            onPitchCorrectionChange = {
                audioPreferences.enablePitchCorrection().set(it)
                viewModel.mpv.setPropertyBoolean("audio-pitch-correction", it)
            },
            pitchCorrection = pitchCorrection || mpvAudioPitchCorrection == true,

            isLocalSource = currentSource?.isLocal() == true,
            showSubtitles = showSubtitles,
            onToggleShowSubtitles = { subtitlePreferences.screenshotSubtitles().set(it) },
            cachePath = viewModel.cachePath,
            onSetAsArt = viewModel::setAsCover,
            onShare = { viewModel.shareImage(it, viewModel.pos) },
            onSave = { viewModel.saveImage(it, viewModel.pos) },
            takeScreenshot = viewModel::takeScreenshot,
            onDismissScreenshot = {
                viewModel.showSheet(Sheets.None)
                viewModel.unpause()
            },
            onOpenPanel = viewModel::showPanel,
            onDismissRequest = { viewModel.showSheet(Sheets.None) },
            dismissSheet = dismissSheet,
        )

        val panel by viewModel.panelShown.collectAsState()
        val subDelayPref by subtitlePreferences.subtitlesDelay().collectAsState()
        val subDelay by viewModel.mpv.propFlow<Double>("sub-delay").collectAsState()
        val subDelaySecondary by viewModel.mpv.propFlow<Double>("secondary-sub-delay").collectAsState()
        val subDelaySecondaryPref by subtitlePreferences.subtitlesSecondaryDelay().collectAsState()
        val subSpeed by viewModel.mpv.propFlow<Double>("sub-speed").collectAsState()
        val audioDelay by viewModel.mpv.propFlow<Double>("audio-delay").collectAsState()
        val isBold by viewModel.mpv.propFlow<Boolean>("sub-bold").collectAsState()
        val isItalic by viewModel.mpv.propFlow<Boolean>("sub-italic").collectAsState()
        val subJustify by viewModel.mpv.propFlow<String>("sub-justify").collectAsState()
        val subFont by viewModel.mpv.propFlow<String>("sub-font").collectAsState()
        val subFontSize by viewModel.mpv.propFlow<Int>("sub-font-size").collectAsState()
        val subBorderStyle by viewModel.mpv.propFlow<String>("sub-border-style").collectAsState()
        val subBorderSize by viewModel.mpv.propFlow<Int>("sub-outline-size").collectAsState()
        val subShadowOffset by viewModel.mpv.propFlow<Int>("sub-shadow-offset").collectAsState()
        val subColor by viewModel.mpv.propFlow<String>("sub-color").collectAsState()
        val subBorderColor by viewModel.mpv.propFlow<String>("sub-outline-color").collectAsState()
        val subBackgroundColor by viewModel.mpv.propFlow<String>("sub-back-color").collectAsState()
        val overrideAssSubs by viewModel.mpv.propFlow<String>("sub-ass-override").collectAsState()
        val subScale by viewModel.mpv.propFlow<Float>("sub-scale").collectAsState()
        val subPos by viewModel.mpv.propFlow<Int>("sub-pos").collectAsState()
        val deband by decoderPreferences.debanding().collectAsState()
        val mpvGpuNext by viewModel.mpv.propFlow<String>("vo").collectAsState()
        val debandSettingsMap = DebandSettings.entries.associateWith { setting ->
            viewModel.mpv.propFlow<Int>(setting.mpvProperty).collectAsState().value ?: 0
        }
        val filterValuesMap = VideoFilters.entries.associateWith { filter ->
            viewModel.mpv.propFlow<Int>(filter.mpvProperty).collectAsState().value ?: 0
        }
        var subtitleColorType by remember { mutableStateOf(SubColorType.Text) }

        PlayerPanels(
            panelShown = panel,
            onDismissRequest = { viewModel.showPanel(Panels.None) },
            // Subtitle settings panel state
            isBold = isBold ?: subtitlePreferences.boldSubtitles().get(),
            isItalic = isItalic ?: subtitlePreferences.italicSubtitles().get(),
            subJustify =
            subJustify?.let { SubtitleJustification.byValue(it) }
                ?: subtitlePreferences.subtitleJustification().get(),
            subFont = subFont ?: subtitlePreferences.subtitleFont().get(),
            subFontSize = subFontSize ?: subtitlePreferences.subtitleFontSize().get(),
            subBorderStyle = subBorderStyle?.let { SubtitlesBorderStyle.byValue(it) }
                ?: subtitlePreferences.borderStyleSubtitles().get(),
            subBorderSize = subBorderSize ?: subtitlePreferences.subtitleBorderSize().get(),
            subShadowOffset = subShadowOffset ?: subtitlePreferences.shadowOffsetSubtitles().get(),
            subColor = subtitleColorType,
            currentSubtitleColor = when (subtitleColorType) {
                SubColorType.Text -> subColor?.toColorInt() ?: subtitlePreferences.textColorSubtitles().get()
                SubColorType.Border -> subBorderColor?.toColorInt() ?: subtitlePreferences.borderColorSubtitles().get()
                SubColorType.Background -> subBackgroundColor?.toColorInt()
                    ?: subtitlePreferences.backgroundColorSubtitles().get()
            },
            overrideAssSubs = overrideAssSubs?.let { SubtitleAssOverride.byValue(it) }
                ?: subtitlePreferences.overrideSubsASS().get(),
            subScale = subScale ?: subtitlePreferences.subtitleFontScale().get(),
            subPos = subPos ?: subtitlePreferences.subtitlePos().get(),
            onSubBoldChange = {
                viewModel.mpv.setPropertyBoolean("sub-bold", it)
                subtitlePreferences.boldSubtitles().set(it)
            },
            onSubItalicChange = {
                viewModel.mpv.setPropertyBoolean("sub-italic", it)
                subtitlePreferences.italicSubtitles().set(it)
            },
            onSubJustifyChange = {
                // ANK -->
                viewModel.mpv.setPropertyBoolean("sub-ass-justify", it != SubtitleJustification.Auto)
                // ANK <--
                viewModel.mpv.setPropertyString("sub-justify", it.value)
                subtitlePreferences.subtitleJustification().set(it)
            },
            onSubFontChange = {
                viewModel.mpv.setPropertyString("sub-font", it)
                subtitlePreferences.subtitleFont().set(it)
            },
            onSubFontSizeChange = {
                viewModel.mpv.setPropertyInt("sub-font-size", it)
                subtitlePreferences.subtitleFontSize().set(it)
            },
            onSubBorderStyleChange = {
                viewModel.mpv.setPropertyString("sub-border-style", it.value)
                subtitlePreferences.borderStyleSubtitles().set(it)
            },
            onSubBorderSizeChange = {
                viewModel.mpv.setPropertyInt("sub-outline-size", it)
                subtitlePreferences.subtitleBorderSize().set(it)
            },
            onSubShadowOffsetChange = {
                viewModel.mpv.setPropertyInt("sub-shadow-offset", it)
                subtitlePreferences.shadowOffsetSubtitles().set(it)
            },
            onSubColorChange = {
                when (subtitleColorType) {
                    SubColorType.Text -> {
                        viewModel.mpv.setPropertyString("sub-color", it.toColorHexString())
                        subtitlePreferences.textColorSubtitles().set(it)
                    }

                    SubColorType.Border -> {
                        viewModel.mpv.setPropertyString("sub-outline-color", it.toColorHexString())
                        subtitlePreferences.borderColorSubtitles().set(it)
                    }

                    SubColorType.Background -> {
                        viewModel.mpv.setPropertyString("sub-back-color", it.toColorHexString())
                        subtitlePreferences.backgroundColorSubtitles().set(it)
                    }
                }
            },
            onOverrideAssSubsChange = {
                viewModel.mpv.setPropertyString("sub-ass-override", it.value)
                subtitlePreferences.overrideSubsASS().set(it)
            },
            onSubScaleChange = {
                viewModel.mpv.setPropertyFloat("sub-scale", it)
                subtitlePreferences.subtitleFontScale().set(it)
            },
            onSubPosChange = {
                viewModel.mpv.setPropertyInt("sub-pos", it)
                subtitlePreferences.subtitlePos().set(it)
            },
            onSubColorTypeChange = { subtitleColorType = it },
            onSubColorReset = {
                resetColors(subtitlePreferences, viewModel.mpv, subtitleColorType)
            },
            onSubtitleSettingsReset = {
                resetTypography(viewModel.mpv, subtitlePreferences)
            },
            onSubtitleMiscReset = {
                subtitlePreferences.subtitlePos().deleteAndGet().let {
                    viewModel.mpv.setPropertyInt("sub-pos", it)
                }
                subtitlePreferences.subtitleFontScale().deleteAndGet().let {
                    viewModel.mpv.setPropertyFloat("sub-scale", it)
                }
                subtitlePreferences.overrideSubsASS().deleteAndGet().let {
                    viewModel.mpv.setPropertyString("sub-ass-override", it.value)
                }
            },
            subDelayMsPrimary = subDelay?.times(1000)?.roundToInt() ?: subDelayPref,
            subDelayMsSecondary = subDelaySecondary?.times(1000)?.roundToInt() ?: subDelaySecondaryPref,
            subSpeed = subSpeed ?: subtitlePreferences.subtitlesSpeed().get().toDouble(),
            onSubDelayPrimaryChange = {
                viewModel.mpv.setPropertyDouble("sub-delay", it / 1000.0)
            },
            onSubDelaySecondaryChange = {
                viewModel.mpv.setPropertyDouble("secondary-sub-delay", it / 1000.0)
            },
            onSubSpeedChange = {
                viewModel.mpv.setPropertyDouble("sub-speed", it)
            },
            onSubDelayApply = {
                subtitlePreferences.subtitlesDelay().set((subDelay?.times(1000)?.roundToInt()) ?: 0)
                subtitlePreferences.subtitlesSecondaryDelay().set((subDelaySecondary?.times(1000)?.roundToInt()) ?: 0)
            },
            onSubDelayReset = {
                viewModel.mpv.setPropertyDouble("sub-delay", subtitlePreferences.subtitlesDelay().get() / 1000.0)
                viewModel.mpv.setPropertyDouble(
                    "secondary-sub-delay",
                    subtitlePreferences.subtitlesSecondaryDelay().get() / 1000.0,
                )
                viewModel.mpv.setPropertyDouble("sub-speed", subtitlePreferences.subtitlesSpeed().get().toDouble())
            },
            audioDelayMs = (audioDelay?.times(1000))?.roundToInt() ?: audioPreferences.audioDelay().get(),
            onAudioDelayChange = { viewModel.mpv.setPropertyDouble("audio-delay", it / 1000.0) },
            onAudioDelayApply = {
                audioPreferences.audioDelay().set((audioDelay?.times(1000)?.roundToInt()) ?: 0)
            },
            onAudioDelayReset = {
                viewModel.mpv.setPropertyDouble("audio-delay", audioPreferences.audioDelay().get() / 1000.0)
            },
            onDebandChange = {
                decoderPreferences.debanding().set(it)
                when (it) {
                    Debanding.None -> {
                        viewModel.mpv.setPropertyString("deband", "no")
                        viewModel.mpv.command("vf", "remove", "@deband")
                    }

                    Debanding.CPU -> {
                        viewModel.mpv.setPropertyString("deband", "no")
                        viewModel.mpv.command("vf", "add", "@deband:gradfun=radius=12")
                    }

                    Debanding.GPU -> {
                        viewModel.mpv.setPropertyString("deband", "yes")
                        viewModel.mpv.command("vf", "remove", "@deband")
                    }
                }
            },
            onDebandReset = {
                // ANK -->
                decoderPreferences.debanding().delete()
                // ANK <--
                viewModel.mpv.setPropertyString("deband", "no")
                viewModel.mpv.command("vf", "remove", "@deband")
                DebandSettings.entries.forEach {
                    viewModel.mpv.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
                }
            },
            onDebandSettingsChange = { setting, value ->
                setting.preference(decoderPreferences).set(value)
                viewModel.mpv.setPropertyInt(setting.mpvProperty, value)
            },
            onVideoFilterChange = { filter, value ->
                filter.preference(decoderPreferences).set(value)
                viewModel.mpv.setPropertyInt(filter.mpvProperty, value)
            },
            onFilterReset = {
                VideoFilters.entries.forEach {
                    viewModel.mpv.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
                }
            },
            deband = deband,
            isGpuNextEnabled = mpvGpuNext == "gpu-next",
            filterValue = { filterValuesMap[it] ?: 0 },
            debandSettings = { debandSettingsMap[it] ?: 0 },
            modifier = Modifier,
        )

        val activity = LocalActivity.current as PlayerActivity
        val dialog by viewModel.dialogShown.collectAsState()
        val anime by viewModel.currentAnime.collectAsState()
        val playlist by viewModel.currentPlaylist.collectAsState()

        PlayerDialogs(
            dialogShown = dialog,
            episodeDisplayMode = anime?.displayMode,
            episodeList = playlist,
            currentEpisodeIndex = viewModel.getCurrentEpisodeIndex(),
            dateRelativeTime = viewModel.relativeTime,
            dateFormat = viewModel.dateFormat,
            onBookmarkClicked = viewModel::bookmarkEpisode,
            onFillermarkClicked = viewModel::fillermarkEpisode,
            onEpisodeClicked = {
                viewModel.showDialog(Dialogs.None)
                activity.changeEpisode(it)
            },
            onDismissRequest = { viewModel.showDialog(Dialogs.None) },
        )

        BrightnessOverlay(
            brightness = currentBrightness,
        )
    }

    if (showCastSheet) {
        CastSheet(
            castManager = castManager,
            viewModel = viewModel,
            onDismissRequest = { showCastSheet = false },
        )
    }
}

fun <T> playerControlsExitAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)

fun <T> playerControlsEnterAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 100,
    easing = LinearOutSlowInEasing,
)
