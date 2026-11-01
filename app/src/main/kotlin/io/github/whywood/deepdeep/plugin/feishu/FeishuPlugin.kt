package io.github.whywood.deepdeep.plugin.feishu

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.plugin.OfficialPlugin
import io.github.whywood.deepdeep.plugin.PluginAction
import io.github.whywood.deepdeep.plugin.PluginActionStyle
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginDetailRow
import io.github.whywood.deepdeep.plugin.PluginDownloader
import io.github.whywood.deepdeep.plugin.PluginProgress
import io.github.whywood.deepdeep.plugin.PluginSkill
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.PluginUi
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxInstallState
import java.io.File
import kotlinx.coroutines.CancellationException

class FeishuPlugin(
    private val context: Context,
    private val linux: LinuxEnvironment,
    private val linuxSettings: LinuxSettingsStore,
    private val downloader: PluginDownloader,
    private val cacheDir: File,
) : OfficialPlugin {
    override val id: String = "feishu"
    override val icon: DdIcon = DdIcon(R.drawable.ic_plugin_feishu)
    override val tintIcon: Boolean = false
    override val nameRes: Int = R.string.plugin_feishu_name
    override val summaryRes: Int = R.string.plugin_feishu_summary
    override val version: String = VERSION
    override val sizeLabelRes: Int = R.string.plugin_feishu_size

    @Volatile private var lastVersion: String? = null
    @Volatile private var lastScopeCount: Int = 0
    @Volatile private var lastAppConfigured: Boolean = false
    @Volatile private var lastAppId: String? = null
    @Volatile private var lastMissingScopes: List<String> = emptyList()

    override suspend fun availability(): PluginAvailability {
        if (!linux.isSupported) {
            return PluginAvailability.Unavailable(context.getString(R.string.plugin_feishu_unavailable_device))
        }
        if (linux.state.value !is LinuxInstallState.Installed) {
            return PluginAvailability.Unavailable(context.getString(R.string.plugin_feishu_unavailable_linux))
        }
        if (!linuxSettings.isEnabled()) {
            return PluginAvailability.Unavailable(context.getString(R.string.plugin_feishu_unavailable_disabled))
        }
        return PluginAvailability.Available
    }

    override suspend fun install(progress: PluginProgress) {
        requireAvailable()
        val hadBinary = hasExistingBinary()
        val staging = File(cacheDir, "plugin-staging/$id")
        staging.deleteRecursively()
        staging.mkdirs()
        val archive = File(staging, ARCHIVE_NAME)
        try {
            downloader.download(DOWNLOAD_URLS, ARCHIVE_SHA256, archive) { percent ->
                progress.report(percent, R.string.plugin_feishu_stage_downloading)
            }
            progress.report(null, R.string.plugin_feishu_stage_verifying)
            val runner = LinuxEnvironment(context, workspaceDir = staging)
            val extract = runner.executeStreaming(
                command = """
                    set -e
                    rm -rf extract
                    mkdir -p extract
                    tar -xzf $ARCHIVE_NAME -C extract
                    chmod 755 extract/lark-cli
                    ./extract/lark-cli --version
                """.trimIndent(),
                timeoutMillis = EXTRACT_TIMEOUT_MS,
            ) { }
            val stagedVersion = FeishuCli.parseVersion(extract.output)
            if (extract.exitCode != 0 || stagedVersion != VERSION) {
                throw IllegalStateException(
                    context.getString(R.string.plugin_feishu_error_version, extract.output.takeLast(400).trim()),
                )
            }
            progress.report(null, R.string.plugin_feishu_stage_installing)
            val install = runner.executeStreaming(
                command = """
                    set -e
                    mkdir -p /usr/local/bin
                    if [ -f ${FeishuCli.INSTALL_PATH} ]; then cp -f ${FeishuCli.INSTALL_PATH} $LARK_CLI_BACKUP_PATH; fi
                    cp -f extract/lark-cli $LARK_CLI_TEMP_PATH
                    chmod 755 $LARK_CLI_TEMP_PATH
                    mv -f $LARK_CLI_TEMP_PATH ${FeishuCli.INSTALL_PATH}
                    ${FeishuCli.INSTALL_PATH} --version
                    rm -f $LARK_CLI_BACKUP_PATH
                """.trimIndent(),
                timeoutMillis = INSTALL_TIMEOUT_MS,
            ) { }
            val installedVersion = FeishuCli.parseVersion(install.output)
            if (install.exitCode != 0 || installedVersion != VERSION) {
                throw IllegalStateException(
                    context.getString(R.string.plugin_feishu_error_install, install.output.takeLast(400).trim()),
                )
            }
            lastVersion = installedVersion
        } catch (error: Exception) {
            runCatching { restoreOrRemoveBinary(hadBinary) }
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    override suspend fun uninstall() {
        linux.execute(FeishuCli.uninstallCommand(), UNINSTALL_TIMEOUT_MS)
        lastVersion = null
        lastScopeCount = 0
        lastAppConfigured = false
        lastAppId = null
        lastMissingScopes = emptyList()
    }

    override suspend fun recover() {
        runCatching { File(cacheDir, "plugin-staging/$id").deleteRecursively() }
    }

    override suspend fun status(): PluginStatus {
        val availability = availability()
        if (availability is PluginAvailability.Unavailable) {
            return PluginStatus.Failed(availability.reason)
        }
        val versionResult = linux.execute(FeishuCli.versionCommand(), VERSION_TIMEOUT_MS)
        val installedVersion = FeishuCli.parseVersion(versionResult.output)
        if (versionResult.exitCode != 0 || installedVersion == null) {
            return PluginStatus.Failed(context.getString(R.string.plugin_feishu_status_broken))
        }
        lastVersion = installedVersion
        val statusResult = linux.execute(FeishuCli.statusCommand(), STATUS_TIMEOUT_MS)
        val parsed = FeishuCli.parseAuthStatus(statusResult.output)
            ?: return PluginStatus.Failed(
                context.getString(
                    R.string.plugin_feishu_status_error,
                    statusResult.output.takeLast(200).trim(),
                ),
            )
        lastAppConfigured = parsed.appConfigured
        lastAppId = parsed.appId
        lastScopeCount = parsed.scopeCount
        return when {
            parsed.appConfigured && parsed.userReady -> {
                val account = parsed.account.orEmpty()
                val missingCount = lastMissingScopes.size
                val label = if (missingCount == 0) {
                    context.getString(R.string.plugin_feishu_status_connected, account)
                } else {
                    context.getString(R.string.plugin_feishu_status_partial, account, missingCount)
                }
                PluginStatus.Ready(label, account)
            }

            else -> PluginStatus.NotReady(context.getString(R.string.plugin_feishu_status_not_connected))
        }
    }

    override suspend fun setup(ui: PluginUi) {
        requireAvailable()
        val before = status()
        if (before is PluginStatus.Ready) return
        login(ui)
    }

    private suspend fun login(ui: PluginUi) {
        val probe = readAuthStatus()
        lastAppId = probe?.appId ?: lastAppId
        if (probe?.appConfigured != true) {
            ui.stage(R.string.plugin_feishu_stage_create_app)
            val url = runConfigInit(ui)
            if (url == null) {
                throw IllegalStateException(context.getString(R.string.plugin_feishu_error_auth))
            }
        }
        ui.stage(R.string.plugin_feishu_stage_auth, messageRes = R.string.plugin_feishu_hint_auth)
        val start = linux.execute(FeishuCli.loginStartCommand(), LOGIN_START_TIMEOUT_MS)
        if (start.exitCode != 0) {
            throw IllegalStateException(context.getString(R.string.plugin_feishu_error_auth_output, start.output.takeLast(300).trim()))
        }
        val challenge = FeishuCli.parseLoginChallenge(start.output)
            ?: throw IllegalStateException(context.getString(R.string.plugin_feishu_error_auth_output, start.output.takeLast(300).trim()))
        ui.stage(
            R.string.plugin_feishu_stage_auth,
            messageRes = R.string.plugin_feishu_hint_auth,
            url = challenge.verificationUrl,
        )
        if (ui.cancelled()) throw CancellationException("cancelled")
        val poll = linux.execute(FeishuCli.loginPollCommand(challenge.deviceCode), LOGIN_POLL_TIMEOUT_MS)
        val result = FeishuCli.parseLoginResult(poll.output)
        if (poll.exitCode != 0 && result?.account == null) {
            val current = readAuthStatus()
            if (current?.userReady != true) {
                throw IllegalStateException(
                    context.getString(R.string.plugin_feishu_error_auth_output, poll.output.takeLast(300).trim()),
                )
            }
            lastMissingScopes = emptyList()
        } else {
            lastMissingScopes = result?.missingScopes.orEmpty()
        }
        if (ui.cancelled()) throw CancellationException("cancelled")
        ui.progress(null, R.string.plugin_feishu_stage_finishing)
    }

    override fun actions(status: PluginStatus): List<PluginAction> = buildList {
        if (status is PluginStatus.Ready) {
            if (!lastAppId.isNullOrBlank()) {
                add(PluginAction(ACTION_APP_SCOPES, R.string.plugin_feishu_action_app_scopes))
            }
            add(PluginAction(ACTION_REAUTHORIZE, R.string.plugin_feishu_action_reauthorize))
            add(
                PluginAction(
                    ACTION_LOGOUT,
                    R.string.plugin_feishu_action_logout,
                    PluginActionStyle.DESTRUCTIVE,
                ),
            )
        } else {
            add(PluginAction(ACTION_CONNECT, R.string.plugin_feishu_action_connect))
        }
    }

    override suspend fun runAction(actionId: String, ui: PluginUi) {
        when (actionId) {
            ACTION_CONNECT -> setup(ui)

            ACTION_APP_SCOPES -> openAppScopes()

            ACTION_REAUTHORIZE -> {
                requireAvailable()
                login(ui)
            }

            ACTION_LOGOUT -> {
                ui.progress(null, R.string.plugin_feishu_stage_signing_out)
                val result = linux.execute(FeishuCli.logoutCommand(), LOGOUT_TIMEOUT_MS)
                if (result.exitCode != 0) {
                    throw IllegalStateException(
                        context.getString(R.string.plugin_feishu_error_logout, result.output.takeLast(300).trim()),
                    )
                }
                lastScopeCount = 0
                lastMissingScopes = emptyList()
            }
        }
    }

    override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = buildList {
        lastVersion?.let { add(PluginDetailRow(R.string.plugin_feishu_detail_cli_version, it)) }
        if (lastScopeCount > 0) {
            add(
                PluginDetailRow(
                    R.string.plugin_feishu_detail_scopes,
                    context.getString(R.string.plugin_feishu_scope_count, lastScopeCount),
                ),
            )
        }
        if (lastMissingScopes.isNotEmpty()) {
            add(
                PluginDetailRow(
                    R.string.plugin_feishu_detail_missing_scopes,
                    missingScopesLabel(lastMissingScopes),
                ),
            )
        }
        if (status !is PluginStatus.Failed) {
            add(
                PluginDetailRow(
                    R.string.plugin_feishu_detail_app,
                    context.getString(
                        if (lastAppConfigured) {
                            R.string.plugin_feishu_detail_app_ready
                        } else {
                            R.string.plugin_feishu_detail_app_missing
                        },
                    ),
                ),
            )
        }
    }

    override fun skills(): List<PluginSkill> = listOf(PluginSkill(SKILL_ID, SKILL_MARKDOWN.replace("<download-directory>", File(linux.workspaceDir, "tmp").absolutePath)))

    override fun promptBody(status: PluginStatus): String = when (status) {
        is PluginStatus.Ready -> buildString {
            appendLine("飞书插件已安装并已连接为 ${status.account ?: "当前账号"}。")
            appendLine("在 bash 中通过 `lark-cli` 访问飞书；个人数据必须加 `--as user`。")
            appendLine("已授权范围内直接执行；超出授权的发送、修改、删除等操作先询问，支持时可用 `--dry-run` 预览。")
            appendLine("不熟悉命令时先运行 `lark-cli skills list` 或 `lark-cli skills read <domain>` 查看用法。")
            if (lastMissingScopes.isNotEmpty()) {
                appendLine("当前授权缺少 ${lastMissingScopes.size} 项权限：${lastMissingScopes.joinToString(" ")}。")
            }
            appendLine(
                "电子表格/多维表格没有直读权限时不要反复重新授权：先用 `mkdir -p ${File(linux.workspaceDir, "tmp").absolutePath} && lark-cli drive +export " +
                    "--url <链接> --file-extension xlsx --output-dir ${File(linux.workspaceDir, "tmp").absolutePath}`（电子表格）或 " +
                    "`--file-extension base`（多维表格）导出到本地再解析。" +
                    "需要直读时请用户在插件详情页点「应用权限」，到飞书开放平台为应用开通对应权限。",
            )
            append("不要自行创建定时任务或总结流程，除非用户明确要求。")
        }

        else -> buildString {
            appendLine("飞书插件已安装，但当前不可用或未连接：${status.label}")
            appendLine("需要连接或重新授权时，调用 `plugin_setup` 工具（plugin=\"feishu\"），不要自己执行 `config init` 或 `auth login`。")
            append("其余情况下如需使用飞书能力，先向用户说明需要完成插件连接。")
        }
    }

    private fun openAppScopes() {
        val appId = lastAppId ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$APP_CONSOLE_URL/$appId/auth"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun missingScopesLabel(scopes: List<String>): String {
        val shown = scopes.take(SHOWN_SCOPE_LIMIT).joinToString(" ")
        return if (scopes.size > SHOWN_SCOPE_LIMIT) {
            context.getString(R.string.plugin_feishu_missing_scopes_more, scopes.size, shown)
        } else {
            shown
        }
    }

    private suspend fun readAuthStatus(): FeishuCli.AuthStatus? {
        val result = linux.execute(FeishuCli.statusCommand(), STATUS_TIMEOUT_MS)
        return FeishuCli.parseAuthStatus(result.output)
    }

    private suspend fun runConfigInit(ui: PluginUi): String? {
        var found: String? = null
        val result = linux.executeStreaming(FeishuCli.configInitCommand(), CONFIG_INIT_TIMEOUT_MS) { line ->
            if (found == null) {
                val url = FeishuCli.parseConfigInitUrl(line)
                if (url != null) {
                    found = url
                    ui.stage(
                        R.string.plugin_feishu_stage_create_app,
                        messageRes = R.string.plugin_feishu_hint_create_app,
                        url = url,
                    )
                }
            }
        }
        if (result.exitCode != 0) {
            throw IllegalStateException(
                context.getString(R.string.plugin_feishu_error_auth_output, result.output.takeLast(300).trim()),
            )
        }
        return found
    }

    private suspend fun requireAvailable() {
        val availability = availability()
        if (availability is PluginAvailability.Unavailable) {
            throw IllegalStateException(availability.reason)
        }
    }

    private suspend fun hasExistingBinary(): Boolean = runCatching {
        linux.execute(EXISTS_COMMAND, VERSION_TIMEOUT_MS).output.contains("yes")
    }.getOrDefault(false)

    private suspend fun restoreOrRemoveBinary(hadBinary: Boolean) {
        runCatching { linux.execute(feishuRestoreCommand(hadBinary), CLEANUP_TIMEOUT_MS) }
    }

    private companion object {
        const val VERSION = "1.0.98"
        const val EXISTS_COMMAND = "if [ -x ${FeishuCli.INSTALL_PATH} ]; then echo yes; else echo no; fi"
        const val ARCHIVE_NAME = "lark-cli-1.0.98-linux-arm64.tar.gz"
        const val ARCHIVE_SHA256 = "f798b26ce4b369d70411945e7f66bbe4382c411bc47d81950a662de7b69b41ed"
        const val SKILL_ID = "deepdeep-feishu"

        const val ACTION_CONNECT = "connect"
        const val ACTION_APP_SCOPES = "app_scopes"
        const val ACTION_REAUTHORIZE = "reauthorize"
        const val ACTION_LOGOUT = "logout"

        const val APP_CONSOLE_URL = "https://open.feishu.cn/app"
        const val SHOWN_SCOPE_LIMIT = 3

        val DOWNLOAD_URLS = listOf(
            "https://registry.npmmirror.com/-/binary/lark-cli/v1.0.98/lark-cli-1.0.98-linux-arm64.tar.gz",
            "https://github.com/larksuite/cli/releases/download/v1.0.98/lark-cli-1.0.98-linux-arm64.tar.gz",
        )

        const val EXTRACT_TIMEOUT_MS = 120_000L
        const val INSTALL_TIMEOUT_MS = 120_000L
        const val UNINSTALL_TIMEOUT_MS = 60_000L
        const val VERSION_TIMEOUT_MS = 20_000L
        const val STATUS_TIMEOUT_MS = 30_000L
        const val CONFIG_INIT_TIMEOUT_MS = 10 * 60_000L
        const val LOGIN_START_TIMEOUT_MS = 60_000L
        const val LOGIN_POLL_TIMEOUT_MS = 11 * 60_000L
        const val LOGOUT_TIMEOUT_MS = 60_000L
        const val CLEANUP_TIMEOUT_MS = 30_000L

        val SKILL_MARKDOWN = """
            ---
            name: deepdeep-feishu
            description: 飞书 CLI 用法速查：读取消息与群聊、查看日历、搜索云文档、云空间文件、邮箱与任务；当用户提到飞书、lark、消息、日程或云文档时使用。
            ---

            # 飞书 CLI

            DeepDeep 已安装官方 `lark-cli`，在 bash 中直接运行。命令默认输出 JSON，成功与否以退出码或输出字段为准。

            - 读取用户个人数据必须使用 `--as user`；`--as bot` 只能访问应用自己的资源。
            - 调用前先看用法：`lark-cli <domain> --help`。
            - 不熟悉的业务域先读指南：`lark-cli skills list`、`lark-cli skills read lark-im`。
            - 命令返回 `missing_scopes` 或登录时提示缺少权限，说明飞书应用本身未开通该权限；不要反复重新授权。
            - 电子表格/多维表格没有直读权限时，改用导出兜底：`mkdir -p <download-directory> && lark-cli drive +export --url <链接> --file-extension xlsx --output-dir <download-directory>`（电子表格），多维表格把 `xlsx` 换成 `base`，导出到 `<download-directory>` 后再解析文件。
            - 需要直读时，请用户在插件详情页点「应用权限」，到飞书开放平台为应用开通对应权限，再重新授权。
            - 已授权范围内直接执行；超出授权的发送、修改、删除、授权等操作先询问；支持时可用 `--dry-run` 预览。
            - 高风险写入会要求确认参数或返回退出码 10，先向用户说明再继续。

            常用入口：

            - 消息：`lark-cli im +chat-list`、`lark-cli im +chat-messages-list --chat-id oc_xxx --start <ISO> --end <ISO>`、`lark-cli im +messages-search`
            - 日历：`lark-cli calendar +agenda`
            - 云文档：`lark-cli docs +create`、`lark-cli docs +fetch`
            - 云空间：`lark-cli drive ...`；导出：`lark-cli drive +export`
            - 电子表格：有权限时用 `lark-cli sheets ...`，无直读权限时用 `lark-cli drive +export --file-extension xlsx`
            - 多维表格：有权限时用 `lark-cli base ...`，无直读权限时用 `lark-cli drive +export --file-extension base`
            - 邮箱：`lark-cli mail ...`
            - 任务：`lark-cli task ...`

            不要自行创建定时任务或总结流程，除非用户明确要求。
        """.trimIndent()
    }
}

internal const val LARK_CLI_BACKUP_PATH = "/usr/local/bin/.lark-cli.old"
internal const val LARK_CLI_TEMP_PATH = "/usr/local/bin/.lark-cli.new"

internal fun feishuRestoreCommand(hadBinary: Boolean): String = buildString {
    appendLine("if [ -f $LARK_CLI_BACKUP_PATH ]; then")
    appendLine("mv -f $LARK_CLI_BACKUP_PATH ${FeishuCli.INSTALL_PATH}")
    appendLine("chmod 755 ${FeishuCli.INSTALL_PATH}")
    if (!hadBinary) {
        appendLine("else")
        appendLine("rm -f ${FeishuCli.INSTALL_PATH}")
    }
    appendLine("fi")
    appendLine("rm -f $LARK_CLI_TEMP_PATH")
    append("true")
}
