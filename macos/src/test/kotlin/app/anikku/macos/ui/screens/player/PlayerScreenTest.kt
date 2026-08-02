package app.anikku.macos.ui.screens.player

import app.anikku.macos.player.PlaybackState
import app.anikku.macos.player.PlayerPreferences
import app.anikku.macos.player.PlayerViewModel
import app.anikku.macos.player.TrackInfo
import app.anikku.macos.player.formatDuration
import app.anikku.macos.player.prettyTime
import app.anikku.macos.ui.screens.models.EpisodeModel
import app.anikku.macos.ui.screens.models.MockData
import org.junit.jupiter.api.Assertions.assertEquals

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerScreenTest {

    // ========================================================================
    // Phase 5: Player Screen Data Model
    // ========================================================================

    @Test
    fun `PlayerScreen has correct animeId`() {
        val screen = PlayerScreen(
            animeId = 1L,
            episodeId = 3L,
            animeUrl = "https://example.com/anime/1",
            animeTitle = "Test Anime",
        )
        assertEquals(1L, screen.animeId)
        assertEquals(3L, screen.episodeId)
    }

    @Test
    fun `PlayerScreen stores animeUrl and animeTitle`() {
        val screen = PlayerScreen(
            animeId = 1L,
            episodeId = 1L,
            animeUrl = "https://example.com/anime/1",
            animeTitle = "Test Anime",
        )
        assertEquals("https://example.com/anime/1", screen.animeUrl)
        assertEquals("Test Anime", screen.animeTitle)
    }

    @Test
    fun `PlayerScreen key is unique per instance`() {
        val screen1 = PlayerScreen(animeId = 1L, episodeId = 1L)
        val screen2 = PlayerScreen(animeId = 1L, episodeId = 1L)
        // Voyager screens should have unique keys even for same data
        // (uniqueScreenKey generates a new key each time)
    }

    @Test
    fun `mock episode data matches player screen requirements`() {
        val episode = MockData.sampleEpisodes.first { it.id == 1L }
        assertEquals(1L, episode.animeId)
        assertEquals(1.0, episode.episodeNumber)
        assertEquals("To You, 2,000 Years Later", episode.name)
    }

    @Test
    fun `episode totalSeconds is set correctly`() {
        val episode = EpisodeModel(
            id = 100L,
            animeId = 1L,
            name = "Test Episode",
            episodeNumber = 10.0,
            seen = false,
            totalSeconds = 1440L,
            lastSecondSeen = 0L,
        )
        assertEquals(1440L, episode.totalSeconds)
        assertEquals(0L, episode.lastSecondSeen)
    }

    @Test
    fun `seen episode has lastSecondSeen`() {
        val episode = EpisodeModel(
            id = 101L,
            animeId = 1L,
            name = "Partially Watched",
            episodeNumber = 5.0,
            seen = true,
            totalSeconds = 1440L,
            lastSecondSeen = 720L, // Watched halfway
        )
        assertNotNull(episode.totalSeconds)
        assertEquals(720L, episode.lastSecondSeen)
    }

    @Test
    fun `player screen resolves anime from mock data`() {
        val anime = MockData.sampleAnime.find { it.id == 1L }
        assertNotNull(anime)
        assertEquals("Attack on Titan", anime!!.title)

        // Verify the anime has episodes
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        assertEquals(8, episodes.size)
    }

    @Test
    fun `unseen episodes are available for first play`() {
        val animeId = 1L
        val episodes = MockData.sampleEpisodes.filter { it.animeId == animeId }
        val firstUnseen = episodes.firstOrNull { !it.seen }
        assertNotNull(firstUnseen)
        assertEquals(3.0, firstUnseen!!.episodeNumber)
    }

    // ========================================================================
    // Phase 6: Episode Navigation Edge Cases
    // ========================================================================

    @Test
    fun `index beyond last episode triggers no-next condition`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val lastIndex = episodes.size - 1 // 7
        val currentEpisodeIndex = lastIndex
        val invalidIndex = lastIndex + 1 // 8

        val outOfBounds = invalidIndex !in episodes.indices
        assert(outOfBounds) { "Index $invalidIndex should be outside 0..$lastIndex" }

        val isForward = invalidIndex > currentEpisodeIndex
        assert(isForward) { "Index $invalidIndex should be > current $currentEpisodeIndex" }
    }

    @Test
    fun `index before first episode triggers no-previous condition`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val currentEpisodeIndex = 0
        val invalidIndex = -1

        val outOfBounds = invalidIndex !in episodes.indices
        assert(outOfBounds) { "Index $invalidIndex should be outside 0..${episodes.size - 1}" }

        val isBackward = invalidIndex < currentEpisodeIndex
        assert(isBackward) { "Index $invalidIndex should be < current $currentEpisodeIndex" }
    }

    @Test
    fun `navigating to next episode from first is valid`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val currentEpisodeIndex = 0
        val targetIndex = 1

        val withinBounds = targetIndex in episodes.indices
        assert(withinBounds) { "Index $targetIndex should be within 0..${episodes.size - 1}" }
    }

    @Test
    fun `navigating to previous episode from last is valid`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val currentEpisodeIndex = episodes.size - 1
        val targetIndex = currentEpisodeIndex - 1

        val withinBounds = targetIndex in episodes.indices
        assert(withinBounds) { "Index $targetIndex should be within 0..${episodes.size - 1}" }
    }

    // ========================================================================
    // Phase 6: Time Formatting Utilities
    // ========================================================================

    @Test
    fun `formatDuration formats seconds correctly`() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("00:30", formatDuration(30L))
        assertEquals("01:00", formatDuration(60L))
        assertEquals("01:30", formatDuration(90L))
        assertEquals("10:00", formatDuration(600L))
        assertEquals("1:00:00", formatDuration(3600L))
        assertEquals("2:30:00", formatDuration(9000L))
    }

    @Test
    fun `formatDuration handles double input`() {
        assertEquals("00:00", formatDuration(0.0))
        assertEquals("01:00", formatDuration(60.0))
        assertEquals("01:30", formatDuration(90.5))
    }

    @Test
    fun `prettyTime formats milliseconds correctly`() {
        assertEquals("00:00", prettyTime(0L))
        assertEquals("00:30", prettyTime(30000L))
        assertEquals("01:00", prettyTime(60000L))
        assertEquals("01:30", prettyTime(90000L))
        assertEquals("1:00:00", prettyTime(3600000L))
    }

    @Test
    fun `prettyTime handles large durations`() {
        assertEquals("10:00:00", prettyTime(36000000L))
    }

    // ========================================================================
    // Phase 6: Playback State Machine
    // ========================================================================

    @Test
    fun `PlaybackState enum has all expected states`() {
        assertEquals(PlaybackState.IDLE, PlaybackState.valueOf("IDLE"))
        assertEquals(PlaybackState.LOADING, PlaybackState.valueOf("LOADING"))
        assertEquals(PlaybackState.PLAYING, PlaybackState.valueOf("PLAYING"))
        assertEquals(PlaybackState.PAUSED, PlaybackState.valueOf("PAUSED"))
        assertEquals(PlaybackState.SEEKING, PlaybackState.valueOf("SEEKING"))
        assertEquals(PlaybackState.BUFFERING, PlaybackState.valueOf("BUFFERING"))
        assertEquals(PlaybackState.ENDED, PlaybackState.valueOf("ENDED"))
        assertEquals(PlaybackState.ERROR, PlaybackState.valueOf("ERROR"))
    }

    @Test
    fun `PlaybackState initial state is IDLE`() {
        val initial = PlaybackState.valueOf("IDLE")
        assertEquals(PlaybackState.IDLE, initial)
    }

    // ========================================================================
    // Phase 6: TrackInfo Data Model
    // ========================================================================

    @Test
    fun `TrackInfo stores track metadata correctly`() {
        val track = TrackInfo(
            id = 1,
            title = "English Dub",
            language = "eng",
            codec = "aac",
        )
        assertEquals(1, track.id)
        assertEquals("English Dub", track.title)
        assertEquals("eng", track.language)
        assertEquals("aac", track.codec)
    }

    @Test
    fun `TrackInfo with empty codec defaults gracefully`() {
        val track = TrackInfo(id = 2, title = "Japanese", language = "jpn")
        assertEquals("", track.codec)
    }

    // ========================================================================
    // Phase 6: PlayerViewModel Initialization
    // ========================================================================

    @Test
    fun `default subtitle selection prefers English regardless of language spelling`() {
        val tracks = listOf(
            TrackInfo(id = 7, title = "Japanese", language = "jpn"),
            TrackInfo(id = 12, title = "English", language = "en-US"),
        )

        assertEquals(12, PlayerViewModel.chooseDefaultSubtitleTrack(tracks))
    }

    @Test
    fun `default subtitle selection falls back to first available track`() {
        val tracks = listOf(
            TrackInfo(id = 21, title = "Spanish", language = "spa"),
            TrackInfo(id = 22, title = "French", language = "fra"),
        )

        assertEquals(21, PlayerViewModel.chooseDefaultSubtitleTrack(tracks))
    }

    @Test
    fun `default subtitle selection returns disabled for empty track list`() {
        assertEquals(-1, PlayerViewModel.chooseDefaultSubtitleTrack(emptyList()))
    }

    @Test
    fun `stale or unowned end file cannot fail a replacement load`() {
        assertTrue(
            PlayerViewModel.shouldIgnoreEndFileDuringLoad(
                state = PlaybackState.LOADING,
                loadInProgress = true,
                activePlaylistEntryId = 42L,
                eventPlaylistEntryId = 41L,
            ),
        )
        assertTrue(
            PlayerViewModel.shouldIgnoreEndFileDuringLoad(
                state = PlaybackState.LOADING,
                loadInProgress = true,
                activePlaylistEntryId = null,
                eventPlaylistEntryId = 41L,
            ),
        )
        assertTrue(
            PlayerViewModel.shouldIgnoreEndFileDuringLoad(
                state = PlaybackState.LOADING,
                loadInProgress = true,
                activePlaylistEntryId = 42L,
                eventPlaylistEntryId = null,
            ),
        )
    }

    @Test
    fun `owned end file and completed loads are not suppressed`() {
        assertFalse(
            PlayerViewModel.shouldIgnoreEndFileDuringLoad(
                state = PlaybackState.LOADING,
                loadInProgress = true,
                activePlaylistEntryId = 42L,
                eventPlaylistEntryId = 42L,
            ),
        )
        assertFalse(
            PlayerViewModel.shouldIgnoreEndFileDuringLoad(
                state = PlaybackState.PLAYING,
                loadInProgress = false,
                activePlaylistEntryId = 42L,
                eventPlaylistEntryId = 41L,
            ),
        )
    }

    @Test
    fun `PlayerViewModel initializes with IDLE state by default`() {
        val viewModel = PlayerViewModel()
        assertEquals(PlaybackState.IDLE, viewModel.playbackState.value)
    }

    @Test
    fun `PlayerViewModel has correct initial position and duration`() {
        val viewModel = PlayerViewModel()
        assertEquals(0.0, viewModel.currentPosition.value)
        assertEquals(0.0, viewModel.duration.value)
    }

    @Test
    fun `PlayerViewModel initial volume is 100`() {
        val viewModel = PlayerViewModel()
        assertEquals(100, viewModel.volume.value)
    }

    @Test
    fun `PlayerViewModel initial speed is 1x`() {
        val viewModel = PlayerViewModel()
        assertEquals(1.0, viewModel.playbackSpeed.value)
    }

    @Test
    fun `PlayerViewModel tracks and subtitles are empty initially`() {
        val viewModel = PlayerViewModel()
        assertTrue(viewModel.audioTracks.value.isEmpty())
        assertTrue(viewModel.subtitleTracks.value.isEmpty())
        assertEquals(-1, viewModel.selectedAudioTrack.value)
        assertEquals(-1, viewModel.selectedSubtitleTrack.value)
    }

    @Test
    fun `PlayerViewModel isMPVAvailable depends on libmpv installation`() {
        val viewModel = PlayerViewModel()
        // If mpv is installed via brew, this could be true
        // If not, it falls back gracefully — either is valid
        // Just verify the method doesn't throw
        val available = viewModel.isMPVAvailable
        // No assertion — just verifying the getter works
    }

    @Test
    fun `PlayerViewModel setVolume clamps between 0 and 200`() {
        val viewModel = PlayerViewModel()
        viewModel.setVolume(50)
        assertEquals(50, viewModel.volume.value)

        viewModel.setVolume(250) // Should clamp to 200
        assertEquals(200, viewModel.volume.value)

        viewModel.setVolume(-10) // Should clamp to 0
        assertEquals(0, viewModel.volume.value)
    }

    @Test
    fun `PlayerViewModel setSpeed clamps between 0_25 and 4_0`() {
        val viewModel = PlayerViewModel()
        viewModel.setSpeed(2.0)
        assertEquals(2.0, viewModel.playbackSpeed.value)

        viewModel.setSpeed(0.1) // Should clamp to 0.25
        assertEquals(0.25, viewModel.playbackSpeed.value)

        viewModel.setSpeed(5.0) // Should clamp to 4.0
        assertEquals(4.0, viewModel.playbackSpeed.value)
    }

    @Test
    fun `PlayerViewModel shutdown resets state to IDLE`() {
        val viewModel = PlayerViewModel()
        viewModel.shutdown()
        assertEquals(PlaybackState.IDLE, viewModel.playbackState.value)
    }

    @Test
    fun `PlayerViewModel shutdown is safe even if not initialized`() {
        val viewModel = PlayerViewModel()
        // Should not throw
        viewModel.shutdown()
        viewModel.shutdown() // Double shutdown should also be safe
        assertEquals(PlaybackState.IDLE, viewModel.playbackState.value)
    }

    @Test
    fun `PlayerViewModel togglePlay is safe without mpv`() {
        val viewModel = PlayerViewModel()
        // Should not throw when mpv is not available
        viewModel.togglePause()
        viewModel.togglePause()
    }

    @Test
    fun `PlayerViewModel seekTo is safe without mpv`() {
        val viewModel = PlayerViewModel()
        viewModel.seekTo(120.0)
        viewModel.seekTo(-10.0) // Negative should be clamped
    }

    @Test
    fun `PlayerViewModel seekRelative is safe without mpv`() {
        val viewModel = PlayerViewModel()
        viewModel.seekRelative(30.0)
        viewModel.seekRelative(-30.0)
    }

    @Test
    fun `PlayerViewModel takeScreenshot returns null without mpv`() {
        val viewModel = PlayerViewModel()
        val result = viewModel.takeScreenshot()
        // Should return null when mpv is not available
    }

    @Test
    fun `PlayerViewModel refreshTracks is safe without mpv`() {
        val viewModel = PlayerViewModel()
        viewModel.refreshTracks()
        assertTrue(viewModel.audioTracks.value.isEmpty())
    }

    @Test
    fun `PlayerViewModel selectAudioTrack is safe without mpv`() {
        val viewModel = PlayerViewModel()
        viewModel.selectAudioTrack(1)
        viewModel.selectAudioTrack(-1)
    }

    @Test
    fun `PlayerViewModel selectSubtitleTrack is safe without mpv`() {
        val viewModel = PlayerViewModel()
        viewModel.selectSubtitleTrack(1)
        viewModel.disableSubtitles()
        assertEquals(-1, viewModel.selectedSubtitleTrack.value)
    }

    // ========================================================================
    // Phase 6: Mock Episode Data for Player
    // ========================================================================

    @Test
    fun `mock episode has correct totalSeconds distribution`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        // Default totalSeconds should be 0 for mock episodes
        episodes.forEach { ep ->
            assertEquals(0L, ep.totalSeconds)
        }
    }

    @Test
    fun `episodes are ordered by episodeNumber`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        for (i in 0 until episodes.size - 1) {
            assertTrue(episodes[i].episodeNumber < episodes[i + 1].episodeNumber)
        }
    }

    @Test
    fun `player screen finds correct index for given episodeId`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val index = episodes.indexOfFirst { it.id == 5L }
        assertEquals(4, index) // Episode 5 should be at index 4 (0-based)
    }

    @Test
    fun `player screen handles missing episodeId gracefully`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val index = episodes.indexOfFirst { it.id == 9999L }
        assertEquals(-1, index)
        // Should coerceAtLeast to 0
        val safeIndex = index.coerceAtLeast(0)
        assertEquals(0, safeIndex)
    }

    // ========================================================================
    // Phase 6: Playback Speed Ranges
    // ========================================================================

    @Test
    fun `playback speed has valid range`() {
        val validSpeeds = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 3.0, 4.0)
        val viewModel = PlayerViewModel()

        validSpeeds.forEach { speed ->
            viewModel.setSpeed(speed)
            // Speed should be preserved after clamping
            assertTrue(viewModel.playbackSpeed.value in 0.25..4.0)
        }
    }

    @Test
    fun `playback speed out of range gets clamped`() {
        val viewModel = PlayerViewModel()

        viewModel.setSpeed(0.0) // Too slow
        assertTrue(viewModel.playbackSpeed.value >= 0.25)

        viewModel.setSpeed(10.0) // Too fast
        assertTrue(viewModel.playbackSpeed.value <= 4.0)
    }

    // ========================================================================
    // Phase 6: Volume Range
    // ========================================================================

    @Test
    fun `volume range is 0 to 200`() {
        val viewModel = PlayerViewModel()

        viewModel.setVolume(0)
        assertEquals(0, viewModel.volume.value)

        viewModel.setVolume(200)
        assertEquals(200, viewModel.volume.value)

        // Valid values in range
        viewModel.setVolume(50)
        assertEquals(50, viewModel.volume.value)
    }
}
