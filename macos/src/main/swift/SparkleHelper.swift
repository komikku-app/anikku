import Sparkle
import Foundation
import Darwin

/// Sparkle 2 helper for Anikku macOS.
///
/// This file is compiled into a dynamic library (libSparkleHelper.dylib)
/// and loaded by the Kotlin app via JNA. It wraps Sparkle's
/// SPUStandardUpdaterController to provide C-compatible functions.

private var updaterController: SPUStandardUpdaterController? = nil
private var feedURLCString: UnsafeMutablePointer<CChar>? = nil

/// Sparkle is an AppKit framework and requires all updater access on the
/// process's native main thread. The JVM's AWT event thread is not that thread.
private func onMainThread<T>(_ body: @escaping () -> T) -> T {
    if Thread.isMainThread {
        return body()
    }
    return DispatchQueue.main.sync(execute: body)
}

/// Initialize Sparkle with an optional feed URL.
/// Sparkle's effective feed comes from the packaged Info.plist; when an
/// explicit URL is supplied it is validated against that effective URL.
@_cdecl("sparkle_init")
public func sparkle_init(feedURL: UnsafePointer<CChar>?) -> Bool {
    let requestedValue = feedURL.map { String(cString: $0) }
    return onMainThread {
        guard updaterController == nil else {
            return true
        }

        let requestedURL: URL?
        if let requestedValue {
            guard !requestedValue.isEmpty, let parsed = URL(string: requestedValue),
                  parsed.scheme?.lowercased() == "https", parsed.host != nil else {
                return false
            }
            requestedURL = parsed
        } else {
            requestedURL = nil
        }

        let controller = SPUStandardUpdaterController(
            startingUpdater: true,
            updaterDelegate: nil,
            userDriverDelegate: nil
        )

        // Enable silent background checks. Sparkle refuses checkForUpdatesInBackground
        // (and logs an error) while automaticallyChecksForUpdates is NO, because it is
        // set to ask the user for permission first. Granting automatic checks here
        // makes the 30s-after-startup background check legitimate and lets Sparkle
        // drive its own daily schedule.
        controller.updater.automaticallyChecksForUpdates = true
        controller.updater.updateCheckInterval = 24 * 60 * 60 // daily

        // Sparkle resolves SUFeedURL from the packaged Info.plist. Do not treat
        // the optional input parameter as proof that the effective feed is safe.
        guard let effectiveURL = controller.updater.feedURL,
              effectiveURL.scheme?.lowercased() == "https",
              effectiveURL.host != nil else {
            return false
        }
        if let requestedURL,
           requestedURL.absoluteString != effectiveURL.absoluteString {
            return false
        }

        if let oldFeedURL = feedURLCString { free(oldFeedURL) }
        feedURLCString = strdup(effectiveURL.absoluteString)
        updaterController = controller
        return true
    }
}

/// Show the standard Sparkle update window.
@_cdecl("sparkle_checkForUpdates")
public func sparkle_checkForUpdates() {
    onMainThread {
        updaterController?.updater.checkForUpdates()
    }
}

/// Perform a silent background check for updates.
@_cdecl("sparkle_checkInBackground")
public func sparkle_checkInBackground() {
    onMainThread {
        updaterController?.updater.checkForUpdatesInBackground()
    }
}

/// Release the native updater controller and its callbacks.
@_cdecl("sparkle_shutdown")
public func sparkle_shutdown() {
    onMainThread {
        updaterController = nil
        if let oldFeedURL = feedURLCString { free(oldFeedURL) }
        feedURLCString = nil
    }
}

/// Return the configured Sparkle feed URL.
/// The caller copies the value immediately. Storage is retained until shutdown
/// so repeated queries do not leak a new strdup allocation.
@_cdecl("sparkle_feedURL")
public func sparkle_feedURL() -> UnsafePointer<CChar>? {
    let readFeedURL: () -> UnsafePointer<CChar>? = {
        guard updaterController != nil, let value = feedURLCString else { return nil }
        return UnsafePointer(value)
    }
    return onMainThread(readFeedURL)
}
