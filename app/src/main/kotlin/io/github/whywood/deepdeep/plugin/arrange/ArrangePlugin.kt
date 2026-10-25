package io.github.whywood.deepdeep.plugin.arrange

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.OfficialPlugin
import io.github.whywood.deepdeep.plugin.PluginAction
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginDetailRow
import io.github.whywood.deepdeep.plugin.PluginProgress
import io.github.whywood.deepdeep.plugin.PluginSkill
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.PluginUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class ArrangePlugin(
    private val context: Context,
    private val launcher: ArrangeLauncher = AndroidArrangeLauncher(context),
    private val alarms: ArrangeAlarmEngine = AndroidArrangeAlarmEngine(context),
    private val calendar: ArrangeCalendarGateway = AndroidArrangeCalendarGateway(context),
) : OfficialPlugin {
    override val id: String = "arrange"
    override val icon: DdIcon = DdIcons.Clock
    override val nameRes: Int = R.string.plugin_arrange_name
    override val summaryRes: Int = R.string.plugin_arrange_summary
    override val version: String = "2.0.0"
    override val sizeLabelRes: Int = R.string.plugin_arrange_size

    override suspend fun availability(): PluginAvailability = PluginAvailability.Available

    override suspend fun install(progress: PluginProgress) = Unit

    override suspend fun uninstall() = Unit

    override suspend fun status(): PluginStatus = if (calendar.hasReadPermission() && calendar.hasWritePermission()) {
        PluginStatus.Ready(string(R.string.plugin_arrange_status_ready))
    } else {
        PluginStatus.Ready(string(R.string.plugin_arrange_status_calendar_permission))
    }

    override suspend fun setup(ui: PluginUi) {
        if (calendar.hasReadPermission() && calendar.hasWritePermission()) return
        ui.stage(R.string.plugin_arrange_permission_title, messageRes = R.string.plugin_arrange_permission_message)
        context.startActivity(
            Intent(context, ArrangePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val deadline = System.currentTimeMillis() + PERMISSION_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (ui.cancelled()) throw CancellationException("arrange permission cancelled")
            delay(PERMISSION_POLL_MS)
            if (calendar.hasReadPermission() && calendar.hasWritePermission()) return
        }
        throw IllegalStateException(string(R.string.plugin_arrange_error_calendar_permission))
    }

    override fun actions(status: PluginStatus): List<PluginAction> = buildList {
        add(PluginAction(ACTION_ALARM, R.string.plugin_arrange_action_alarm))
        add(PluginAction(ACTION_CALENDAR, R.string.plugin_arrange_action_calendar))
        if (!calendar.hasReadPermission() || !calendar.hasWritePermission()) {
            add(PluginAction(ACTION_PERMISSION, R.string.plugin_arrange_action_permission))
        }
        if (!alarms.canScheduleExactAlarms()) {
            add(PluginAction(ACTION_EXACT_ALARM, R.string.plugin_arrange_action_exact_alarm))
        }
    }

    override suspend fun runAction(actionId: String, ui: PluginUi) {
        when (actionId) {
            ACTION_ALARM -> launch(alarmShowRequest())
            ACTION_CALENDAR -> launch(calendarOpenRequest(null))
            ACTION_PERMISSION -> context.startActivity(
                Intent(context, ArrangePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            ACTION_EXACT_ALARM -> requestExactAlarmPermission()
        }
    }

    override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = listOf(
        PluginDetailRow(
            R.string.plugin_arrange_detail_alarms,
            string(R.string.plugin_arrange_detail_alarms_value),
        ),
        PluginDetailRow(
            R.string.plugin_arrange_detail_calendar,
            if (calendar.hasReadPermission() && calendar.hasWritePermission()) {
                string(R.string.plugin_arrange_detail_calendar_granted)
            } else {
                string(R.string.plugin_arrange_detail_calendar_missing)
            },
        ),
        PluginDetailRow(
            R.string.plugin_arrange_detail_system,
            string(R.string.plugin_arrange_detail_system_value),
        ),
        PluginDetailRow(
            R.string.plugin_arrange_detail_exact,
            if (alarms.canScheduleExactAlarms()) {
                string(R.string.plugin_arrange_detail_exact_granted)
            } else {
                string(R.string.plugin_arrange_detail_exact_missing)
            },
        ),
    )

    override fun tools(): List<CodingTool> = arrangeTools(launcher, alarms, calendar)

    override fun skills(): List<PluginSkill> = listOf(PluginSkill(SKILL_ID, SKILL_MARKDOWN))

    override fun promptBody(status: PluginStatus): String = PROMPT_BODY

    private fun launch(spec: ArrangeIntentSpec) {
        val result = launcher.launch(spec)
        if (!result.launched) {
            throw IllegalStateException(result.errorMessage ?: string(R.string.plugin_arrange_error_launch))
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { throw IllegalStateException(string(R.string.plugin_arrange_error_exact_alarm)) }
    }

    private fun string(id: Int): String = context.resources.getString(id)

    private companion object {
        const val ACTION_ALARM = "alarm"
        const val ACTION_CALENDAR = "calendar"
        const val ACTION_PERMISSION = "permission"
        const val ACTION_EXACT_ALARM = "exact_alarm"
        const val SKILL_ID = "deepdeep-arrange"
        const val PERMISSION_POLL_MS = 500L
        const val PERMISSION_TIMEOUT_MS = 2L * 60L * 1_000L

        val PROMPT_BODY = """
            安排插件已可用：
            - `arrange_alarm`：创建、列出、修改、删除 App 管理的闹钟与计时器（`list` / `set` / `update` / `delete` / `timer` / `show`）。到点由 App 响铃。
            - `arrange_calendar`：直接读写系统日历（`calendars` / `list` / `get` / `create` / `update` / `delete` / `open`），需要日历权限。
            系统时钟里已有的闹钟无法读取、修改或删除；`show` 只打开系统时钟。所有修改类操作先 `list`/`get` 取得 id，结果以工具返回为准。
        """.trimIndent()

        val SKILL_MARKDOWN = """
            ---
            name: deepdeep-arrange
            description: 管理 App 闹钟、计时器与系统日历事件：创建、列出、修改、删除；需要叫醒、倒计时、会议或日程落库时使用。
            ---

            # 安排

            用 `arrange_alarm` 管理 App 自己的闹钟与计时器，用 `arrange_calendar` 直接读写系统日历。两者都返回结构化 JSON；修改类操作先用 `list` 或 `get` 拿到 id。

            ## 闹钟与计时器

            - `action=list`：列出 App 管理的闹钟、计时器与下次系统闹钟（`systemNext` 只读）。
            - `action=set`：`time`（HH:mm）或 `hour`+`minute`，`label` 可选，`days` 用 ISO 星期（1=周一 … 7=周日，省略或空数组表示只响一次），`enabled` 默认 true。
            - `action=update`：`id` 必填，可改 `time`/`hour`+`minute`/`label`/`days`/`enabled`；未提供的字段保持不变。
            - `action=delete`：`id` 必填。
            - `action=timer`：`durationSeconds`（1-86400）与可选 `label`，到点响铃，可像闹钟一样 list/delete。
            - `action=show`：打开系统时钟；系统时钟里已有的闹钟无法读取、修改或删除。
            - 用户说「明早 7 点」这类相对时间时，按当前日期与本地时区换算成 `HH:mm`；重复闹钟再补 `days`。

            ## 日历

            - `action=calendars`：列出可写日历；多日历时先查 id。
            - `action=list`：`start`/`end` 时间范围（默认现在到 30 天后），`query` 标题过滤，`limit` 1-200。
            - `action=get`：`id` 读取单个日程。
            - `action=create`：必填 `title`、`start`，可选 `end`、`allDay`、`location`、`description`、`calendarId`、`reminders`（提前分钟数组）。
            - `action=update`：`id` 必填，只改提供的字段；`location`/`description` 传空字符串可清除，`reminders` 传空数组清空提醒。
            - `action=delete`：`id` 必填。
            - `action=open`：打开系统日历；可选 `time` 定位。
            - `start`/`end` 支持 `yyyy-MM-dd HH:mm`、`yyyy-MM-dd`、ISO 8601 或毫秒时间戳；非全天只给 start 时默认 1 小时。

            ## 权限与限制

            - 日历读写需要 READ_CALENDAR 与 WRITE_CALENDAR；未授权时工具会返回错误，提示用户在「安排」插件中授权。
            - 精确闹钟权限未允许时，设置闹钟会报错；可在插件页打开系统设置授权。
            - 日历写入会通过系统账户同步；最终以系统日历显示为准。
            - 不要声称结果之外的内容；工具返回的 id、时间与 JSON 就是实际状态。
        """.trimIndent()
    }
}
