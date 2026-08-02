package app.anikku.macos.platform.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MacOSBiometricAuthTest {

    private val unavailableHelper = FakeBiometricHelper(available = false)
    private val auth = MacOSBiometricAuth(unavailableHelper)

    @Test
    fun `pin is not initially set`() {
        assertFalse(auth.isPinSet)
    }

    @Test
    fun `set new pin`() {
        auth.setPin("1234")
        assertTrue(auth.isPinSet)
    }

    @Test
    fun `verify correct pin`() {
        auth.setPin("1234")
        assertTrue(auth.verifyPin("1234"))
    }

    @Test
    fun `verify incorrect pin`() {
        auth.setPin("1234")
        assertFalse(auth.verifyPin("5678"))
    }

    @Test
    fun `change pin with correct old pin`() {
        auth.setPin("1234")
        assertTrue(auth.changePin("1234", "5678"))
        assertTrue(auth.verifyPin("5678"))
        assertFalse(auth.verifyPin("1234"))
    }

    @Test
    fun `change pin fails with incorrect old pin`() {
        auth.setPin("1234")
        assertFalse(auth.changePin("wrong", "5678"))
        assertTrue(auth.verifyPin("1234"))
    }

    @Test
    fun `clear pin`() {
        auth.setPin("1234")
        assertTrue(auth.isPinSet)
        auth.clearPin()
        assertFalse(auth.isPinSet)
    }

    @Test
    fun `authenticate with pin when biometrics unavailable`() {
        auth.setPin("1234")
        val result = auth.authenticate(reason = "Test", pin = "1234")
        assertTrue(result)
    }

    @Test
    fun `authenticate fails with wrong pin`() {
        auth.setPin("1234")
        val result = auth.authenticate(reason = "Test", pin = "wrong")
        assertFalse(result)
    }

    @Test
    fun `verify pin is case sensitive`() {
        auth.setPin("AbCd")
        assertTrue(auth.verifyPin("AbCd"))
        assertFalse(auth.verifyPin("abcd"))
        assertFalse(auth.verifyPin("ABCD"))
    }

    @Test
    fun `verify empty pin`() {
        auth.setPin("")
        assertTrue(auth.verifyPin(""))
        assertFalse(auth.verifyPin("not_empty"))
    }

    @Test
    fun `biometric availability returns false on test JVM`() {
        assertFalse(auth.isBiometricAvailable)
    }

    @Test
    fun `available native helper authenticates with bounded timeout`() {
        val helper = FakeBiometricHelper(available = true, evaluationResult = 1)
        val nativeAuth = MacOSBiometricAuth(helper)

        assertTrue(nativeAuth.isBiometricAvailable)
        assertTrue(nativeAuth.authenticateWithBiometrics("  Unlock private library  ", 999))
        assertEquals("Unlock private library", helper.lastReason)
        assertEquals(300, helper.lastTimeout)
    }

    @Test
    fun `native cancellation and timeout fail closed`() {
        val cancelled = MacOSBiometricAuth(FakeBiometricHelper(available = true, evaluationResult = 0))
        val timedOut = MacOSBiometricAuth(FakeBiometricHelper(available = true, evaluationResult = -1))

        assertFalse(cancelled.authenticateWithBiometrics("Unlock", 30))
        assertFalse(timedOut.authenticateWithBiometrics("Unlock", 30))
    }

    @Test
    fun `invalid prompt and timeout never invoke native dialog`() {
        val helper = FakeBiometricHelper(available = true, evaluationResult = 1)
        val nativeAuth = MacOSBiometricAuth(helper)

        assertFalse(nativeAuth.authenticateWithBiometrics("   ", 30))
        assertFalse(nativeAuth.authenticateWithBiometrics("Unlock", 0))
        assertEquals(0, helper.evaluationCalls)
    }

    @Test
    fun `built LocalAuthentication helper can be loaded and queried safely`() {
        val nativeAuth = MacOSBiometricAuth()
        assertDoesNotThrow { nativeAuth.isBiometricAvailable }
    }

    private class FakeBiometricHelper(
        private val available: Boolean,
        private val evaluationResult: Int = 0,
    ) : BiometricHelperLib {
        var lastReason: String? = null
        var lastTimeout: Int? = null
        var evaluationCalls: Int = 0

        override fun anikku_biometric_can_evaluate(): Int = if (available) 1 else 0

        override fun anikku_biometric_evaluate(reason: String, timeoutSeconds: Int): Int {
            evaluationCalls++
            lastReason = reason
            lastTimeout = timeoutSeconds
            return evaluationResult
        }
    }
}
