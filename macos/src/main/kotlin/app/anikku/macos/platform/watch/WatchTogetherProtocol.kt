package app.anikku.macos.platform.watch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Wire protocol for Watch Together rooms (JSON over WebSocket — LAN, or over
 * the internet through the bundled Cloudflare tunnel).
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

    /** Host: lock/unlock room controls. While locked only the host can control playback. */
    @Serializable
    @SerialName("lock")
    data class Lock(val locked: Boolean, val by: String = "") : WtMessage()

    /** Host: remove a member by name. The kicked member's socket is closed. */
    @Serializable
    @SerialName("kick")
    data class Kick(val name: String, val by: String = "") : WtMessage()

    /** Any member: change playback speed for the whole room. */
    @Serializable
    @SerialName("speed")
    data class Speed(val rate: Double, val by: String = "") : WtMessage()

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
        /**
         * anikku:// deep link that opens this episode in the app. Set for
         * magnet rooms (which browsers can't stream) so the join page can
         * offer "Open in Anikku" instead of a dead end.
         */
        val appDeepLink: String? = null,
    ) : WtMessage()

    /** Member count + names + host after join/leave; sent by the server. */
    @Serializable
    @SerialName("members")
    data class Members(
        val count: Int,
        val names: List<String> = emptyList(),
        val hostName: String? = null,
    ) : WtMessage()

    /** Sent by the server just before a room closes (host left / app shutdown). */
    @Serializable
    @SerialName("room_closed")
    data class RoomClosed(val reason: String = "The host closed the room") : WtMessage()

    /**
     * Room chat. Clients send with [text] (and optionally [image], a base64
     * data URL, plus its file [name]); the server stamps [by] (the sender's
     * Hello name — never trusted from the client) and [ts] before relaying
     * to everyone, and keeps a short buffer for late joiners.
     */
    @Serializable
    @SerialName("chat")
    data class Chat(
        val text: String = "",
        val by: String = "",
        val ts: Long = 0L,
        /** Optional attached image as a `data:` URL (PNG/GIF/JPEG). */
        val image: String = "",
        /** Original file name of the attached image. */
        val name: String = "",
    ) : WtMessage()

    companion object {
        /** Largest image chat payload, in base64 characters (~10 MB decoded). */
        const val MAX_CHAT_IMAGE_BASE64 = 13_400_000
    }
}

/** JSON codec shared by the server, the app client and the browser join page. */
object WtProtocol {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(message: WtMessage): String = json.encodeToString(message)

    fun decode(text: String): WtMessage? =
        runCatching { json.decodeFromString<WtMessage>(text) }.getOrNull()
}

/**
 * Builds a `data:` URL for [file] so it can travel over the room websocket
 * (screenshots and GIF clips from the player). Returns null when the file is
 * unreadable or larger than [WtMessage.MAX_CHAT_IMAGE_BASE64] (~10 MB decoded).
 */
fun wtImageDataUrl(file: java.io.File): String? {
    return try {
        if (!file.isFile || file.length() > 10_000_000L) return null
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
        val base64 = java.util.Base64.getEncoder().encodeToString(file.readBytes())
        if (base64.length > WtMessage.MAX_CHAT_IMAGE_BASE64) return null
        "data:$mime;base64,$base64"
    } catch (_: Exception) {
        null
    }
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

/**
 * Parsing for shared room links — the internet way to join a room.
 *
 * A host exposing its room through the Cloudflare tunnel shares
 * `https://<tunnel>/room/<CODE>`; a guest pastes that link (or the bare code,
 * which still means LAN discovery). Links carry the host, so no UDP discovery
 * is needed: https/wss links join over a TLS WebSocket, http/ws over plain.
 */
object WtLinks {

    /** A join target extracted from a shared link. */
    data class JoinTarget(
        val secure: Boolean,
        val host: String,
        val port: Int,
        val code: String,
    )

    private val LINK_REGEX = Regex(
        "^(https?|wss?)://([^/:]+)(?::(\\d+))?/room/([A-Za-z0-9]{6})/?$",
    )

    fun parse(input: String): JoinTarget? {
        val match = LINK_REGEX.matchEntire(input.trim()) ?: return null
        val scheme = match.groupValues[1]
        val host = match.groupValues[2]
        val portText = match.groupValues[3]
        val code = match.groupValues[4].uppercase()
        if (!WtCodes.isValid(code)) return null
        val secure = scheme == "https" || scheme == "wss"
        val port = when {
            portText.isNotEmpty() -> portText.toIntOrNull() ?: return null
            secure -> 443
            else -> 80
        }
        return JoinTarget(secure, host, port, code)
    }

    /** Whether [input] is a full join link (vs. a bare code for LAN discovery). */
    fun isJoinable(input: String): Boolean = parse(input) != null
}
