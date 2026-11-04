package com.familymoney.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.familymoney.app.FamilyMoneyApp
import com.familymoney.app.MainActivity
import com.familymoney.app.R
import com.familymoney.app.data.AppSettings
import com.familymoney.app.logic.Calc
import com.familymoney.app.logic.Dates
import com.familymoney.app.widget.BalanceWidgetProvider
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Notifier {
    const val CHANNEL_ID = "bills"
    private const val NOTIFICATION_ID = 4101

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Bills & budget reminders", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Overdue bills, card due dates and budgets close to the limit"
        nm.createNotificationChannel(channel)
    }

    fun show(context: Context, lines: List<String>) {
        if (lines.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN, "bills")
        }
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val style = NotificationCompat.InboxStyle()
        for (line in lines.take(6)) style.addLine(line)
        if (lines.size > 6) style.setSummaryText("+${lines.size - 6} more")

        val title = if (lines.size == 1) "1 reminder" else "${lines.size} reminders"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Family Money · $title")
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // notification permission was revoked in the meantime
        }
    }
}

/** Keeps the offline copy fresh and sends queued changes, even when the app is closed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FamilyMoneyApp
        val repo = app.repo
        if (!repo.isPaired()) return Result.success()
        val outcome = repo.refresh(false)
        BalanceWidgetProvider.updateAll(applicationContext, repo)
        return if (outcome.isSuccess) Result.success() else Result.retry()
    }
}

/** Runs once a day at the chosen hour and works offline, from the saved copy of the data. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FamilyMoneyApp
        val repo = app.repo
        val settings = repo.store.loadSettings()
        if (repo.isPaired() && settings.notifications) {
            val snap = repo.snapshot.value
            if (snap != null) {
                val pct = if (snap.settings.budgetAlertPercent in 50..100) snap.settings.budgetAlertPercent else 80
                Notifier.show(applicationContext, Calc.reminderLines(snap, Dates.today(), pct))
            }
        }
        Scheduler.scheduleReminder(applicationContext, settings, fromWorker = true)       // plan tomorrow's reminder
        return Result.success()
    }
}

object Scheduler {
    private const val SYNC = "fm_sync"
    private const val REMINDER = "fm_reminder"

    fun scheduleAll(context: Context, settings: AppSettings) {
        schedulePeriodicSync(context)
        scheduleReminder(context, settings)
    }

    private fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleReminder(context: Context, settings: AppSettings, fromWorker: Boolean = false) {
        val wm = WorkManager.getInstance(context)
        if (!settings.notifications) {
            wm.cancelUniqueWork(REMINDER)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(settings.notifyHour.coerceIn(0, 23), 0)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        // From the worker itself we must not cancel the running job, so the next one is appended;
        // when the person changes the time we replace whatever is waiting.
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        wm.enqueueUniqueWork(REMINDER, policy, request)
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(SYNC)
        wm.cancelUniqueWork(REMINDER)
    }
}
