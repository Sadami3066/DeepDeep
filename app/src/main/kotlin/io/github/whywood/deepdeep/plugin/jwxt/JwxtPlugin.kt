package io.github.whywood.deepdeep.plugin.jwxt

import android.content.Context
import android.system.Os
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAction
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginDetailRow
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginProgress
import io.github.whywood.deepdeep.plugin.api.PluginSetupUi
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.PromptSection
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxInstallState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JwxtPlugin(
    private val context: Context,
    private val linux: LinuxEnvironment,
    private val linuxSettings: LinuxSettingsStore,
) : DeepDeepPlugin {
    @Volatile private var settingsValues: Map<String, String> = emptyMap()
    @Volatile private var lastVersion: String? = null
    @Volatile private var lastNodeVersion: String? = null
    @Volatile private var lastAccount: String? = null
    @Volatile private var lastLoggedIn: Boolean = false
    @Volatile private var lastStatus: PluginStatus? = null

    override suspend fun availability(host: PluginHost): PluginAvailability {
        if (!linux.isSupported) {
            return PluginAvailability.Unavailable(
                host.text(LocalizedText.Resource(R.string.plugin_jwxt_unavailable_device)),
            )
        }
        if (linux.state.value !is LinuxInstallState.Installed) {
            return PluginAvailability.Unavailable(
                host.text(LocalizedText.Resource(R.string.plugin_jwxt_unavailable_linux)),
            )
        }
        if (!linuxSettings.isEnabled()) {
            return PluginAvailability.Unavailable(
                host.text(LocalizedText.Resource(R.string.plugin_jwxt_unavailable_disabled)),
            )
        }
        return PluginAvailability.Available
    }

    override suspend fun install(host: PluginHost, progress: PluginProgress) {
        requireAvailable(host)
        progress.report(null, LocalizedText.Resource(R.string.plugin_jwxt_stage_checking))
        val nodeVersion = probeNodeVersion()
            ?: throw IllegalStateException(host.text(LocalizedText.Resource(R.string.plugin_jwxt_error_node_missing)))
        val nodeMajor = JwxtCli.parseNodeMajor(nodeVersion)
        if (nodeMajor == null || nodeMajor < JwxtCli.MIN_NODE_MAJOR) {
            throw IllegalStateException(
                host.text(LocalizedText.Resource(R.string.plugin_jwxt_error_node_old), nodeVersion),
            )
        }
        progress.report(null, LocalizedText.Resource(R.string.plugin_jwxt_stage_installing))
        val target = installDir()
        target.deleteRecursively()
        try {
            withContext(Dispatchers.IO) { copyAssets(target) }
            syncCredentials()
            val result = linux.execute(JwxtCli.installCommand(), INSTALL_TIMEOUT_MS)
            val installedVersion = JwxtCli.parseVersion(result.output)
            if (result.exitCode != 0 || installedVersion != JwxtCli.VERSION) {
                throw IllegalStateException(
                    host.text(
                        LocalizedText.Resource(R.string.plugin_jwxt_error_install),
                        result.output.takeLast(400).trim(),
                    ),
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

    override suspend fun uninstall(host: PluginHost) {
        runCatching { File(linux.rootfsDir, JwxtCli.BIN_RELATIVE).delete() }
        runCatching { installDir().deleteRecursively() }
        runCatching { credentialsDir().deleteRecursively() }
        lastVersion = null
        lastNodeVersion = null
        lastAccount = null
        lastLoggedIn = false
        lastStatus = null
    }

    override suspend fun status(host: PluginHost): PluginStatus = cache(
        when (val availability = availability(host)) {
            is PluginAvailability.Unavailable -> PluginStatus.Failed(availability.reason)
            PluginAvailability.Available -> readStatus(host)
        },
    )

    private suspend fun readStatus(host: PluginHost): PluginStatus {
        if (!File(linux.rootfsDir, JwxtCli.INSTALLED_ENTRY_RELATIVE).isFile) {
            lastVersion = null
            lastLoggedIn = false
            return PluginStatus.Failed(host.text(LocalizedText.Resource(R.string.plugin_jwxt_status_needs_reinstall)))
        }
        val nodeVersion = probeNodeVersion()
        if (nodeVersion == null) {
            lastVersion = null
            lastNodeVersion = null
            lastLoggedIn = false
            return PluginStatus.Failed(host.text(LocalizedText.Resource(R.string.plugin_jwxt_status_no_node)))
        }
        lastNodeVersion = nodeVersion
        val nodeMajor = JwxtCli.parseNodeMajor(nodeVersion)
        if (nodeMajor == null || nodeMajor < JwxtCli.MIN_NODE_MAJOR) {
            lastVersion = null
            lastLoggedIn = false
            return PluginStatus.Failed(
                host.text(LocalizedText.Resource(R.string.plugin_jwxt_status_node_old), nodeVersion),
            )
        }
        val versionResult = linux.execute(JwxtCli.versionCommand(), VERSION_TIMEOUT_MS)
        val installedVersion = JwxtCli.parseVersion(versionResult.output)
        if (versionResult.exitCode != 0 || installedVersion == null) {
            lastVersion = null
            return PluginStatus.Failed(host.text(LocalizedText.Resource(R.string.plugin_jwxt_status_needs_reinstall)))
        }
        lastVersion = installedVersion
        val credentials = readRootfsText(JwxtCli.CREDENTIALS_RELATIVE)
        val account = JwxtCli.netId(credentials) ?: settingsNetId()
        lastAccount = account
        lastLoggedIn = JwxtCli.isLoggedIn(readRootfsText(JwxtCli.SESSION_RELATIVE))
        return if (lastLoggedIn) {
            PluginStatus.Ready(host.text(LocalizedText.Resource(R.string.plugin_jwxt_status_logged_in)), account)
        } else {
            PluginStatus.NotReady(host.text(LocalizedText.Resource(R.string.plugin_jwxt_status_not_logged_in)))
        }
    }

    override suspend fun setup(host: PluginHost, ui: PluginSetupUi) {
        requireAvailable(host)
        if (status(host) is PluginStatus.Ready) return
        throw IllegalStateException(
            host.text(
                LocalizedText.Resource(
                    if (settingsNetId() == null || settingsPassword().isEmpty()) {
                        R.string.plugin_jwxt_setup_no_credentials
                    } else {
                        R.string.plugin_jwxt_setup_needs_login
                    },
                ),
            ),
        )
    }

    override fun actions(host: PluginHost, status: PluginStatus): List<PluginAction> = when (status) {
        is PluginStatus.Ready -> listOf(
            PluginAction(ACTION_RELOGIN, LocalizedText.Resource(R.string.plugin_jwxt_action_relogin)),
        )

        else -> emptyList()
    }

    override suspend fun runAction(host: PluginHost, actionId: String, ui: PluginSetupUi) {
        if (actionId != ACTION_RELOGIN) return
        ui.progress(null, LocalizedText.Resource(R.string.plugin_jwxt_stage_relogin))
        runCatching { File(linux.rootfsDir, JwxtCli.SESSION_RELATIVE).delete() }
        lastLoggedIn = false
    }

    override fun detailRows(host: PluginHost, status: PluginStatus): List<PluginDetailRow> = buildList {
        lastVersion?.let { add(PluginDetailRow(LocalizedText.Resource(R.string.plugin_jwxt_detail_cli_version), it)) }
        lastNodeVersion?.let { add(PluginDetailRow(LocalizedText.Resource(R.string.plugin_jwxt_detail_node), it)) }
        if (status !is PluginStatus.Failed) {
            add(
                PluginDetailRow(
                    LocalizedText.Resource(R.string.plugin_jwxt_detail_account),
                    lastAccount ?: host.text(LocalizedText.Resource(R.string.plugin_jwxt_account_missing)),
                ),
            )
            add(
                PluginDetailRow(
                    LocalizedText.Resource(R.string.plugin_jwxt_detail_login),
                    host.text(
                        LocalizedText.Resource(
                            if (lastLoggedIn) R.string.plugin_jwxt_login_on else R.string.plugin_jwxt_login_off,
                        ),
                    ),
                ),
            )
        }
    }

    override fun skills(host: PluginHost): List<PluginSkill> =
        listOf(PluginSkill(JwxtCli.SKILL_ID, JwxtContent.skillMarkdown()))

    override fun promptSections(host: PluginHost): List<PromptSection> {
        val status = lastStatus ?: PluginStatus.NotReady(
            host.text(LocalizedText.Resource(R.string.plugin_jwxt_status_not_logged_in)),
        )
        return listOf(PromptSection(SECTION_ID, JwxtContent.promptBody(status is PluginStatus.Ready, lastAccount, status.label)))
    }

    override fun settings(): List<SettingSpec> = listOf(
        SettingSpec.Text(KEY_NETID, LocalizedText.Resource(R.string.plugin_jwxt_setting_netid)),
        SettingSpec.Text(KEY_PASSWORD, LocalizedText.Resource(R.string.plugin_jwxt_setting_password), secret = true),
    )

    override suspend fun onSettingsChanged(host: PluginHost, values: Map<String, String>) {
        val previousNetId = settingsNetId()
        settingsValues = values
        if (linux.state.value !is LinuxInstallState.Installed) return
        if (!File(linux.rootfsDir, JwxtCli.INSTALLED_ENTRY_RELATIVE).isFile) return
        syncCredentials()
        val netId = settingsNetId()
        if (previousNetId != null && netId != null && previousNetId != netId) {
            withContext(Dispatchers.IO) {
                runCatching { File(linux.rootfsDir, JwxtCli.SESSION_RELATIVE).delete() }
            }
        }
    }

    private fun cache(status: PluginStatus): PluginStatus {
        lastStatus = status
        return status
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

    private suspend fun requireAvailable(host: PluginHost) {
        val availability = availability(host)
        if (availability is PluginAvailability.Unavailable) {
            throw IllegalStateException(availability.reason)
        }
    }

    private companion object {
        const val KEY_NETID = JWXT_SETTING_NETID
        const val KEY_PASSWORD = JWXT_SETTING_PASSWORD
        const val CREDENTIALS_NAME = "credentials"
        const val ACTION_RELOGIN = "relogin"
        const val SECTION_ID = "jwxt_prompt"

        const val CONFIG_DIR_MODE = 0x1C0
        const val CREDENTIALS_MODE = 0x180

        const val NODE_TIMEOUT_MS = 20_000L
        const val VERSION_TIMEOUT_MS = 20_000L
        const val INSTALL_TIMEOUT_MS = 120_000L
    }
}

internal const val JWXT_SETTING_NETID = "netid"
internal const val JWXT_SETTING_PASSWORD = "password"
