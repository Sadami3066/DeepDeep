package io.github.whywood.deepdeep.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

class AdbPairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AdbPairingNotifier.ACTION_REPLY) return
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AdbPairingNotifier.EXTRA_CODE)
            ?.toString()
        val pending = goAsync()
        AdbBridgeRuntime.launchPairingCode(context, code).invokeOnCompletion { pending.finish() }
    }
}
