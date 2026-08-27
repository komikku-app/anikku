package app.anikku.macos.platform.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.EOFException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class DiscordRPCTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var rpc: DiscordRPC? = null
    private val socketPaths = mutableListOf<Path>()

    @AfterEach
    fun tearDown() {
        rpc?.stop()
        scope.cancel()
        socketPaths.forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `initial state is disconnected and stop is idempotent`() {
        val client = DiscordRPC(scope = scope, socketCandidates = { emptyList() })
        rpc = client

        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
        assertFalse(client.isConnected)
        client.stop()
        client.stop()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun `missing Discord socket remains optional and disconnected`() = runBlocking {
        val client = DiscordRPC(
            scope = scope,
            socketCandidates = { listOf(Path.of("/tmp/anikku-discord-socket-does-not-exist")) },
            reconnectDelayMillis = 60_000,
        )
        rpc = client

        client.start()
        withTimeout(5_000) {
            client.connectionState.first { it == ConnectionState.DISCONNECTED }
        }

        assertFalse(client.isConnected)
        client.setPresence("Watching Test Anime", "Episode 1")
        client.clearPresence()
    }

    @Test
    fun `playback presence sanitizes metadata and calculates media timestamps`() {
        assertEquals(
            "Watching My Anime",
            DiscordRPC.normalizeActivityText("Watching My\nAnime", "fallback"),
        )
        assertEquals(128, DiscordRPC.normalizeActivityText("x".repeat(200), "fallback").length)
        assertEquals("fallback", DiscordRPC.normalizeActivityText("\u0000\n", "fallback"))

        val playing = DiscordRPC.playbackPresence(
            animeTitle = "My Anime",
            episodeLabel = "Episode 7",
            positionSeconds = 30.5,
            durationSeconds = 90.0,
            isPaused = false,
            nowMillis = 1_700_000_100_000,
        )
        assertEquals("Watching My Anime", playing.details)
        assertEquals("Episode 7", playing.state)
        assertEquals(1_700_000_069_500, playing.startTimestamp)
        assertEquals(1_700_000_159_500, playing.endTimestamp)

        val paused = DiscordRPC.playbackPresence(
            animeTitle = "My Anime",
            episodeLabel = "Episode 7",
            positionSeconds = Double.NaN,
            durationSeconds = Double.POSITIVE_INFINITY,
            isPaused = true,
            nowMillis = 1_700_000_100_000,
        )
        assertEquals("Episode 7 • Paused", paused.state)
        assertEquals(null, paused.startTimestamp)
        assertEquals(null, paused.endTimestamp)
    }

    @Test
    fun `native IPC performs handshake publishes presence responds to ping and clears activity`() = runBlocking {
        val socketPath = shortSocketPath()
        val received = LinkedBlockingQueue<IpcFrame>()
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(UnixDomainSocketAddress.of(socketPath))
        val serverThread = thread(name = "discord-ipc-test", isDaemon = true) {
            server.use {
                it.accept().use { channel ->
                    received.put(readFrame(channel))
                    writeFrame(channel, 1, """{"cmd":"DISPATCH","evt":"READY","data":{}}""")
                    received.put(readFrame(channel))
                    writeFrame(channel, 3, "ping-token")
                    received.put(readFrame(channel))
                    received.put(readFrame(channel))
                }
            }
        }

        val client = DiscordRPC(
            scope = scope,
            socketCandidates = { listOf(socketPath) },
            reconnectDelayMillis = 60_000,
        )
        rpc = client
        client.setPresence(
            details = "Watching Test Anime",
            state = "Episode 7",
            startTimestamp = 1_700_000_000_000,
            endTimestamp = 1_700_000_300_000,
        )
        client.start()
        withTimeout(5_000) {
            client.connectionState.first { it == ConnectionState.CONNECTED }
        }

        val handshake = received.poll(5, TimeUnit.SECONDS)!!
        val presence = received.poll(5, TimeUnit.SECONDS)!!
        val handshakeJson = Json.parseToJsonElement(handshake.payload).jsonObject
        val presenceJson = Json.parseToJsonElement(presence.payload).jsonObject
        val activity = presenceJson.getValue("args").jsonObject.getValue("activity").jsonObject

        assertEquals(0, handshake.opCode)
        assertEquals(1, handshakeJson.getValue("v").jsonPrimitive.content.toInt())
        assertEquals("1173423931865170070", handshakeJson.getValue("client_id").jsonPrimitive.content)
        assertEquals(1, presence.opCode)
        assertEquals("SET_ACTIVITY", presenceJson.getValue("cmd").jsonPrimitive.content)
        assertEquals(3, activity.getValue("type").jsonPrimitive.content.toInt())
        assertEquals("Watching Test Anime", activity.getValue("details").jsonPrimitive.content)
        assertEquals("Episode 7", activity.getValue("state").jsonPrimitive.content)
        assertEquals(
            1_700_000_000,
            activity.getValue("timestamps").jsonObject.getValue("start").jsonPrimitive.content.toLong(),
        )

        val pong = received.poll(5, TimeUnit.SECONDS)!!
        assertEquals(4, pong.opCode)
        assertEquals("ping-token", pong.payload)

        client.clearPresence()
        val clear = received.poll(5, TimeUnit.SECONDS)!!
        val clearActivity = Json.parseToJsonElement(clear.payload)
            .jsonObject.getValue("args").jsonObject.getValue("activity").jsonPrimitive
        assertEquals("null", clearActivity.content)

        serverThread.join(5_000)
        assertFalse(serverThread.isAlive)
        assertTrue(client.connectionState.value != ConnectionState.CONNECTING)
    }

    private fun shortSocketPath(): Path {
        val directory = Files.createTempDirectory(Path.of("/tmp"), "anikku-ipc-")
        val path = directory.resolve("discord-ipc-0")
        socketPaths.add(path)
        socketPaths.add(directory)
        return path
    }

    private fun readFrame(channel: SocketChannel): IpcFrame {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        readFully(channel, header)
        header.flip()
        val opcode = header.int
        val length = header.int
        val payload = ByteBuffer.allocate(length)
        readFully(channel, payload)
        payload.flip()
        return IpcFrame(opcode, StandardCharsets.UTF_8.decode(payload).toString())
    }

    private fun writeFrame(channel: SocketChannel, opcode: Int, payload: String) {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val frame = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(opcode)
            .putInt(bytes.size)
            .put(bytes)
        frame.flip()
        while (frame.hasRemaining()) channel.write(frame)
    }

    private fun readFully(channel: SocketChannel, buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) throw EOFException("socket closed")
        }
    }

    private data class IpcFrame(val opCode: Int, val payload: String)
}
