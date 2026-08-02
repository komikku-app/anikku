import Foundation
import LocalAuthentication

/// C-compatible LocalAuthentication bridge loaded from Kotlin through JNA.
/// Only success/failure crosses the boundary; biometric data remains entirely
/// within macOS and the Secure Enclave.

@_cdecl("anikku_biometric_can_evaluate")
public func anikku_biometric_can_evaluate() -> Int32 {
    let context = LAContext()
    var error: NSError?
    return context.canEvaluatePolicy(
        .deviceOwnerAuthenticationWithBiometrics,
        error: &error
    ) ? 1 : 0
}

@_cdecl("anikku_biometric_evaluate")
public func anikku_biometric_evaluate(
    reason: UnsafePointer<CChar>?,
    timeoutSeconds: Int32
) -> Int32 {
    guard let reason else { return 0 }
    let localizedReason = String(cString: reason).trimmingCharacters(in: .whitespacesAndNewlines)
    guard !localizedReason.isEmpty else { return 0 }

    let context = LAContext()
    var availabilityError: NSError?
    guard context.canEvaluatePolicy(
        .deviceOwnerAuthenticationWithBiometrics,
        error: &availabilityError
    ) else {
        return 0
    }

    let semaphore = DispatchSemaphore(value: 0)
    let result = UnsafeMutablePointer<Int32>.allocate(capacity: 1)
    result.initialize(to: 0)
    defer {
        result.deinitialize(count: 1)
        result.deallocate()
    }

    context.evaluatePolicy(
        .deviceOwnerAuthenticationWithBiometrics,
        localizedReason: localizedReason
    ) { success, _ in
        result.pointee = success ? 1 : 0
        semaphore.signal()
    }

    let boundedTimeout = max(1, min(Int(timeoutSeconds), 300))
    if semaphore.wait(timeout: .now() + .seconds(boundedTimeout)) == .timedOut {
        context.invalidate()
        return -1
    }
    return result.pointee
}
