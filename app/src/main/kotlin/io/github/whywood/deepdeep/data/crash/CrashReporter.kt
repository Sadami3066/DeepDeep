package io.github.whywood.deepdeep.data.crash

import android.app.Application
import android.os.Build
import android.os.Process
import io.github.whywood.deepdeep.BuildConfig
import java.io.File

object CrashReporter {
    fun install(application: Application) {
        val store = CrashLogStore(File(application.filesDir, DIRECTORY))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val handler = Thread.UncaughtExceptionHandler { thread, error ->
            runCatching {
                store.record(
                    thread = thread,
                    error = error,
                    info = CrashInfo(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE.toLong(),
                        device = "${Build.MANUFACTURER} ${Build.MODEL}",
                        androidSdk = Build.VERSION.SDK_INT,
                    ),
                )
            }
            if (previous != null) previous.uncaughtException(thread, error)
        }
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    fun storeOf(application: Application): CrashLogStore =
        CrashLogStore(File(application.filesDir, DIRECTORY))

    private const val DIRECTORY = "crash"
}
