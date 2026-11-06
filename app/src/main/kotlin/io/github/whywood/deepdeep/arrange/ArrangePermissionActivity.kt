package io.github.whywood.deepdeep.arrange

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat

class ArrangePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = REQUIRED_PERMISSIONS.filter { permission ->
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            finish()
            return
        }
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) finish()
    }

    private companion object {
        const val REQUEST_CODE = 7201
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        )
    }
}
