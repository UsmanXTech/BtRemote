package com.atharok.btremote

import android.app.Application
import com.atharok.btremote.common.injections.appModules
import com.atharok.btremote.common.utils.DiagnosticLogger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BtRemoteApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.initialize(this)
        DiagnosticLogger.info("app.process_started", "version=1.13.0")
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DiagnosticLogger.recordFatal(throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        startKoin {
            androidContext(this@BtRemoteApplication)
            modules(appModules)
        }
    }
}