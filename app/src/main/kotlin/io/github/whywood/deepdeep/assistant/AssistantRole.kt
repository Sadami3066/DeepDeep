package io.github.whywood.deepdeep.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal object AssistantRole {
    fun isAvailable(context: Context): Boolean {
        val manager = roleManager(context) ?: return false
        return runCatching { manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)
    }

    fun isHeld(context: Context): Boolean {
        val manager = roleManager(context) ?: return false
        return runCatching { manager.isRoleHeld(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)
    }

    fun requestIntent(context: Context): Intent? {
        val manager = roleManager(context) ?: return null
        if (!isAvailable(context)) return null
        return runCatching {
            manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
        }.getOrNull()
    }

    fun settingsIntent(context: Context): Intent {
        val candidates = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        return candidates.firstOrNull { context.packageManager.resolveActivity(it, 0) != null }
            ?: Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
    }

    private fun roleManager(context: Context): RoleManager? =
        context.getSystemService(RoleManager::class.java)
}
