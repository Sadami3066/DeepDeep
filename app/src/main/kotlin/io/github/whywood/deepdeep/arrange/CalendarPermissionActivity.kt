package io.github.whywood.deepdeep.arrange

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class CalendarPermissionActivity : ComponentActivity() {
    private var requestId: Long = -1L
    private var completed = false

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        completed = true
        CalendarPermissionBridge.complete(requestId, granted)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = CalendarPermissionBridge.requestId(intent)
        val permission = CalendarPermissionBridge.permission(intent)
        if (requestId < 0L || permission == null) {
            finish()
            return
        }
        if (savedInstanceState == null) requestPermission.launch(permission)
    }

    override fun onDestroy() {
        if (!completed) CalendarPermissionBridge.complete(requestId, false)
        super.onDestroy()
    }
}
