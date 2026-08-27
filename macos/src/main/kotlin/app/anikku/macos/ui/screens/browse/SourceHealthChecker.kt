package app.anikku.macos.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private val logger = KotlinLogging.logger {}

/**
 * Cached source health status.
 * Health checks are performed once and cached per source ID for the session
 * (the process lifetime). To force a refresh, clear the cache by calling
 * [clearCache] or restart the app.
 *
 * Uses [StateFlow] internally so composables can observe health changes reactively
 * via [observeHealth].
 */
object SourceHealthChecker {

    /** Health status of a source. */
    enum class Health {
        /** Source returned browse results within timeout — fully working. */
        WORKING,
        /** Source has known issues (DNS, Cloudflare, API changed, etc.). */
        FAILING,
        /** Health check not yet performed. */
        UNKNOWN,
    }

    /**
     * Optional extended diagnostic info when a source is failing.
     * Mirrors the categories from [app.anikku.macos.ui.screens.player.PlayerScreen.ErrorDiagnostic.ErrorCategory].
     */
    data class HealthResult(
        val status: Health,
        val category: String? = null,   // e.g. "DNS", "SSL", "403", "Timeout"
        val detail: String? = null,      // e.g. "UnknownHostException: hexawatch.tv"
        val cachedAt: Long = System.currentTimeMillis(),
    )

    // Reactive state — the full map of all source health results
    private val _healthFlow = MutableStateFlow<Map<Long, HealthResult>>(emptyMap())

    /** Public read-only flow of the complete health map. */
    val healthFlow: StateFlow<Map<Long, HealthResult>> = _healthFlow.asStateFlow()

    /** Bumped by [requestRecheckAll] so badges re-run their check on demand. */
    private val _recheckVersion = MutableStateFlow(0)

    /** Public read-only version of the recheck counter. */
    val recheckVersion: StateFlow<Int> = _recheckVersion.asStateFlow()

    /**
     * Gate for network health checks. Disabled until the Browse tab is first
     * shown — otherwise the 60+ installed sources each fire a check (and a
     * Cloudflare bypass attempt for dead ones) at startup, which stalls input
     * for the first minute. Set via [setChecksEnabled] when Browse activates.
     */
    private val _checksEnabled = MutableStateFlow(false)

    /** Public read-only gate state. */
    val checksEnabled: StateFlow<Boolean> = _checksEnabled.asStateFlow()

    /** Enable/disable health-check execution (Browse tab activity). */
    fun setChecksEnabled(enabled: Boolean) {
        _checksEnabled.value = enabled
    }

    // Per-source reactive flows so composables can observe individual sources
    private val sourceFlows = mutableMapOf<Long, MutableStateFlow<HealthResult>>()

    private const val TIMEOUT_MS = 8_000L

    /**
     * Get the cached health result for a source, or UNKNOWN if not yet checked.
     * This returns the current snapshot — for reactive observation, use [observeHealth].
     */
    fun getHealth(sourceId: Long): HealthResult {
        return _healthFlow.value[sourceId] ?: HealthResult(Health.UNKNOWN)
    }

    /**
     * Observe health changes for a specific source reactively.
     * Returns a [StateFlow] that emits whenever the health result for this source changes.
     * Composables can use `.collectAsState()` to trigger recomposition automatically.
     */
    fun observeHealth(sourceId: Long): StateFlow<HealthResult> {
        return sourceFlows.getOrPut(sourceId) {
            MutableStateFlow(getHealth(sourceId))
        }.asStateFlow()
    }

    /**
     * Perform a health check for the given source and cache the result.
     * This is a suspend function — call it from a coroutine.
     *
     * The check is lightweight: calls [CatalogueSource.getPopularAnime] with a
     * timeout. If that succeeds, the source is marked as WORKING. If the call
     * is not a [CatalogueSource], the source is marked as UNKNOWN.
     *
     * All consumers observing this source via [observeHealth] will be notified
     * automatically when the check completes.
     */
    suspend fun checkHealth(source: Source): HealthResult {
        val sourceId = source.id

        // Return cached result if already checked
        val cached = _healthFlow.value[sourceId]
        if (cached != null && cached.status != Health.UNKNOWN) return cached

        if (source !is CatalogueSource) {
            val result = HealthResult(Health.UNKNOWN)
            setResult(sourceId, result)
            return result
        }

        return try {
            val startTime = System.currentTimeMillis()
            val page = withContext(Dispatchers.IO) {
                withTimeout(TIMEOUT_MS) {
                    if (source.supportsLatest) {
                        source.getLatestUpdates(page = 1)
                    } else {
                        source.getPopularAnime(page = 1)
                    }
                }
            }
            val elapsed = System.currentTimeMillis() - startTime
            val count = page.animes.filter { a ->
                try { a.title.isNotBlank() } catch (_: Exception) { false }
            }.size

            val result = if (count > 0) {
                HealthResult(Health.WORKING, cachedAt = System.currentTimeMillis())
            } else {
                HealthResult(Health.FAILING, "Empty", "Returned 0 results in ${elapsed}ms")
            }
            setResult(sourceId, result)
            result
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val result = HealthResult(Health.FAILING, "Timeout", "Request timed out after ${TIMEOUT_MS}ms")
            setResult(sourceId, result)
            result
        } catch (e: Throwable) {
            // Extension code runs in a separate classloader and can throw Errors
            // (NoClassDefFoundError, NoSuchFieldError, …) when the extension was
            // compiled against libraries newer/different than the app bundles.
            // Never let that crash the app — mark the source unhealthy instead.
            val msg = e.message ?: ""
            val category = when {
                e is java.net.UnknownHostException -> "DNS"
                e is javax.net.ssl.SSLException -> "SSL"
                msg.contains("NoClassDefFoundError") ||
                    msg.contains("NoSuchFieldError") ||
                    msg.contains("NoSuchMethodError") -> "Incompatible"
                msg.contains("403") || msg.contains("forbidden", ignoreCase = true) -> "403"
                msg.contains("404") || msg.contains("not found", ignoreCase = true) -> "404"
                msg.contains("cloudflare", ignoreCase = true) ||
                    msg.contains("cf-", ignoreCase = true) -> "Cloudflare"
                else -> "Error"
            }
            val detail = "${e.javaClass.simpleName}: ${msg.take(60)}"
            logger.debug { "Health check for source #$sourceId: $category — $detail" }
            val result = HealthResult(Health.FAILING, category, detail)
            setResult(sourceId, result)
            result
        }
    }

    /**
     * Update both the global health map and the per-source flow atomically.
     */
    private fun setResult(sourceId: Long, result: HealthResult) {
        // Update the full map
        _healthFlow.value = _healthFlow.value + (sourceId to result)
        // Update the per-source flow if it exists
        sourceFlows[sourceId]?.value = result
    }

    /**
     * Clear the health cache — forces re-check on next access.
     * All observers will be reset to UNKNOWN and composables will recompose
     * to show gray dots until health checks re-run.
     */
    fun clearCache() {
        _healthFlow.value = emptyMap()
        sourceFlows.values.forEach { it.value = HealthResult(Health.UNKNOWN) }
        sourceFlows.clear()
    }

    /**
     * Clear the cache AND bump the recheck version so every mounted
     * [SourceHealthBadge] re-runs its check immediately. Used by the
     * "Re-check health" affordance in Browse.
     */
    fun requestRecheckAll() {
        clearCache()
        _recheckVersion.value++
    }
}

/**
 * A small colored dot that indicates the health of a source.
 *
 * - 🟢 GREEN: Working (browse returns results)
 * - 🔴 RED: Failing (error or timeout)
 * - ⚫ GRAY: Unknown (not yet checked)
 *
 * The health check runs once (as a side effect) and is cached for the session.
 * The dot updates reactively — when the health check completes, the color changes
 * automatically without requiring recomposition from the parent.
 *
 * @param source The source to check.
 * @param modifier Optional modifier for positioning.
 */
@Composable
fun SourceHealthBadge(
    source: Source,
    modifier: Modifier = Modifier,
) {
    // Collect from reactive state flow — updates automatically when health check completes
    val healthResult by SourceHealthChecker.observeHealth(source.id).collectAsState()
    val recheckVersion by SourceHealthChecker.recheckVersion.collectAsState()
    val checksEnabled by SourceHealthChecker.checksEnabled.collectAsState()

    // Trigger health check once per source per session (re-runs on manual
    // recheck). Gated on the Browse tab being active — see [setChecksEnabled].
    LaunchedEffect(source.id, recheckVersion, checksEnabled) {
        if (checksEnabled) {
            SourceHealthChecker.checkHealth(source)
        }
    }

    val color = when (healthResult.status) {
        SourceHealthChecker.Health.WORKING -> Color(0xFF4ECCA3)  // green
        SourceHealthChecker.Health.FAILING -> Color(0xFFE94560)  // red
        SourceHealthChecker.Health.UNKNOWN -> Color(0xFF666666)  // gray
    }

    val size = when (healthResult.status) {
        SourceHealthChecker.Health.WORKING -> 8.dp
        SourceHealthChecker.Health.FAILING -> 8.dp
        SourceHealthChecker.Health.UNKNOWN -> 6.dp
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * A small colored dot that indicates the health of a source by its ID only.
 * This variant reads from the cache but does NOT trigger a health check — use it
 * for sources that are not yet instantiated (e.g., from [Extension.Available]).
 *
 * The dot will be gray (UNKNOWN) for sources that have never been checked,
 * or display the cached health color if this source was checked before
 * (e.g., when the extension was previously installed).
 *
 * @param sourceId The source ID to display health for.
 * @param modifier Optional modifier for positioning.
 */
@Composable
fun SourceHealthBadge(
    sourceId: Long,
    modifier: Modifier = Modifier,
) {
    // Collect from reactive state flow — shows cached value or UNKNOWN
    val healthResult by SourceHealthChecker.observeHealth(sourceId).collectAsState()

    val color = when (healthResult.status) {
        SourceHealthChecker.Health.WORKING -> Color(0xFF4ECCA3)  // green
        SourceHealthChecker.Health.FAILING -> Color(0xFFE94560)  // red
        SourceHealthChecker.Health.UNKNOWN -> Color(0xFF666666)  // gray
    }

    val size = when (healthResult.status) {
        SourceHealthChecker.Health.WORKING -> 8.dp
        SourceHealthChecker.Health.FAILING -> 8.dp
        SourceHealthChecker.Health.UNKNOWN -> 6.dp
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
