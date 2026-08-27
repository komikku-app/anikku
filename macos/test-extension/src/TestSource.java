package com.anikku.test;

import eu.kanade.tachiyomi.source.CatalogueSource;
import eu.kanade.tachiyomi.source.Source;
import eu.kanade.tachiyomi.animesource.model.SAnime;
import eu.kanade.tachiyomi.animesource.model.SEpisode;
import eu.kanade.tachiyomi.animesource.model.Video;
import kotlin.coroutines.Continuation;
import java.util.Collections;
import java.util.List;

/**
 * Minimal test anime source for verifying the macOS extension loading pipeline.
 *
 * This source does not actually fetch data — it returns empty/placeholder
 * results for all methods. It exists solely to validate:
 *   1. JAR packaging with META-INF/extension.json
 *   2. Class loading via URLClassLoader
 *   3. Source instantiation by MacOSExtensionLoader
 */
public class TestSource implements CatalogueSource {

    @Override
    public long getId() {
        return 9999999999999L;
    }

    @Override
    public String getName() {
        return "TestSource (macOS)";
    }

    @Override
    public String getLang() {
        return "en";
    }

    // --- Suspend function implementations ---
    // Kotlin suspend functions compile to methods with Continuation parameter returning Object

    @Override
    public Object getAnimeDetails(SAnime anime, Continuation<? super SAnime> continuation) {
        anime.setTitle("Test Anime: " + anime.getTitle());
        anime.setInitialized(true);
        return anime;
    }

    @Override
    public Object getEpisodeList(SAnime anime, Continuation<? super List<SEpisode>> continuation) {
        SEpisode episode = SEpisode.Companion.create();
        episode.setName("Episode 1");
        episode.setUrl("https://example.com/ep1");
        episode.setEpisode_number(1.0f);
        return Collections.singletonList(episode);
    }

    @Override
    public Object getVideoList(SEpisode episode, Continuation<? super List<Video>> continuation) {
        Video video = new Video(
            "https://example.com/video.mp4",
            "720p",
            null, null, null,
            false,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            "",
            true,
            ""
        );
        return Collections.singletonList(video);
    }

    @Override
    public String toString() {
        return getName() + " (ID: " + getId() + ", Lang: " + getLang() + ")";
    }
}
