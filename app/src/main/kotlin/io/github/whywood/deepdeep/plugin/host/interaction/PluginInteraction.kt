package io.github.whywood.deepdeep.plugin.host.interaction

import android.content.Intent
import android.net.Uri
import io.github.whywood.deepdeep.plugin.api.PluginActivityResult
import io.github.whywood.deepdeep.plugin.api.PluginPageContext
import io.github.whywood.deepdeep.plugin.api.PluginPickedFile
import io.github.whywood.deepdeep.plugin.api.UiChoice
import io.github.whywood.deepdeep.plugin.api.UiChooseRequest
import io.github.whywood.deepdeep.plugin.api.UiConfirmRequest
import io.github.whywood.deepdeep.plugin.api.UiDatePickRequest
import io.github.whywood.deepdeep.plugin.api.UiFilePickRequest
import io.github.whywood.deepdeep.plugin.api.UiImagePickRequest
import io.github.whywood.deepdeep.plugin.api.UiPromptRequest
import io.github.whywood.deepdeep.plugin.api.UiTimePickRequest
import kotlinx.serialization.json.JsonObject

internal interface PluginInteraction {
    val foreground: Boolean

    val dark: Boolean

    fun launchMainActivity()

    suspend fun requestPermissions(permissions: Array<String>): Map<String, Boolean>

    suspend fun startActivityForResult(intent: Intent, timeoutMs: Long): PluginActivityResult

    suspend fun confirm(request: UiConfirmRequest): Boolean

    suspend fun prompt(request: UiPromptRequest): String?

    suspend fun choose(request: UiChooseRequest): String?

    suspend fun pickDate(request: UiDatePickRequest): Long?

    suspend fun pickTime(request: UiTimePickRequest): Int?

    suspend fun pickFiles(request: UiFilePickRequest): List<PluginPickedFile>

    suspend fun pickImage(request: UiImagePickRequest): PluginPickedFile?

    suspend fun scanQr(): String?

    suspend fun pickDirectory(): String?

    suspend fun importUri(uri: Uri, fileName: String? = null): PluginPickedFile?

    fun snackbar(message: String, actionLabel: String? = null, action: (() -> Unit)? = null)

    fun openPage(pageId: String, arguments: JsonObject, newTask: Boolean)

    fun closePage(result: JsonObject?)

    fun pageContext(): PluginPageContext?

    fun chooseOption(request: UiChooseRequest): String?
}
