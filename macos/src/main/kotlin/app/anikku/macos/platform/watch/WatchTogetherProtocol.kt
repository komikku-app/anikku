package app.anikku.macos.platform.watch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Wire protocol for Watch Together rooms (LAN, JSON over WebSocket).
 *
 * Messages are relayed verbatim to every other room member; the server only
 * inspects [Episode] (stored so late joiners receive the current media) and
 * [Hello]/[Members] (membership accounting). Control is peer-to-peer: any
 * member's play/pause/seek applies to everyone.
 */
@Serializable
sealed class WtMessage {

    /** Sent once on connect; carries the sender's display name. */
    @Serializable
    @SerialName("hello")
    data class Hello(val name: String = "") : WtMessage()

    /** Resume playback for all members. */
    @Serializable
    @SerialName("play")
    data class Play(val by: String = "") : WtMessage()

    /** Pause playback for all members. */
    @Serializable
    @SerialName("pause")
    data class Pause(val by: String = "") : WtMessage()

    /** Seek to an absolute position (seconds) for all members. */
    @Serializable
    @SerialName("seek")
    data class Seek(val pos: Double, val by: String = "") : WtMessage()

    /**
     * Periodic host position broadcast; guests reconcile drift against it.
     * [duration] is the host's known media length (seconds, -1 when unknown)
     * so guests can render a usable timeline even when their own stream has
     * no duration metadata.
     */
    @Serializable
    @SerialName("sync")
    data class Sync(
        val pos: Double,
        val playing: Boolean,
        val rate: Double = 1.0,
        val duration: Double = -1.0,
    ) : WtMessage()

    /** Current media identity. Host sends it; the server keeps the latest for late joiners. */
    @Serializable
    @SerialName("episode")
    data class Episode(
        val title: String = "",
        val name: String = "",
        val number: Double = 0.0,
        /** Room-server media URL guests can play, or null for magnets. */
        val mediaUrl: String? = null,
        /** direct = plain http(s) media, proxy = proxied through the room server, magnet = resolve yourself. */
        val kind: String = "direct",
        val duration: Double = 0.0,
    ) : WtMessage()

    /** Member count + names after join/leave; sent by the server. */
    @Serializable
    @SerialName("members")
    data class Members(val count: Int, val names: List<String> = emptyList()) : WtMessage()

    /** Sent by the server just before a room closes (host left / app shutdown). */
    @Serializable
    @SerialName("room_closed")
    data class RoomClosed(val reason: String = "The host closed the room") : WtMessage()
}

/** JSON codec shared by the server, the app client and the browser join page. */
object WtProtocol {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(message: WtMessage): String = json.encodeToString(message)

    fun decode(text: String): WtMessage? =
        runCatching { json.decodeFromString<WtMessage>(text) }.getOrNull()
}

/** Room code alphabet — unambiguous (no I/O/0/1). */
object WtCodes {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 6
    private val random = java.security.SecureRandom()

    fun newCode(): String = buildString {
        repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    fun isValid(code: String): Boolean = code.length == CODE_LENGTH && code.all { it in ALPHABET }
}
