package app.anikku.macos.platform.watch

import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

private val logger = KotlinLogging.logger {}

/**
 * Zero-config room discovery over the LAN: the host broadcasts a small UDP
 * beacon (code + TCP port + name) once per second; guests listen on the same
 * port and match their room code. No mDNS, no permissions, no server.
 *
 * The beacon payload is `ANIKKU1|<code>|<tcpPort>|<name>`.
 */
object WatchTogetherDiscovery {

    const val DEFAULT_PORT = 18234
    private const val MAGIC = "ANIKKU1"

    data class Advertisement(val code: String, val host: String, val tcpPort: Int, val name: String)

    /**
     * Broadcast the host's room every second. Returns a stop handle; call
     * [Beacon.stop] (or interrupt) to end the loop.
     */
    fun advertise(
        code: String,
        tcpPort: Int,
        name: String,
        target: InetAddress = InetAddress.getByName("255.255.255.255"),
        targetPort: Int = DEFAULT_PORT,
    ): Beacon {
        val beacon = Beacon(code, tcpPort, name, target, targetPort)
        beacon.start()
        return beacon
    }

    class Beacon internal constructor(
        private val code: String,
        private val tcpPort: Int,
        private val name: String,
        private val target: InetAddress,
        private val targetPort: Int,
    ) : Thread("anikku-watch-beacon") {

        @Volatile
        private var running = true

        override fun run() {
            val socket = try {
                DatagramSocket().apply { broadcast = true }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to open beacon socket" }
                return
            }
            val payload = "$MAGIC|$code|$tcpPort|$name".toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(payload, payload.size, target, targetPort)
            try {
                while (running) {
                    runCatching { socket.send(packet) }
                    runCatching { Thread.sleep(1000) }
                }
            } catch (e: InterruptedException) {
                // stop()
            } finally {
                socket.close()
            }
        }

        fun shutdown() {
            running = false
            interrupt()
        }
    }

    /**
     * Listen for beacons until a host advertising [code] is found (or
     * [timeoutMs] elapses). Returns the host's LAN address, or null.
     * The guest socket binds [port]; tests pass an ephemeral port.
     */
    fun findHost(code: String, timeoutMs: Long, port: Int = DEFAULT_PORT): String? {
        val socket = try {
            DatagramSocket(port).apply { soTimeout = 200; reuseAddress = true }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to open discovery listener" }
            return null
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(512)
        try {
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val parsed = parseAdvertisement(text) ?: continue
                if (parsed.code.equals(code, ignoreCase = true)) {
                    logger.info { "Found room $code at ${packet.address.hostAddress}:${parsed.tcpPort}" }
                    return "${packet.address.hostAddress}:${parsed.tcpPort}"
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Discovery listen failed" }
        } finally {
            socket.close()
        }
        return null
    }

    internal fun parseAdvertisement(text: String): Advertisement? {
        val parts = text.split("|")
        if (parts.size != 4 || parts[0] != MAGIC) return null
        val tcpPort = parts[2].toIntOrNull() ?: return null
        return Advertisement(parts[1], "", tcpPort, parts[3])
    }
}

/** The machine's first site-local IPv4 (the address LAN friends connect to). */
object LanAddresses {
    fun siteLocalIPv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
