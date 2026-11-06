package io.github.whywood.deepdeep.core

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

internal object AssistantRole {
    fun isHeld(context: Context): Boolean {
        val manager = roleManager(context) ?: return false
        return runCatching { manager.isRoleHeld(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)
    }

    fun defaultAppsIntent(context: Context): Intent? = listOf(
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    ).firstOrNull { intent -> context.packageManager.resolveActivity(intent, 0) != null }

    private fun roleManager(context: Context): RoleManager? =
        context.getSystemService(RoleManager::class.java)
}
