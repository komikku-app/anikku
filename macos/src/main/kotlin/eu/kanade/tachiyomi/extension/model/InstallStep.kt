package eu.kanade.tachiyomi.extension.model

/**
 * Installation progress steps for extension downloads.
 */
sealed class InstallStep {
    data class Downloading(val progress: Float) : InstallStep()
    data object Installing : InstallStep()
    data object Complete : InstallStep()
    data class Error(val message: String) : InstallStep()
}
