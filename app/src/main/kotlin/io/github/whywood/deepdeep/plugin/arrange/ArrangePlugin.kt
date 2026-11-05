package io.github.whywood.deepdeep.plugin.arrange

import android.content.Context
import android.content.Intent
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAction
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSetupUi
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.host.CodingToolPluginAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class ArrangePlugin(
    private val context: Context,
    private val calendar: ArrangeCalendarGateway = AndroidArrangeCalendarGateway(context),
) : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override suspend fun status(host: PluginHost): PluginStatus = if (hasCalendarPermission()) {
        PluginStatus.Ready(host.text(LocalizedText.Resource(R.string.plugin_arrange_status_ready)))
    } else {
        PluginStatus.Ready(host.text(LocalizedText.Resource(R.string.plugin_arrange_status_calendar_permission)))
    }

    override suspend fun setup(host: PluginHost, ui: PluginSetupUi) {
        if (hasCalendarPermission()) return
        ui.stage(
            LocalizedText.Resource(R.string.plugin_arrange_permission_title),
            message = LocalizedText.Resource(R.string.plugin_arrange_permission_message),
        )
        context.startActivity(
            Intent(context, ArrangePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val deadline = System.currentTimeMillis() + PERMISSION_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (ui.cancelled()) throw CancellationException("arrange permission cancelled")
            delay(PERMISSION_POLL_MS)
            if (hasCalendarPermission()) return
        }
        throw IllegalStateException(
            host.text(LocalizedText.Resource(R.string.plugin_arrange_error_calendar_permission)),
        )
    }

    override fun actions(host: PluginHost, status: PluginStatus): List<PluginAction> = buildList {
        if (!hasCalendarPermission()) {
            add(
                PluginAction(
                    ACTION_PERMISSION,
                    LocalizedText.Resource(R.string.plugin_arrange_action_permission),
                ),
            )
        }
    }

    override suspend fun runAction(host: PluginHost, actionId: String, ui: PluginSetupUi) {
        if (actionId != ACTION_PERMISSION) return
        context.startActivity(
            Intent(context, ArrangePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions =
        Contributions(tools = CodingToolPluginAdapter.toPluginTools(arrangeTools(calendar)))

    override fun skills(host: PluginHost): List<PluginSkill> = listOf(PluginSkill(SKILL_ID, SKILL_MARKDOWN))

    private fun hasCalendarPermission(): Boolean = calendar.hasReadPermission() && calendar.hasWritePermission()

    private companion object {
        const val ACTION_PERMISSION = "permission"
        const val SKILL_ID = "deepdeep-arrange"
        const val PERMISSION_POLL_MS = 500L
        const val PERMISSION_TIMEOUT_MS = 2L * 60L * 1_000L

        val SKILL_MARKDOWN = """
            ---
            name: deepdeep-arrange
            description: 管理系统日历事件与提醒：创建、读取、修改、删除；需要把会议、日程或提醒落到系统日历时使用。
            ---

            # 安排

            用 `arrange_calendar` 直接读写系统日历。所有修改类操作先用 `list` 或 `get` 拿到 id，结果以工具返回 JSON 为准。

            - `action=calendars`：列出可写日历；多日历时先查 id。
            - `action=list`：`start`/`end` 时间范围（默认现在到 30 天后），`query` 标题过滤，`limit` 1-200。
            - `action=get`：`id` 读取单个日程。
            - `action=create`：必填 `title`、`start`，可选 `end`、`allDay`、`location`、`description`、`calendarId`、`reminders`（提前分钟数组）。
            - `action=update`：`id` 必填，只改提供的字段；`location`/`description` 传空字符串可清除，`reminders` 传空数组清空提醒。
            - `action=delete`：`id` 必填。
            - `start`/`end` 支持 `yyyy-MM-dd HH:mm`、`yyyy-MM-dd`、ISO 8601 或毫秒时间戳；非全天只给 start 时默认 1 小时。

            日历读写需要日历权限；未授权时工具会返回错误，提示用户在「安排」插件中授权。日历写入会通过系统账户同步；最终以系统日历显示为准。
        """.trimIndent()
    }
}
