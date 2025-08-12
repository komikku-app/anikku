package eu.kanade.tachiyomi.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Try to guess if the backup is an old aniyomi backup.
 *
 * Returns true if it's (probably) an old aniyomi backup, or false if it's a new aniyomi backup.
 */
object BackupDetector {
    @Serializable
    data class BackupDetector(
        @ProtoNumber(500) val isLegacy: Boolean = true,
    )

    fun isLegacyBackup(bytes: ByteArray): Boolean {
        return try {
            val detect = ProtoBuf.decodeFromByteArray(BackupDetector.serializer(), bytes)
            detect.isLegacy
        } catch (_: SerializationException) {
            false
        }
    }
}
