package app.anikku.macos.platform

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Replaces Android WorkManager for background tasks on macOS desktop.
 *
 * Key difference from Android: on macOS, there is no guaranteed background execution
 * when the app is closed. Tasks run only while the app is open, which matches user
 * expectations for a desktop app.
 *
 * Usage:
 * ```
 * val scheduler = BackgroundTaskScheduler(applicationScope)
 * scheduler.schedulePeriodic("library-update", intervalMinutes = 60) {
 *     updateLibrary()
 * }
 * scheduler.runOnce("backup") {
 *     createBackup()
 * }
 * ```
 */
class BackgroundTaskScheduler(
    private val scope: CoroutineScope,
) {

    private val runningTasks = ConcurrentHashMap<String, Job>()

    /**
     * Schedules a periodic task that runs at the given interval.
     * Runs once immediately, then repeats after each interval.
     */
    fun schedulePeriodic(
        name: String,
        intervalMinutes: Long,
        runImmediately: Boolean = false,
        task: suspend () -> Unit,
    ): Job {
        require(intervalMinutes > 0) { "Periodic interval must be positive" }
        return schedulePeriodic(name, intervalMinutes.minutes, runImmediately, task)
    }

    /**
     * Schedules a periodic task with a Duration interval.
     */
    fun schedulePeriodic(
        name: String,
        interval: Duration,
        runImmediately: Boolean = false,
        task: suspend () -> Unit,
    ): Job {
        require(interval.isPositive()) { "Periodic interval must be positive" }
        return launchUnique(name) {
            if (runImmediately) runSafely(name, task)
            while (currentCoroutineContext().isActive) {
                delay(interval)
                runSafely(name, task)
            }
        }
    }

    /**
     * Runs a one-shot task and returns its Job.
     */
    fun runOnce(
        name: String,
        task: suspend () -> Unit,
    ): Job {
        return launchUnique(name) { runSafely(name, task) }
    }

    /**
     * Cancels a running task by name.
     */
    fun cancelTask(name: String) {
        runningTasks.remove(name)?.cancel()
    }

    /**
     * Cancels all running tasks.
     */
    fun cancelAll() {
        val tasks = runningTasks.values.toList()
        runningTasks.clear()
        tasks.forEach(Job::cancel)
    }

    /**
     * Returns true if a task with the given name is currently scheduled.
     */
    fun isRunning(name: String): Boolean {
        return runningTasks[name]?.isActive == true
    }

    /** Snapshot of active names for diagnostics/status UI. */
    fun activeTaskNames(): Set<String> = runningTasks
        .filterValues(Job::isActive)
        .keys
        .toSet()

    private fun launchUnique(name: String, block: suspend () -> Unit): Job {
        require(name.isNotBlank()) { "Task name must not be blank" }
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                // An older task must never remove a newer replacement that is
                // registered under the same unique name.
                runningTasks.remove(name, job)
            }
        }
        runningTasks.put(name, job)?.cancel()
        job.start()
        return job
    }

    private suspend fun runSafely(name: String, task: suspend () -> Unit) {
        try {
            task()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logger.warn(error) { "Background task '$name' failed" }
        }
    }
}
