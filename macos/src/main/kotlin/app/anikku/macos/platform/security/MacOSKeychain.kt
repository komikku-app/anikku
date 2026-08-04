package app.anikku.macos.platform.security

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream

private val logger = KotlinLogging.logger {}

/**
 * macOS Keychain wrapper — stores sensitive strings (OAuth tokens, client secrets)
 * in the user's login Keychain via the `security` command-line tool.
 *
 * This is the macOS equivalent of Android's EncryptedSharedPreferences / Keystore.
 * Data is stored in the user's Keychain, which is encrypted at rest and only
 * accessible while the user is logged in to macOS.
 *
 * ## Usage
 *
 * ```kotlin
 * val keychain = MacOSKeychain(service = "anikku-tracker")
 * keychain.store("myanimelist-token", "access_token_value")
 * val token = keychain.retrieve("myanimelist-token")
 * keychain.delete("myanimelist-token")
 * ```
 *
 * ## Security properties
 *
 * - Data is encrypted with the user's login password (FileVault-class encryption)
 * - Accessible only while the user is logged in
 * - Survives app reinstallation (stored in system Keychain, not app sandbox)
 * - Not accessible by other apps without explicit Keychain access
 *
 * ## Thread safety
 *
 * All methods are safe to call from any thread. Each call spawns a short-lived
 * `security` process. Avoid calling from the main thread in tight loops.
 */
interface MacOSSecretStore {
    val isAvailable: Boolean
    val lastError: String?
    fun store(key: String, value: String): Boolean
    fun retrieve(key: String): String?
    fun delete(key: String): Boolean
}

class MacOSKeychain(
    /** Keychain service name — scopes entries so they don't collide with other apps. */
    private val service: String = "anikku",
    /** Keychain account name — groups related entries under one account. */
    private val account: String = "anikku-app",
) : MacOSSecretStore {

    @Volatile
    override var lastError: String? = null
        private set

    /** Whether the `security` CLI tool is available on this system. */
    override val isAvailable: Boolean by lazy {
        try {
            val process = ProcessBuilder("which", "security")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check security CLI availability" }
            false
        }
    }

    /**
     * Store a value in the Keychain under the given key.
     * If an entry with the same key already exists, it is updated.
     *
     * @param key The key to store the value under (e.g., "tracker_token_myanimelist").
     * @param value The value to store.
     * @return true if the value was stored successfully.
     */
    @Synchronized
    override fun store(key: String, value: String): Boolean {
        if (value.isBlank()) return delete(key)

        return try {
            // Pass the secret as the -w ARGUMENT, not via stdin: `security
            // add-generic-password -w` with no argument INTERACTIVELY PROMPTS
            // for the password ("password data for new item:") instead of
            // reading stdin — a piped value only answers the first prompt, the
            // retype prompt fails, and the item is created with an EMPTY
            // password (still exit 0). That made every keychain read return
            // null and OAuth sessions appear "not connected". argv is only
            // visible to the same user, so this is safe.
            val updateResult = runCommand(
                "security", "add-generic-password",
                "-a", account,
                "-s", "$service-$key",
                "-U", // Update if exists
                "-j", service, // Service label for organization
                "-w", value,
            )

            if (updateResult.exitCode == 0) {
                lastError = null
                logger.debug { "Keychain: stored $key (${value.length} chars)" }
                true
            } else {
                lastError = "security add-generic-password exited with ${updateResult.exitCode}"
                logger.warn { "Keychain: failed to store $key (exit ${updateResult.exitCode}): ${updateResult.stderr.take(100)}" }
                false
            }
        } catch (e: Exception) {
            lastError = e.message ?: e::class.simpleName
            logger.warn(e) { "Keychain: error storing $key" }
            false
        }
    }

    /**
     * Retrieve a value from the Keychain by key.
     *
     * @param key The key to look up.
     * @return The stored value, or null if no entry exists.
     */
    @Synchronized
    override fun retrieve(key: String): String? {
        return try {
            val result = runCommand(
                "security", "find-generic-password",
                "-a", account,
                "-s", "$service-$key",
                "-w", // Output only the password
            )

            if (result.exitCode == 0) {
                val value = result.stdout.trimEnd('\n')
                if (value.isNotBlank()) {
                    lastError = null
                    logger.debug { "Keychain: retrieved $key (${value.length} chars)" }
                    value
                } else {
                    lastError = null
                    null
                }
            } else {
                // 44 is errSecItemNotFound: absence is not a keychain failure.
                lastError = if (result.exitCode == 44) null else {
                    "security find-generic-password exited with ${result.exitCode}"
                }
                logger.debug { "Keychain: no entry found for $key (exit ${result.exitCode})" }
                null
            }
        } catch (e: Exception) {
            lastError = e.message ?: e::class.simpleName
            logger.warn(e) { "Keychain: error retrieving $key" }
            null
        }
    }

    /**
     * Delete a value from the Keychain by key.
     *
     * @param key The key to delete.
     * @return true if the entry was deleted or didn't exist.
     */
    @Synchronized
    override fun delete(key: String): Boolean {
        return try {
            val result = runCommand(
                "security", "delete-generic-password",
                "-a", account,
                "-s", "$service-$key",
            )

            val success = result.exitCode == 0 || result.exitCode == 44 // 44 = item not found
            lastError = if (success) null else "security delete-generic-password exited with ${result.exitCode}"
            if (success) {
                logger.debug { "Keychain: deleted $key" }
            } else {
                logger.warn { "Keychain: failed to delete $key (exit ${result.exitCode})" }
            }
            success
        } catch (e: Exception) {
            lastError = e.message ?: e::class.simpleName
            logger.warn(e) { "Keychain: error deleting $key" }
            false
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun runCommand(vararg args: String): CommandResult =
        runCommandWithStdin(null, *args)

    private fun runCommandWithStdin(stdin: String?, vararg args: String): CommandResult {
        val process = ProcessBuilder(*args)
            .redirectErrorStream(false)
            .start()

        if (stdin != null) {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(stdin) }
        } else {
            process.outputStream.close()
        }

        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()
        val stdoutReader = Thread({ process.inputStream.transferTo(stdoutStream) }, "keychain-stdout")
        val stderrReader = Thread({ process.errorStream.transferTo(stderrStream) }, "keychain-stderr")
        stdoutReader.isDaemon = true
        stderrReader.isDaemon = true
        stdoutReader.start()
        stderrReader.start()

        val exitCode = process.waitFor()
        stdoutReader.join()
        stderrReader.join()

        return CommandResult(
            exitCode = exitCode,
            stdout = stdoutStream.toString("UTF-8"),
            stderr = stderrStream.toString("UTF-8"),
        )
    }
}
