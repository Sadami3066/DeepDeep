package io.github.whywood.deepdeep.plugin.arrange

import android.content.Context
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

class ArrangePlugin(
    private val context: Context,
    private val launcher: ArrangeLauncher = AndroidArrangeLauncher(context),
) : OfficialPlugin {
    override val id: String = "arrange"
    override val icon: DdIcon = DdIcons.Clock
    override val nameRes: Int = R.string.plugin_arrange_name
    override val summaryRes: Int = R.string.plugin_arrange_summary
    override val version: String = "1.0.0"
    override val sizeLabelRes: Int = R.string.plugin_arrange_size

    override suspend fun availability(): PluginAvailability = PluginAvailability.Available

    override suspend fun install(progress: PluginProgress) = Unit

    override suspend fun uninstall() = Unit

    override suspend fun status(): PluginStatus = PluginStatus.Ready(string(R.string.plugin_arrange_status_ready))

    override fun actions(status: PluginStatus): List<PluginAction> = listOf(
        PluginAction(ACTION_ALARM, R.string.plugin_arrange_action_alarm),
        PluginAction(ACTION_CALENDAR, R.string.plugin_arrange_action_calendar),
    )

    override suspend fun runAction(actionId: String, ui: PluginUi) {
        val spec = when (actionId) {
            ACTION_ALARM -> alarmShowRequest()
            ACTION_CALENDAR -> calendarOpenRequest(null)
            else -> return
        }
        val result = launcher.launch(spec)
        if (!result.launched) {
            throw IllegalStateException(result.errorMessage ?: string(R.string.plugin_arrange_error_launch))
        }
    }

    override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = listOf(
        PluginDetailRow(
            R.string.plugin_arrange_detail_saved,
            string(R.string.plugin_arrange_detail_saved_value),
        ),
        PluginDetailRow(
            R.string.plugin_arrange_detail_confirm,
            string(R.string.plugin_arrange_detail_confirm_value),
        ),
    )

    override fun tools(): List<CodingTool> = arrangeTools(launcher)

    override fun skills(): List<PluginSkill> = listOf(PluginSkill(SKILL_ID, SKILL_MARKDOWN))

    override fun promptBody(status: PluginStatus): String = PROMPT_BODY

    private fun string(id: Int): String = context.resources.getString(id)

    private companion object {
        const val ACTION_ALARM = "alarm"
        const val ACTION_CALENDAR = "calendar"
        const val SKILL_ID = "deepdeep-arrange"

        val PROMPT_BODY = """
            安排插件已可用：
            - `arrange_alarm`：请系统时钟设置闹钟（time 或 hour+minute、label、days ISO 1-7、skipUi）、创建计时器（durationSeconds、label）或打开闹钟列表。
            - `arrange_calendar`：打开系统日历新建页并预填日程（title、start、end?、allDay?、location?、description?），或用 time 打开日历。
            App 无法读取、修改或删除系统里已保存的闹钟与日程；系统可能要求用户确认，不要声称保存成功。
        """.trimIndent()

        val SKILL_MARKDOWN = """
            ---
            name: deepdeep-arrange
            description: 通过系统时钟与日历处理安排：设置闹钟、计时器、新建日程或打开系统应用；当用户要求叫醒、提醒、倒计时、开会或日程安排并需要落到系统闹钟/日历时使用。
            ---

            # 安排

            用 `arrange_alarm` 与 `arrange_calendar` 把安排交给系统的时钟与日历应用。App 只负责打开系统界面或提交请求，无法读取已保存的闹钟和日程，也无法修改或删除它们。

            ## 闹钟与计时器

            - `arrange_alarm` 的 `action=set`：`time` 用 24 小时制 `HH:mm`，也可以同时给 `hour` 与 `minute`；`label` 是显示在闹钟上的说明；`days` 用 ISO 星期（1=周一 … 7=周日），不传表示只响一次；`skipUi=true` 请求系统静默创建，部分系统仍会弹出确认。
            - `action=timer`：`durationSeconds` 是倒计时长（1-86400 秒），`label` 可选。
            - `action=show`：打开系统闹钟列表。
            - 用户说「明早 7 点」这类相对时间时，按当前日期与本地时区换算成 `HH:mm`；重复闹钟再补 `days`。

            ## 日历

            - `arrange_calendar` 的 `action=create`：必填 `title`、`start`，可选 `end`、`allDay`、`location`、`description`。
            - `start`/`end` 支持 `yyyy-MM-dd HH:mm`、`yyyy-MM-dd`、ISO 8601 或毫秒时间戳；只给 `start` 时默认 1 小时，`allDay=true` 且不给 `end` 时按全天处理。
            - `action=open`：打开系统日历；可以传 `time` 请求定位到某个时间，是否生效取决于系统日历。
            - 创建只是打开系统新建页并预填内容，未确认保存；要告诉用户在系统日历里确认保存。

            ## 限制

            - 只能使用当前时间与用户给出的信息，无法列出、修改或删除系统里已保存的闹钟和日程。
            - 系统可能忽略 `skipUi` 或预填字段，最终以系统应用里显示的内容为准。
            - 不要声称闹钟或日程已经创建成功；需要查看或删除时请用户打开系统时钟或日历。
            - 设备没有系统时钟或日历应用时会返回错误，请用户检查系统应用。
        """.trimIndent()
    }
}
