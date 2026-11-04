package com.familymoney.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.familymoney.app.FamilyMoneyApp
import com.familymoney.app.MainActivity
import com.familymoney.app.R
import com.familymoney.app.data.Repository
import com.familymoney.app.logic.Calc
import com.familymoney.app.logic.Dates
import com.familymoney.app.logic.Fmt

/** Home-screen widget: total balance, bills needing attention, and a one-tap "Add expense". */
class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repo = (context.applicationContext as FamilyMoneyApp).repo
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, build(context, repo))
        }
    }

    companion object {

        fun updateAll(context: Context, repo: Repository) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetProvider::class.java))
            for (id in ids) {
                manager.updateAppWidget(id, build(context, repo))
            }
        }

        private fun build(context: Context, repo: Repository): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            val snapshot = repo.snapshot.value
            val settings = repo.store.loadSettings()

            if (!repo.isPaired() || snapshot == null) {
                views.setTextViewText(R.id.w_balance, "Family Money")
                views.setTextViewText(R.id.w_sub, "Open the app to set up")
            } else {
                val effective = Calc.applyPending(snapshot, repo.outbox.value)
                val total = effective.banks.sumOf { it.balance }
                val urgent = Calc.urgentBills(effective.bills, Dates.today()).size
                views.setTextViewText(
                    R.id.w_balance,
                    if (settings.widgetHideAmounts) "₹ ••••••" else Fmt.money(total)
                )
                views.setTextViewText(
                    R.id.w_sub,
                    when (urgent) {
                        0 -> "No bills need attention"
                        1 -> "1 bill needs attention"
                        else -> "$urgent bills need attention"
                    }
                )
            }

            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(context, 10, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )

            val add = Intent(Intent.ACTION_VIEW, Uri.parse("familymoney://add?type=Expense"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.w_add,
                PendingIntent.getActivity(context, 11, add, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            return views
        }
    }
}
