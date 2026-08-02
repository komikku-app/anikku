package app.anikku.macos.platform.security

import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val logger = KotlinLogging.logger {}

/**
 * macOS biometric authentication service.
 *
 * Supports two authentication methods:
 * 1. **Touch ID / Face ID** — via macOS LocalAuthentication framework (JNA bridge)
 * 2. **PIN/Password fallback** — app-internal PIN with PBKDF2 hashing
 *
 * ## Touch ID Usage
 *
 * ```kotlin
 * val biometric = MacOSBiometricAuth()
 * val authenticated = biometric.authenticateWithBiometrics(
 *     reason = "Unlock Anikku to access your library",
 *     timeoutSeconds = 30,
 * )
 * ```
 *
 * ## PIN Usage
 *
 * ```kotlin
 * biometric.setPin("1234")
 * val verified = biometric.verifyPin("1234")
 * ```
 */
class MacOSBiometricAuth {

    companion object {
        private const val PIN_HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PIN_ITERATIONS = 100_000
        private const val PIN_KEY_LENGTH = 256
        private const val PIN_SALT = "AnikkuBiometricSalt_v1" // Static salt is acceptable for app-internal PIN
    }

    private var pinHash: String? = null

    /** Whether a PIN has been set. */
    val isPinSet: Boolean get() = pinHash != null

    /**
     * Whether biometric authentication is available on this Mac.
     *
     * Checks for Touch ID hardware support.
     * Requires macOS 10.12.2+ with a Touch Bar (Touch ID) or Apple Silicon Mac.
     */
    val isBiometricAvailable: Boolean
        get() {
            // Check if running on macOS (not headless)
            return try {
                val osName = System.getProperty("os.name").lowercase()
                if (!osName.contains("mac")) return false

                // Check for biometric hardware via LocalAuthentication framework
                // This uses JNA to call LAContext.canEvaluatePolicy()
                // For v1, biometric hardware detection via JNA is not yet wired.
                // Return false to trigger PIN fallback path.
                logger.warn { "Touch ID hardware detection via JNA not yet implemented" }
                false
            } catch (e: Exception) {
                logger.warn(e) { "Failed to check biometric availability" }
                false
            }
        }

    /**
     * Authenticate using biometrics (Touch ID).
     *
     * On macOS, this uses the LocalAuthentication framework via JNA to show
     * the Touch ID dialog. If the Mac doesn't have Touch ID, falls back to
     * PIN verification.
     *
     * @param reason The reason displayed in the Touch ID dialog.
     * @param timeoutSeconds Maximum time to wait for biometric authentication.
     * @return true if authentication succeeded.
     */
    fun authenticateWithBiometrics(
        reason: String = "Unlock Anikku",
        timeoutSeconds: Int = 30,
    ): Boolean {
        if (!isBiometricAvailable) {
            logger.warn { "Biometric authentication not available on this Mac" }
            return false
        }

        return try {
            // Use JNA to call macOS LocalAuthentication framework
            // LAContext.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics,
            //                         localizedReason: reason)
            val result = CompletableFuture<Boolean>()

            // Evaluate biometric policy
            val authenticated = evaluateBiometricPolicy(reason, timeoutSeconds)

            if (authenticated) {
                logger.info { "Biometric authentication succeeded" }
            } else {
                logger.warn { "Biometric authentication failed or was cancelled" }
            }

            authenticated
        } catch (e: Exception) {
            logger.error(e) { "Biometric authentication error" }
            false
        }
    }

    /**
     * Authenticate using either biometrics or PIN (tries biometrics first).
     *
     * @param reason The reason for authentication.
     * @param pin The PIN to verify (if biometrics unavailable).
     * @return true if any authentication method succeeded.
     */
    fun authenticate(reason: String = "Unlock Anikku", pin: String? = null): Boolean {
        // Try biometrics first
        if (isBiometricAvailable) {
            val biometricResult = authenticateWithBiometrics(reason)
            if (biometricResult) return true
        }

        // Fall back to PIN
        if (pin != null && isPinSet) {
            return verifyPin(pin)
        }

        return false
    }

    // -------------------------------------------------------------------------
    // PIN Management
    // -------------------------------------------------------------------------

    /**
     * Set a new PIN. Hashes it with PBKDF2 before storing.
     *
     * @param pin The PIN to set (numeric or alphanumeric).
     */
    fun setPin(pin: String) {
        pinHash = hashPin(pin)
        logger.info { "Biometric PIN set" }
    }

    /**
     * Change the PIN. Requires the old PIN for verification.
     *
     * @param oldPin The current PIN.
     * @param newPin The new PIN.
     * @return true if the PIN was changed successfully.
     */
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        setPin(newPin)
        return true
    }

    /**
     * Clear the PIN (disable PIN lock).
     */
    fun clearPin() {
        pinHash = null
        logger.info { "Biometric PIN cleared" }
    }

    /**
     * Verify a PIN against the stored hash.
     *
     * @param pin The PIN to verify.
     * @return true if the PIN matches.
     */
    fun verifyPin(pin: String): Boolean {
        val hash = pinHash ?: return false
        return MessageDigest.isEqual(
            hashPin(pin).toByteArray(),
            hash.toByteArray(),
        )
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun hashPin(pin: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), PIN_SALT.toByteArray(), PIN_ITERATIONS, PIN_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PIN_HASH_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(hash)
    }

    /**
     * Call macOS LocalAuthentication framework via JNA.
     *
     * Evaluates `LAPolicy.deviceOwnerAuthenticationWithBiometrics`.
     *
     * This is a simplified version. The actual implementation requires:
     * 1. JNA mapping to `LocalAuthentication.framework`
     * 2. Calling `LAContext.evaluatePolicy()` on the main thread
     * 3. Handling the async reply block with a callback
     *
     * For v1, we log that biometric auth is not yet fully wired via JNA
     * and return false (which triggers the PIN fallback path).
     */
    private fun evaluateBiometricPolicy(reason: String, timeoutSeconds: Int): Boolean {
        // Unsupported until a LocalAuthentication JNA bridge with an async
        // callback is bundled. Keep this explicit so callers use the PIN
        // fallback instead of mistaking a silent false for a successful check.
        logger.warn {
            "Touch ID via JNA not yet implemented. " +
                "Falling back to PIN authentication."
        }
        return false
    }
}
