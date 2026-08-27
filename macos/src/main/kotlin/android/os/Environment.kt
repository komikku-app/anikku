package android.os

import java.io.File

/**
 * Stub for `android.os.Environment` on macOS JVM.
 */
object Environment {

    @JvmField
    val MEDIA_MOUNTED: String = "mounted"

    @JvmField
    val MEDIA_MOUNTED_READ_ONLY: String = "mounted_ro"

    const val DIRECTORY_MUSIC: String = "Music"
    const val DIRECTORY_PODCASTS: String = "Podcasts"
    const val DIRECTORY_RINGTONES: String = "Ringtones"
    const val DIRECTORY_ALARMS: String = "Alarms"
    const val DIRECTORY_NOTIFICATIONS: String = "Notifications"
    const val DIRECTORY_PICTURES: String = "Pictures"
    const val DIRECTORY_MOVIES: String = "Movies"
    const val DIRECTORY_DOWNLOADS: String = "Downloads"
    const val DIRECTORY_DCIM: String = "DCIM"
    const val DIRECTORY_DOCUMENTS: String = "Documents"

    @JvmStatic
    fun getExternalStorageDirectory(): File =
        File(System.getProperty("user.home"), ".Anikku/storage")

    @JvmStatic
    fun getExternalStoragePublicDirectory(type: String): File =
        File(getExternalStorageDirectory(), type)

    @JvmStatic
    fun getExternalStorageState(): String = MEDIA_MOUNTED

    @JvmStatic
    fun getExternalStorageState(path: File): String = MEDIA_MOUNTED

    @JvmStatic
    fun isExternalStorageRemovable(): Boolean = false

    @JvmStatic
    fun isExternalStorageEmulated(): Boolean = true

    @JvmStatic
    fun getRootDirectory(): File = File(System.getProperty("user.home"), ".Anikku")
}
