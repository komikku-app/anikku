// AY -->
package tachiyomi.source.local.image

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.SAnime
import java.io.InputStream

expect class LocalBackgroundManager {

    fun find(animeUrl: String): UniFile?

    fun update(anime: SAnime, inputStream: InputStream): UniFile?
}
// <-- AY
