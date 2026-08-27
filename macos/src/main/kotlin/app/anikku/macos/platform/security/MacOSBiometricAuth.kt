package app.anikku.macos.platform.security

import com.sun.jna.Library
import com.sun.jna.Native
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val logger = KotlinLogging.logger {}

/** Stable C ABI exposed by `BiometricHelper.swift`. */
interface BiometricHelperLib : Library {
    fun anikku_biometric_can_evaluate(): Int
    fun anikku_biometric_evaluate(reason: String, timeoutSeconds: Int): Int
}

/**
 * macOS app-lock authentication using LocalAuthentication with a PIN fallback.
 *
 * The native helper asks macOS to evaluate the biometric-only device-owner
 * policy. Anikku receives only a success/failure result; fingerprint data never
 * leaves the operating system or Secure Enclave.
 */
class MacOSBiometricAuth(
    private val biometricHelper: BiometricHelperLib? = loadBiometricHelper(),
    private val secretStore: MacOSSecretStore? = null,
) {

    companion object {
        private const val PIN_HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PIN_ITERATIONS = 100_000
        private const val PIN_KEY_LENGTH = 256
        private const val PIN_STORAGE_KEY = "app_lock_pin"
        private const val PIN_RECORD_VERSION = "v2"
        private const val PIN_SALT_BYTES = 16

        private fun loadBiometricHelper(): BiometricHelperLib? {
            if (!System.getProperty("os.name", "").contains("mac", ignoreCase = true)) return null

            return runCatching {
                Native.load("AnikkuBiometric", BiometricHelperLib::class.java)
            }.recoverCatching {
                val path = findDevelopmentHelper()
                    ?: throw UnsatisfiedLinkError("libAnikkuBiometric.dylib was not found")
                Native.load(path, BiometricHelperLib::class.java)
            }.onSuccess {
                logger.info { "LocalAuthentication helper loaded" }
            }.onFailure {
                logger.info { "LocalAuthentication helper unavailable; PIN fallback remains enabled" }
            }.getOrNull()
        }

        private fun findDevelopmentHelper(): String? {
            val resources = System.getProperty("compose.application.resources.dir")
                ?.takeIf(String::isNotBlank)
                ?.let(::File)
            val cwd = File(System.getProperty("user.dir", "."))
            return listOfNotNull(
                resources?.resolve("Frameworks/libAnikkuBiometric.dylib"),
                cwd.resolve("build/native/libAnikkuBiometric.dylib"),
                cwd.resolve("macos/build/native/libAnikkuBiometric.dylib"),
            ).firstOrNull(File::isFile)?.absolutePath
        }
    }

    @Volatile
    private var pinRecord: String? = loadPinRecord()

    val isPinSet: Boolean get() = pinRecord != null

    /** Re-check every time because enrolled biometrics can change at runtime. */
    val isBiometricAvailable: Boolean
        get() = try {
            biometricHelper?.anikku_biometric_can_evaluate() == 1
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check biometric availability" }
            false
        } catch (e: UnsatisfiedLinkError) {
            logger.warn(e) { "LocalAuthentication helper has an incompatible ABI" }
            false
        }

    fun authenticateWithBiometrics(
        reason: String = "Unlock Anikku",
        timeoutSeconds: Int = 30,
    ): Boolean {
        if (reason.isBlank() || timeoutSeconds <= 0) return false
        val helper = biometricHelper ?: return false
        if (!isBiometricAvailable) return false

        return try {
            when (helper.anikku_biometric_evaluate(reason.trim(), timeoutSeconds.coerceAtMost(300))) {
                1 -> true.also { logger.info { "Biometric authentication succeeded" } }
                -1 -> false.also { logger.warn { "Biometric authentication timed out" } }
                else -> false.also { logger.info { "Biometric authentication failed or was cancelled" } }
            }
        } catch (e: Exception) {
            logger.error(e) { "Biometric authentication error" }
            false
        } catch (e: UnsatisfiedLinkError) {
            logger.error(e) { "LocalAuthentication helper has an incompatible ABI" }
            false
        }
    }

    fun authenticate(reason: String = "Unlock Anikku", pin: String? = null): Boolean {
        if (isBiometricAvailable && authenticateWithBiometrics(reason)) return true
        return pin != null && isPinSet && verifyPin(pin)
    }

    fun setPin(pin: String): Boolean {
        val salt = ByteArray(PIN_SALT_BYTES).also(SecureRandom()::nextBytes)
        val record = encodePinRecord(salt, hashPin(pin, salt))
        if (secretStore != null) {
            if (!secretStore.isAvailable || !secretStore.store(PIN_STORAGE_KEY, record)) {
                logger.error { "Failed to store app-lock PIN securely: ${secretStore.lastError ?: "Keychain unavailable"}" }
                return false
            }
        }
        pinRecord = record
        logger.info { "App-lock PIN set" }
        return true
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        return setPin(newPin)
    }

    fun clearPin(): Boolean {
        if (secretStore != null && (!secretStore.isAvailable || !secretStore.delete(PIN_STORAGE_KEY))) {
            logger.error { "Failed to clear app-lock PIN securely: ${secretStore.lastError ?: "Keychain unavailable"}" }
            return false
        }
        pinRecord = null
        logger.info { "App-lock PIN cleared" }
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val record = pinRecord ?: return false
        val parts = record.split(':')
        if (parts.size != 3 || parts[0] != PIN_RECORD_VERSION) return false
        return try {
            val salt = Base64.getDecoder().decode(parts[1])
            val expected = Base64.getDecoder().decode(parts[2])
            MessageDigest.isEqual(hashPin(pin, salt), expected)
        } catch (e: IllegalArgumentException) {
            logger.warn(e) { "Stored app-lock PIN record is malformed" }
            false
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_ITERATIONS, PIN_KEY_LENGTH)
        return try {
            SecretKeyFactory.getInstance(PIN_HASH_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodePinRecord(salt: ByteArray, hash: ByteArray): String = listOf(
        PIN_RECORD_VERSION,
        Base64.getEncoder().encodeToString(salt),
        Base64.getEncoder().encodeToString(hash),
    ).joinToString(":")

    private fun loadPinRecord(): String? {
        val store = secretStore ?: return null
        if (!store.isAvailable) return null
        return store.retrieve(PIN_STORAGE_KEY)?.takeIf { it.isNotBlank() }
    }
}
