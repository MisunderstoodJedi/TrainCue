package com.jongrady.traincue

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity

internal const val EXTRA_RESUME_SESSION = "com.jongrady.traincue.RESUME_SESSION"

internal class TrainingOngoingActivity(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    fun show(day: TrainingDay, session: ActiveSession) {
        ensureChannel()
        val step = day.sessionSteps().getOrNull(session.stepIndex)
        val status = step?.notificationStatus(session).orEmpty().ifBlank { day.subtitle }
        val resumeIntent = Intent(appContext, MainActivity::class.java)
            .putExtra(EXTRA_RESUME_SESSION, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle(day.title)
            .setContentText(status)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        val ongoingActivity = OngoingActivity.Builder(appContext, NOTIFICATION_ID, notification)
            .setStaticIcon(Icon.createWithResource(appContext, R.drawable.ic_workout_notification))
            .setTouchIntent(pendingIntent)
            .build()
        ongoingActivity.apply(appContext)
        notificationManager.notify(NOTIFICATION_ID, notification.build())
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active workouts", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Current TrainCue workout and quick return"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun SessionStep.notificationStatus(session: ActiveSession): String = when (this) {
        is SessionStep.Exercise -> {
            val set = ((session.completedSets[key] ?: 0) + 1).coerceAtMost(workout.sets)
            if (workout.sets > 1) "${workout.name} - set $set of ${workout.sets}" else "${workout.name} - ${workout.reps}"
        }
        is SessionStep.Run -> block.distanceKm?.let { "${block.label} - ${formatDistanceKm(it)}" } ?: block.label
        is SessionStep.Simple -> block.label
    }

    private companion object {
        const val CHANNEL_ID = "active_workout"
        const val NOTIFICATION_ID = 301
    }
}
