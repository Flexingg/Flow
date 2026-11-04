package com.flow.todo.coach.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.flow.todo.coach.CoachSchedule
import com.flow.todo.coach.CoachSettings
import com.flow.todo.coach.CoachTrigger
import com.flow.todo.coach.data.CoachController
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Background triggers.
 *  - INTERVAL: a periodic job (WorkManager's minimum is 15 min; we offer whole hours).
 *  - RANDOM:   a self-rescheduling one-shot job whose delay is drawn at random, so messages
 *              arrive at unpredictable times a few times per day and never during quiet hours.
 * Foreground triggers (app open / screen open) are handled by [CoachController] directly.
 */
object CoachScheduler {
    private const val INTERVAL_WORK = "flow_coach_interval"
    private const val RANDOM_WORK = "flow_coach_random"
    private const val MANUAL_WORK = "flow_coach_manual"
    internal const val KEY_TRIGGER = "trigger"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Reconciles scheduled work with [settings]. Safe to call repeatedly. */
    fun sync(context: Context, settings: CoachSettings) {
        val wm = WorkManager.getInstance(context.applicationContext)

        if (settings.enabled && CoachTrigger.INTERVAL in settings.triggers) {
            val request = PeriodicWorkRequestBuilder<CoachWorker>(settings.intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(network)
                .setInputData(workDataOf(KEY_TRIGGER to CoachTrigger.INTERVAL.name))
                .build()
            wm.enqueueUniquePeriodicWork(INTERVAL_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            wm.cancelUniqueWork(INTERVAL_WORK)
        }

        if (settings.enabled && CoachTrigger.RANDOM in settings.triggers) {
            // KEEP: re-opening settings must not reshuffle an already scheduled random slot.
            scheduleNextRandom(context, settings, ExistingWorkPolicy.KEEP)
        } else {
            wm.cancelUniqueWork(RANDOM_WORK)
        }
    }

    /**
     * Runs a manual refresh off the main thread / receiver (used by the widget's refresh button:
     * an LLM call can take longer than a broadcast receiver is allowed to run). KEEP means
     * tapping repeatedly doesn't queue duplicates.
     */
    fun enqueueManual(context: Context) {
        val request = OneTimeWorkRequestBuilder<CoachWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(KEY_TRIGGER to CoachTrigger.MANUAL.name))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
    }

    internal fun scheduleNextRandom(
        context: Context,
        settings: CoachSettings,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE
    ) {
        val delay = CoachSchedule.nextRandomDelayMinutes(LocalDateTime.now(), settings)
        val request = OneTimeWorkRequestBuilder<CoachWorker>()
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .setConstraints(network)
            .setInputData(workDataOf(KEY_TRIGGER to CoachTrigger.RANDOM.name))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(RANDOM_WORK, policy, request)
    }
}

class CoachWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val trigger = runCatching {
            CoachTrigger.valueOf(inputData.getString(CoachScheduler.KEY_TRIGGER) ?: "")
        }.getOrDefault(CoachTrigger.INTERVAL)

        val controller = CoachController.get(applicationContext)
        val settings = controller.settings.value

        // The engine applies quiet hours, min gap and the daily cap. Errors are surfaced on the
        // card; we never retry automatically so a bad key or empty balance can't loop.
        if (settings.enabled && settings.isEnabled(trigger)) {
            controller.generateInBackground(trigger)
        }
        if (trigger == CoachTrigger.RANDOM && settings.enabled && settings.isEnabled(CoachTrigger.RANDOM)) {
            CoachScheduler.scheduleNextRandom(applicationContext, controller.settings.value)
        }
        return Result.success()
    }
}
