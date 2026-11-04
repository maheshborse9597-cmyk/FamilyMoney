package com.familymoney.app

import android.app.Application
import com.familymoney.app.data.Repository
import com.familymoney.app.work.Notifier
import com.familymoney.app.work.Scheduler

class FamilyMoneyApp : Application() {

    /** Created on first use (opening the encrypted store takes a moment). */
    val repo: Repository by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
    }

    /** Called after pairing and after settings change. */
    fun scheduleBackgroundWork() {
        Scheduler.scheduleAll(this, repo.store.loadSettings())
    }
}
