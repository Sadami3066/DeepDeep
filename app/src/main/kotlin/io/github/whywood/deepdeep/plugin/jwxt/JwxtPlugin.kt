package io.github.whywood.deepdeep.plugin.jwxt

import android.content.Context
import android.system.Os
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.OfficialPlugin
import io.github.whywood.deepdeep.plugin.PluginAction
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginDetailRow
import io.github.whywood.deepdeep.plugin.PluginProgress
import io.github.whywood.deepdeep.plugin.PluginSettingSpec
import io.github.whywood.deepdeep.plugin.PluginSkill
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.PluginUi
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxInstallState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JwxtPlugin(
    private val context: Context,
    private val linux: LinuxEnvironment,
    private val linuxSettings: LinuxSettingsStore,
) : OfficialPlugin {
    override val id: String = "jwxt"
    override val icon: DdIcon = DdIcons.Globe
    override val nameRes: Int = R.string.plugin_jwxt_name
    override val summaryRes: Int = R.string.plugin_jwxt_summary
    override val version: String = JwxtCli.VERSION
    override val sizeLabelRes: Int = R.string.plugin_jwxt_size

    @Volatile private var settingsValues: Map<String, String> = emptyMap()
    @Volatile private var lastVersion: String? = null
    @Volatile private var lastNodeVersion: String? = null
    @Volatile private var lastAccount: String? = null
    @Volatile private var lastLoggedIn: Boolean = false

    override suspend fun availability(): PluginAvailability {
        if (!linux.isSupported) {
            return PluginAvailability.Unavailable(context.getString(R.string.plugin_jwxt_unavailable_device))
        }
        if (linux.state.value !is LinuxInstallState.Installed) {
            return PluginAvailability.Unavailable(context.getString(R.string.plugin_jwxt_unavailable_linux))
        }
        if (!linuxSettings.isEnabled()) {
            return PluginAvailability.Unavailable(context.getString(R.string.plugin_jwxt_unavailable_disabled))
        }
        return PluginAvailability.Available
    }

    override suspend fun install(progress: PluginProgress) {
        requireAvailable()
        progress.report(null, R.string.plugin_jwxt_stage_checking)
        val nodeVersion = probeNodeVersion()
            ?: throw IllegalStateException(context.getString(R.string.plugin_jwxt_error_node_missing))
        val nodeMajor = JwxtCli.parseNodeMajor(nodeVersion)
        if (nodeMajor == null || nodeMajor < JwxtCli.MIN_NODE_MAJOR) {
            throw IllegalStateException(context.getString(R.string.plugin_jwxt_error_node_old, nodeVersion))
        }
        progress.report(null, R.string.plugin_jwxt_stage_installing)
        val target = installDir()
        target.deleteRecursively()
        try {
            withContext(Dispatchers.IO) { copyAssets(target) }
            syncCredentials()
            val result = linux.execute(JwxtCli.installCommand(), INSTALL_TIMEOUT_MS)
            val installedVersion = JwxtCli.parseVersion(result.output)
            if (result.exitCode != 0 || installedVersion != JwxtCli.VERSION) {
                throw IllegalStateException(
                    context.getString(R.string.plugin_jwxt_error_install, result.output.takeLast(400).trim()),
                )
            }
            lastVersion = installedVersion
            lastNodeVersion = nodeVersion
        } catch (error: Exception) {
            lastVersion = null
            lastNodeVersion = null
            runCatching { target.deleteRecursively() }
            runCatching { File(linux.rootfsDir, JwxtCli.BIN_RELATIVE).delete() }
            throw error
        }
    }

    override suspend fun uninstall() {
        runCatching { File(linux.rootfsDir, JwxtCli.BIN_RELATIVE).delete() }
        runCatching { installDir().deleteRecursively() }
        runCatching { credentialsDir().deleteRecursively() }
        lastVersion = null
        lastNodeVersion = null
        lastAccount = null
        lastLoggedIn = false
    }

    override suspend fun status(): PluginStatus {
        val availability = availability()
        if (availability is PluginAvailability.Unavailable) {
            return PluginStatus.Failed(availability.reason)
        }
        if (!File(linux.rootfsDir, JwxtCli.ENTRY_RELATIVE).isFile) {
            lastVersion = null
            lastLoggedIn = false
            return PluginStatus.Failed(context.getString(R.string.plugin_jwxt_status_needs_reinstall))
        }
        val nodeVersion = probeNodeVersion()
        if (nodeVersion == null) {
            lastVersion = null
            lastNodeVersion = null
            lastLoggedIn = false
            return PluginStatus.Failed(context.getString(R.string.plugin_jwxt_status_no_node))
        }
        lastNodeVersion = nodeVersion
        val nodeMajor = JwxtCli.parseNodeMajor(nodeVersion)
        if (nodeMajor == null || nodeMajor < JwxtCli.MIN_NODE_MAJOR) {
            lastVersion = null
            lastLoggedIn = false
            return PluginStatus.Failed(context.getString(R.string.plugin_jwxt_status_node_old, nodeVersion))
        }
        val versionResult = linux.execute(JwxtCli.versionCommand(), VERSION_TIMEOUT_MS)
        val installedVersion = JwxtCli.parseVersion(versionResult.output)
        if (versionResult.exitCode != 0 || installedVersion == null) {
            lastVersion = null
            return PluginStatus.Failed(context.getString(R.string.plugin_jwxt_status_needs_reinstall))
        }
        lastVersion = installedVersion
        val credentials = readRootfsText(JwxtCli.CREDENTIALS_RELATIVE)
        val account = JwxtCli.netId(credentials) ?: settingsNetId()
        lastAccount = account
        lastLoggedIn = JwxtCli.isLoggedIn(readRootfsText(JwxtCli.SESSION_RELATIVE))
        return if (lastLoggedIn) {
            PluginStatus.Ready(context.getString(R.string.plugin_jwxt_status_logged_in), account)
        } else {
            PluginStatus.NotReady(context.getString(R.string.plugin_jwxt_status_not_logged_in))
        }
    }

    override suspend fun setup(ui: PluginUi) {
        requireAvailable()
        if (status() is PluginStatus.Ready) return
        throw IllegalStateException(
            context.getString(
                if (settingsNetId() == null || settingsPassword().isEmpty()) {
                    R.string.plugin_jwxt_setup_no_credentials
                } else {
                    R.string.plugin_jwxt_setup_needs_login
                },
            ),
        )
    }

    override fun actions(status: PluginStatus): List<PluginAction> = when (status) {
        is PluginStatus.Ready -> listOf(PluginAction(ACTION_RELOGIN, R.string.plugin_jwxt_action_relogin))
        else -> emptyList()
    }

    override suspend fun runAction(actionId: String, ui: PluginUi) {
        if (actionId != ACTION_RELOGIN) return
        ui.progress(null, R.string.plugin_jwxt_stage_relogin)
        runCatching { File(linux.rootfsDir, JwxtCli.SESSION_RELATIVE).delete() }
        lastLoggedIn = false
    }

    override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = buildList {
        lastVersion?.let { add(PluginDetailRow(R.string.plugin_jwxt_detail_cli_version, it)) }
        lastNodeVersion?.let { add(PluginDetailRow(R.string.plugin_jwxt_detail_node, it)) }
        if (status !is PluginStatus.Failed) {
            add(
                PluginDetailRow(
                    R.string.plugin_jwxt_detail_account,
                    lastAccount ?: context.getString(R.string.plugin_jwxt_account_missing),
                ),
            )
            add(
                PluginDetailRow(
                    R.string.plugin_jwxt_detail_login,
                    context.getString(if (lastLoggedIn) R.string.plugin_jwxt_login_on else R.string.plugin_jwxt_login_off),
                ),
            )
        }
    }

    override fun skills(): List<PluginSkill> = listOf(PluginSkill(JwxtCli.SKILL_ID, JwxtContent.skillMarkdown()))

    override fun promptBody(status: PluginStatus): String =
        JwxtContent.promptBody(status is PluginStatus.Ready, lastAccount, status.label)

    override fun settings(): List<PluginSettingSpec> = listOf(
        PluginSettingSpec.Text(KEY_NETID, R.string.plugin_jwxt_setting_netid),
        PluginSettingSpec.Text(KEY_PASSWORD, R.string.plugin_jwxt_setting_password, secret = true),
    )

    override suspend fun onSettingsChanged(values: Map<String, String>) {
        val previousNetId = settingsNetId()
        settingsValues = values
        if (linux.state.value !is LinuxInstallState.Installed) return
        if (!File(linux.rootfsDir, JwxtCli.ENTRY_RELATIVE).isFile) return
        syncCredentials()
        val netId = settingsNetId()
        if (previousNetId != null && netId != null && previousNetId != netId) {
            withContext(Dispatchers.IO) {
                runCatching { File(linux.rootfsDir, JwxtCli.SESSION_RELATIVE).delete() }
            }
        }
    }

    private suspend fun syncCredentials() {
        val netid = settingsNetId()
        val password = settingsPassword()
        withContext(Dispatchers.IO) {
            val dir = credentialsDir()
            val file = File(dir, CREDENTIALS_NAME)
            if (netid == null || password.isEmpty()) {
                runCatching { file.delete() }
                runCatching { File(linux.rootfsDir, JwxtCli.SESSION_RELATIVE).delete() }
                return@withContext
            }
            dir.mkdirs()
            file.writeText(JwxtCli.credentialsContent(netid, password))
            runCatching { Os.chmod(dir.absolutePath, CONFIG_DIR_MODE) }
            runCatching { Os.chmod(file.absolutePath, CREDENTIALS_MODE) }
        }
    }

    private suspend fun probeNodeVersion(): String? {
        val result = runCatching { linux.execute(JwxtCli.nodeVersionCommand(), NODE_TIMEOUT_MS) }.getOrNull()
        if (result == null || result.exitCode != 0) return null
        return JwxtCli.parseNodeVersion(result.output)
    }

    private fun readRootfsText(relative: String): String? = runCatching {
        File(linux.rootfsDir, relative).takeIf { it.isFile }?.readText()
    }.getOrNull()

    private fun copyAssets(target: File) {
        JwxtCli.ASSET_FILES.forEach { relative ->
            val file = File(target, relative)
            file.parentFile?.mkdirs()
            context.assets.open("${JwxtCli.ASSET_ROOT}/$relative").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun installDir(): File = File(linux.rootfsDir, JwxtCli.INSTALL_DIR_RELATIVE)

    private fun credentialsDir(): File = File(linux.rootfsDir, JwxtCli.CONFIG_DIR_RELATIVE)

    private fun settingsNetId(): String? = settingsValues[KEY_NETID]?.trim()?.takeIf { it.isNotEmpty() }

    private fun settingsPassword(): String = settingsValues[KEY_PASSWORD].orEmpty()

    private suspend fun requireAvailable() {
        val availability = availability()
        if (availability is PluginAvailability.Unavailable) {
            throw IllegalStateException(availability.reason)
        }
    }

    private companion object {
        const val KEY_NETID = "netid"
        const val KEY_PASSWORD = "password"
        const val CREDENTIALS_NAME = "credentials"
        const val ACTION_RELOGIN = "relogin"

        const val CONFIG_DIR_MODE = 0x1C0
        const val CREDENTIALS_MODE = 0x180

        const val NODE_TIMEOUT_MS = 20_000L
        const val VERSION_TIMEOUT_MS = 20_000L
        const val INSTALL_TIMEOUT_MS = 120_000L
    }
}
