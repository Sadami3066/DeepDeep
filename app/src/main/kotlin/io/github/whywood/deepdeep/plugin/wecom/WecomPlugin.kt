package io.github.whywood.deepdeep.plugin.wecom

import android.content.Context
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.plugin.PluginDownloader
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAction
import io.github.whywood.deepdeep.plugin.api.PluginActionStyle
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginDetailRow
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginProgress
import io.github.whywood.deepdeep.plugin.api.PluginSetupUi
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.PromptSection
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WecomPlugin internal constructor(
    private val context: Context,
    private val runtime: WecomRuntime,
    private val stagingRuntime: (File) -> WecomRuntime,
    private val downloader: PluginDownloader,
    private val workspaceDir: File,
) : DeepDeepPlugin {
    constructor(
        context: Context,
        linux: LinuxEnvironment,
        linuxSettings: LinuxSettingsStore,
        downloader: PluginDownloader,
        workspaceDir: File,
    ) : this(
        context = context,
        runtime = LinuxWecomRuntime(context, linux, linuxSettings),
        stagingRuntime = { dir ->
            LinuxWecomRuntime(
                context,
                LinuxEnvironment(context, workspaceDir = dir),
                linuxSettings,
            )
        },
        downloader = downloader,
        workspaceDir = workspaceDir,
    )

    @Volatile private var lastVersion: String? = null
    @Volatile private var lastBotId: String? = null
    @Volatile private var lastStatus: PluginStatus? = null

    override suspend fun availability(host: PluginHost): PluginAvailability {
        if (!runtime.isSupported) {
            return PluginAvailability.Unavailable(
                host.text(LocalizedText.Resource(R.string.plugin_wecom_unavailable_device)),
            )
        }
        if (!runtime.isInstalled()) {
            return PluginAvailability.Unavailable(
                host.text(LocalizedText.Resource(R.string.plugin_wecom_unavailable_linux)),
            )
        }
        if (!runtime.isEnabled()) {
            return PluginAvailability.Unavailable(
                host.text(LocalizedText.Resource(R.string.plugin_wecom_unavailable_disabled)),
            )
        }
        return PluginAvailability.Available
    }

    override suspend fun install(host: PluginHost, progress: PluginProgress) {
        requireAvailable(host)
        val hadBinary = hasExistingBinary()
        val staging = File(context.cacheDir, "plugin-staging/${host.manifest.id}")
        staging.deleteRecursively()
        staging.mkdirs()
        val archive = File(staging, WecomCli.ARCHIVE_NAME)
        try {
            downloader.download(WecomCli.DOWNLOAD_URLS, WecomCli.ARCHIVE_SHA256, archive) { percent ->
                progress.report(percent, LocalizedText.Resource(R.string.plugin_wecom_stage_downloading))
            }
            progress.report(null, LocalizedText.Resource(R.string.plugin_wecom_stage_verifying))
            val runner = stagingRuntime(staging)
            val extract = runner.executeStreaming(WecomCli.extractCommand(), EXTRACT_TIMEOUT_MS) { }
            val stagedVersion = WecomCli.parseVersion(extract.output)
            if (extract.exitCode != 0 || stagedVersion != WecomCli.VERSION) {
                throw IllegalStateException(
                    host.text(
                        LocalizedText.Resource(R.string.plugin_wecom_error_version),
                        extract.output.takeLast(400).trim(),
                    ),
                )
            }
            progress.report(null, LocalizedText.Resource(R.string.plugin_wecom_stage_installing))
            val install = runner.executeStreaming(WecomCli.installCommand(), INSTALL_TIMEOUT_MS) { }
            val installedVersion = WecomCli.parseVersion(install.output)
            if (install.exitCode != 0 || installedVersion != WecomCli.VERSION) {
                throw IllegalStateException(
                    host.text(
                        LocalizedText.Resource(R.string.plugin_wecom_error_install),
                        install.output.takeLast(400).trim(),
                    ),
                )
            }
            lastVersion = installedVersion
        } catch (error: Exception) {
            runCatching { runtime.execute(WecomCli.restoreCommand(hadBinary), CLEANUP_TIMEOUT_MS) }
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    override suspend fun uninstall(host: PluginHost) {
        runtime.execute(WecomCli.uninstallCommand(), UNINSTALL_TIMEOUT_MS)
        lastVersion = null
        lastBotId = null
        lastStatus = null
    }

    override suspend fun status(host: PluginHost): PluginStatus = cache(
        when (val availability = availability(host)) {
            is PluginAvailability.Unavailable -> PluginStatus.Failed(availability.reason)
            PluginAvailability.Available -> readStatus(host)
        },
    )

    private suspend fun readStatus(host: PluginHost): PluginStatus {
        val versionResult = runtime.execute(WecomCli.versionCommand(), VERSION_TIMEOUT_MS)
        val installedVersion = WecomCli.parseVersion(versionResult.output)
        if (versionResult.exitCode != 0 || installedVersion == null) {
            return PluginStatus.Failed(host.text(LocalizedText.Resource(R.string.plugin_wecom_status_broken)))
        }
        lastVersion = installedVersion
        val statusResult = runtime.execute(WecomCli.authStatusCommand(), STATUS_TIMEOUT_MS)
        val authorized = WecomCli.parseAuthStatus(statusResult.output)
            ?: return PluginStatus.Failed(
                host.text(
                    LocalizedText.Resource(R.string.plugin_wecom_status_error),
                    statusResult.output.takeLast(200).trim(),
                ),
            )
        if (!authorized) {
            lastBotId = null
            return PluginStatus.NotReady(
                host.text(LocalizedText.Resource(R.string.plugin_wecom_status_not_connected)),
            )
        }
        val showResult = runtime.execute(WecomCli.authShowCommand(), STATUS_TIMEOUT_MS)
        lastBotId = WecomCli.parseBotId(showResult.output) ?: lastBotId
        val connected = host.text(LocalizedText.Resource(R.string.plugin_wecom_status_connected))
        val label = lastBotId?.let { "$connected · $it" } ?: connected
        return PluginStatus.Ready(label, lastBotId)
    }

    override suspend fun setup(host: PluginHost, ui: PluginSetupUi) {
        requireAvailable(host)
        if (status(host) is PluginStatus.Ready) return
        authorize(host, ui)
    }

    override fun actions(host: PluginHost, status: PluginStatus): List<PluginAction> =
        if (status is PluginStatus.Ready) {
            listOf(
                PluginAction(ACTION_REAUTHORIZE, LocalizedText.Resource(R.string.plugin_wecom_action_reauthorize)),
                PluginAction(
                    ACTION_LOGOUT,
                    LocalizedText.Resource(R.string.plugin_wecom_action_logout),
                    PluginActionStyle.DESTRUCTIVE,
                ),
            )
        } else {
            listOf(PluginAction(ACTION_CONNECT, LocalizedText.Resource(R.string.plugin_wecom_action_connect)))
        }

    override suspend fun runAction(host: PluginHost, actionId: String, ui: PluginSetupUi) {
        when (actionId) {
            ACTION_CONNECT -> setup(host, ui)

            ACTION_REAUTHORIZE -> {
                requireAvailable(host)
                authorize(host, ui)
            }

            ACTION_LOGOUT -> {
                ui.progress(null, LocalizedText.Resource(R.string.plugin_wecom_stage_signing_out))
                val result = runtime.execute(WecomCli.logoutCommand(), LOGOUT_TIMEOUT_MS)
                if (result.exitCode != 0) {
                    throw IllegalStateException(
                        host.text(
                            LocalizedText.Resource(R.string.plugin_wecom_error_logout),
                            result.output.takeLast(300).trim(),
                        ),
                    )
                }
                lastBotId = null
            }
        }
    }

    override fun detailRows(host: PluginHost, status: PluginStatus): List<PluginDetailRow> = buildList {
        lastVersion?.let { add(PluginDetailRow(LocalizedText.Resource(R.string.plugin_wecom_detail_cli_version), it)) }
        lastBotId?.let { add(PluginDetailRow(LocalizedText.Resource(R.string.plugin_wecom_detail_bot_id), it)) }
    }

    override fun skills(host: PluginHost): List<PluginSkill> = listOf(
        PluginSkill(
            SKILL_ID,
            SKILL_MARKDOWN.replace("<download-directory>", File(workspaceDir, "tmp").absolutePath),
        ),
    )

    override fun promptSections(host: PluginHost): List<PromptSection> = listOf(
        PromptSection(SECTION_ID, promptBody(host, lastStatus ?: notConnectedStatus(host))),
    )

    private fun promptBody(host: PluginHost, status: PluginStatus): String = when (status) {
        is PluginStatus.Ready -> buildString {
            appendLine(
                "企业微信插件已安装并已连接" +
                    (status.account?.let { "（Bot ID：$it）" } ?: "") + "。",
            )
            appendLine("在 bash 中通过 `wecom-cli` 访问企业微信；发送、修改、删除等操作支持时先用 `--dry-run` 预览。")
            appendLine("发消息前先运行 `wecom-cli message aibot sessions list`，使用本次返回的 `chat_id`；给授权人发消息可用 `wecom-cli identity whoami` 的 ID 作为 chat_id。")
            appendLine("不熟悉命令时先看 `wecom-cli --help`，或对应服务的 `--help`、`--doc`、`--schema`。")
            append("不要自行运行 `auth init`，也不要读取或输出凭据文件；授权由用户在插件页完成。")
        }

        else -> buildString {
            appendLine("企业微信插件已安装，但当前不可用或未连接：${status.label}")
            appendLine("需要连接或重新授权时，调用 `plugin_setup` 工具（plugin=\"wecom\"），不要自己执行 `auth init`。")
            append("其余情况下如需使用企业微信能力，先向用户说明需要完成插件连接。")
        }
    }

    private fun notConnectedStatus(host: PluginHost): PluginStatus =
        PluginStatus.NotReady(host.text(LocalizedText.Resource(R.string.plugin_wecom_status_not_connected)))

    private fun cache(status: PluginStatus): PluginStatus {
        lastStatus = status
        return status
    }

    private suspend fun authorize(host: PluginHost, ui: PluginSetupUi) {
        val qrFile = File(workspaceDir, WecomCli.QR_HOST_FILE)
        workspaceDir.mkdirs()
        qrFile.delete()
        ui.stage(
            LocalizedText.Resource(R.string.plugin_wecom_stage_scan),
            message = LocalizedText.Resource(R.string.plugin_wecom_hint_scan),
        )
        val pageUrl = AtomicReference<String?>(null)
        try {
            val result = coroutineScope {
                val poller = launch { publishQrCode(host, ui, qrFile, pageUrl) }
                try {
                    runtime.executeStreaming(WecomCli.authInitCommand(qrFile), INIT_TIMEOUT_MS) { line ->
                        val url = WecomCli.parseQrPageUrl(line)
                        if (url != null && pageUrl.compareAndSet(null, url)) {
                            ui.stage(
                                LocalizedText.Resource(R.string.plugin_wecom_stage_scan),
                                message = LocalizedText.Resource(R.string.plugin_wecom_hint_scan),
                                url = url,
                                imagePath = qrFile.takeIf(WecomCli::isCompleteQrCode)?.absolutePath,
                            )
                        }
                    }
                } finally {
                    poller.cancel()
                }
            }
            if (ui.cancelled()) throw CancellationException("cancelled")
            when {
                result.timedOut -> throw IllegalStateException(
                    host.text(LocalizedText.Resource(R.string.plugin_wecom_error_timeout)),
                )

                result.exitCode != 0 -> throw IllegalStateException(
                    host.text(
                        LocalizedText.Resource(R.string.plugin_wecom_error_auth),
                        result.output.takeLast(300).trim(),
                    ),
                )
            }
            ui.stage(LocalizedText.Resource(R.string.plugin_wecom_stage_finishing))
            val current = status(host)
            if (current !is PluginStatus.Ready) {
                throw IllegalStateException(
                    host.text(LocalizedText.Resource(R.string.plugin_wecom_error_auth), current.label),
                )
            }
        } finally {
            qrFile.delete()
        }
    }

    private suspend fun publishQrCode(
        host: PluginHost,
        ui: PluginSetupUi,
        qrFile: File,
        pageUrl: AtomicReference<String?>,
    ) {
        while (currentCoroutineContext().isActive) {
            val ready = withContext(Dispatchers.IO) { WecomCli.isCompleteQrCode(qrFile) }
            if (ready) {
                ui.stage(
                    LocalizedText.Resource(R.string.plugin_wecom_stage_scan),
                    message = LocalizedText.Resource(R.string.plugin_wecom_hint_scan),
                    url = pageUrl.get(),
                    imagePath = qrFile.absolutePath,
                )
                return
            }
            delay(QR_POLL_INTERVAL_MS)
        }
    }

    private suspend fun hasExistingBinary(): Boolean = runCatching {
        runtime.execute(
            "if [ -x ${WecomCli.INSTALL_PATH} ]; then echo yes; else echo no; fi",
            VERSION_TIMEOUT_MS,
        ).output.contains("yes")
    }.getOrDefault(false)

    private suspend fun requireAvailable(host: PluginHost) {
        val availability = availability(host)
        if (availability is PluginAvailability.Unavailable) {
            throw IllegalStateException(availability.reason)
        }
    }

    private companion object {
        const val SKILL_ID = WECOM_SKILL_ID
        const val SECTION_ID = "wecom_prompt"

        const val ACTION_CONNECT = "connect"
        const val ACTION_REAUTHORIZE = "reauthorize"
        const val ACTION_LOGOUT = "logout"

        const val QR_POLL_INTERVAL_MS = 400L
        const val EXTRACT_TIMEOUT_MS = 120_000L
        const val INSTALL_TIMEOUT_MS = 120_000L
        const val UNINSTALL_TIMEOUT_MS = 60_000L
        const val VERSION_TIMEOUT_MS = 20_000L
        const val STATUS_TIMEOUT_MS = 30_000L
        const val INIT_TIMEOUT_MS = 6 * 60_000L
        const val LOGOUT_TIMEOUT_MS = 60_000L
        const val CLEANUP_TIMEOUT_MS = 30_000L

        val SKILL_MARKDOWN = """
            ---
            name: deepdeep-wecom
            description: 企业微信 CLI 用法速查：消息、邮件、文档、在线表格、智能表格、日程、会议、待办、微盘与通讯录；当用户提到企业微信、WeCom、企微、发消息、文档、日程或待办时使用。
            ---

            # 企业微信 CLI

            DeepDeep 已安装官方 `wecom-cli`，在 bash 中直接运行。命令默认输出 compact JSON，以退出码和输出字段判断结果。

            - 先确认命令与参数：`wecom-cli --help`、`wecom-cli <service> --help`；需要文档或 schema 时加 `--doc` / `--schema`。
            - 服务目录在线获取，需要已授权与网络；鉴权失败时提示用户在插件页重新授权，不要自行运行 `auth init`。
            - 已授权范围内直接执行；超出授权的发送、修改、删除等操作先询问；支持时可用 `--dry-run` 预览。
            - 请求体可用命名参数、`--json '<JSON>'` 或 `--set path=value` 组合给出。
            - 发消息前先 `wecom-cli message aibot sessions list`，用本次返回的 `chat_id`；给授权人发消息可用 `wecom-cli identity whoami` 的 ID 作为 chat_id。
            - 发送图片、文件、语音或视频前，先经 `wecom-cli media` 上传拿到 `media_id`。
            - 拉取多页数据用 `--page-count <n>`；下载文件用 `--output-dir <download-directory>` 落到工作区，再按当前运行支持的交付方式返回文件绝对路径。
            - 不要自行创建定时任务或总结流程，除非用户明确要求。
        """.trimIndent()
    }
}

internal const val WECOM_SKILL_ID = "deepdeep-wecom"
