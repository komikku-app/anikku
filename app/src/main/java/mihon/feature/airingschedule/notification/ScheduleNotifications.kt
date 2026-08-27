package mihon.feature.airingschedule.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import mihon.feature.airingschedule.AiringScheduleEntry
import mihon.feature.airingschedule.SchedulePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Schedules/cancels the local alarms that back the per-anime "notify me" bell on the
 * Schedule tab. Notifications are delivered by [ScheduleAlarmReceiver] at episode air time.
 */
object ScheduleNotifications {

    private val schedulePreferences: SchedulePreferences by lazy { Injekt.get() }

    // The persisted "scheduled alarm keys" set is read-modified-written from multiple
    // independent callers (UI toggle taps, the weekly refresh worker, and the alarm receiver
    // itself when an alarm fires) which can race and lose updates if not serialized. All
    // mutating access goes through this mutex.
    private val keysMutex = Mutex()

    private inline fun <T> withKeysLock(crossinline block: () -> T): T = runBlocking { keysMutex.withLock { block() } }

    fun alarmKey(mediaId: Int, episode: Int): String = "$mediaId:$episode"

    fun requestCode(mediaId: Int, episode: Int): Int = alarmKey(mediaId, episode).hashCode()

    /**
     * Whether the app is currently allowed to schedule *exact* alarms. On Android 12
     * (API 31) this permission is auto-granted; on Android 13+ it can be revoked by the
     * user or the system, so callers should check this and, if false, prompt the user via
     * [requestExactAlarmPermission] so the schedule bell fires at the precise air time
     * instead of being deferred by battery-optimization/Doze batching windows.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /** Opens the system settings screen where the user can grant the exact-alarm permission. */
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Ensures an alarm is scheduled for [entry] if it hasn't aired yet and isn't already
     * pending. Returns true if an alarm is now scheduled (or already was), false if the
     * episode has already aired and nothing was scheduled — callers must not persist a
     * "notify" preference in that case since no alarm backs it.
     */
    fun ensureScheduled(context: Context, entry: AiringScheduleEntry): Boolean = withKeysLock {
        if (entry.hasAired()) return@withKeysLock false
        val key = alarmKey(entry.mediaId, entry.episode)
        val scheduled = schedulePreferences.scheduledAlarmKeys().get()
        if (key in scheduled) return@withKeysLock true

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return@withKeysLock false
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra(ScheduleAlarmReceiver.EXTRA_MEDIA_ID, entry.mediaId)
            putExtra(ScheduleAlarmReceiver.EXTRA_EPISODE, entry.episode)
            putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, entry.titleUserPreferred)
            putExtra(ScheduleAlarmReceiver.EXTRA_COVER_URL, entry.coverImageUrl)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(entry.mediaId, entry.episode),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAtMillis = entry.airingAt * 1000L
        runCatching {
            if (canScheduleExactAlarms(context)) {
                // Fires at the precise millisecond even in Doze/App Standby, regardless of
                // whether the user has disallowed background battery usage for the app —
                // exact RTC_WAKEUP alarms are not subject to that restriction.
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                // No exact-alarm permission granted (Android 13+ user/system revoked it) —
                // fall back to an inexact alarm; timing may drift under Doze.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            // Only persist the key after a successful registration so that a future call
            // can retry if the platform rejected the alarm. Re-read the current set under
            // the lock rather than reusing the stale `scheduled` snapshot, since another
            // caller could have mutated it while the alarm was being registered.
            val current = schedulePreferences.scheduledAlarmKeys().get()
            schedulePreferences.scheduledAlarmKeys().set(current + key)
            true
        }.getOrDefault(false)
    }

    /** Cancels a single previously-scheduled alarm for [entry], if any. */
    fun cancel(context: Context, entry: AiringScheduleEntry) = withKeysLock {
        val key = alarmKey(entry.mediaId, entry.episode)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return@withKeysLock
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(entry.mediaId, entry.episode),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        schedulePreferences.scheduledAlarmKeys().set(schedulePreferences.scheduledAlarmKeys().get() - key)
    }

    /** Cancels every alarm currently scheduled for episodes belonging to [mediaId]. */
    fun cancelAllForMedia(context: Context, mediaId: Int, entries: List<AiringScheduleEntry>) {
        entries.filter { it.mediaId == mediaId }.forEach { cancel(context, it) }
    }

    /** Removes the persisted alarm key for an already-fired alarm (receiver path). */
    fun removeAlarmKey(mediaId: Int, episode: Int) = withKeysLock {
        val key = alarmKey(mediaId, episode)
        schedulePreferences.scheduledAlarmKeys().set(schedulePreferences.scheduledAlarmKeys().get() - key)
    }
}
