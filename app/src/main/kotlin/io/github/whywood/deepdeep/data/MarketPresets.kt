package io.github.whywood.deepdeep.data

import androidx.annotation.StringRes
import io.github.whywood.deepdeep.R

enum class MarketCategory { Skill, Mcp, Plugin }

data class MarketPreset(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val summaryRes: Int,
    @StringRes val tagsRes: Int,
    val category: MarketCategory,
)

internal object MarketPresets {

    val skills: List<MarketPreset> = listOf(
        MarketPreset(
            id = "polish",
            nameRes = R.string.market_skill_polish_name,
            summaryRes = R.string.market_skill_polish_summary,
            tagsRes = R.string.market_skill_polish_tags,
            category = MarketCategory.Skill,
        ),
        MarketPreset(
            id = "summary",
            nameRes = R.string.market_skill_summary_name,
            summaryRes = R.string.market_skill_summary_summary,
            tagsRes = R.string.market_skill_summary_tags,
            category = MarketCategory.Skill,
        ),
        MarketPreset(
            id = "meeting",
            nameRes = R.string.market_skill_meeting_name,
            summaryRes = R.string.market_skill_meeting_summary,
            tagsRes = R.string.market_skill_meeting_tags,
            category = MarketCategory.Skill,
        ),
        MarketPreset(
            id = "review",
            nameRes = R.string.market_skill_review_name,
            summaryRes = R.string.market_skill_review_summary,
            tagsRes = R.string.market_skill_review_tags,
            category = MarketCategory.Skill,
        ),
        MarketPreset(
            id = "translate",
            nameRes = R.string.market_skill_translate_name,
            summaryRes = R.string.market_skill_translate_summary,
            tagsRes = R.string.market_skill_translate_tags,
            category = MarketCategory.Skill,
        ),
        MarketPreset(
            id = "weekly",
            nameRes = R.string.market_skill_weekly_name,
            summaryRes = R.string.market_skill_weekly_summary,
            tagsRes = R.string.market_skill_weekly_tags,
            category = MarketCategory.Skill,
        ),
        MarketPreset(
            id = "study",
            nameRes = R.string.market_skill_study_name,
            summaryRes = R.string.market_skill_study_summary,
            tagsRes = R.string.market_skill_study_tags,
            category = MarketCategory.Skill,
        ),
        MarketPreset(
            id = "resume",
            nameRes = R.string.market_skill_resume_name,
            summaryRes = R.string.market_skill_resume_summary,
            tagsRes = R.string.market_skill_resume_tags,
            category = MarketCategory.Skill,
        ),
    )

    val mcp: List<MarketPreset> = listOf(
        MarketPreset(
            id = "files",
            nameRes = R.string.market_mcp_files_name,
            summaryRes = R.string.market_mcp_files_summary,
            tagsRes = R.string.market_mcp_files_tags,
            category = MarketCategory.Mcp,
        ),
        MarketPreset(
            id = "web",
            nameRes = R.string.market_mcp_web_name,
            summaryRes = R.string.market_mcp_web_summary,
            tagsRes = R.string.market_mcp_web_tags,
            category = MarketCategory.Mcp,
        ),
        MarketPreset(
            id = "git",
            nameRes = R.string.market_mcp_git_name,
            summaryRes = R.string.market_mcp_git_summary,
            tagsRes = R.string.market_mcp_git_tags,
            category = MarketCategory.Mcp,
        ),
        MarketPreset(
            id = "database",
            nameRes = R.string.market_mcp_database_name,
            summaryRes = R.string.market_mcp_database_summary,
            tagsRes = R.string.market_mcp_database_tags,
            category = MarketCategory.Mcp,
        ),
        MarketPreset(
            id = "calendar",
            nameRes = R.string.market_mcp_calendar_name,
            summaryRes = R.string.market_mcp_calendar_summary,
            tagsRes = R.string.market_mcp_calendar_tags,
            category = MarketCategory.Mcp,
        ),
        MarketPreset(
            id = "cloud",
            nameRes = R.string.market_mcp_cloud_name,
            summaryRes = R.string.market_mcp_cloud_summary,
            tagsRes = R.string.market_mcp_cloud_tags,
            category = MarketCategory.Mcp,
        ),
        MarketPreset(
            id = "chat",
            nameRes = R.string.market_mcp_chat_name,
            summaryRes = R.string.market_mcp_chat_summary,
            tagsRes = R.string.market_mcp_chat_tags,
            category = MarketCategory.Mcp,
        ),
        MarketPreset(
            id = "design",
            nameRes = R.string.market_mcp_design_name,
            summaryRes = R.string.market_mcp_design_summary,
            tagsRes = R.string.market_mcp_design_tags,
            category = MarketCategory.Mcp,
        ),
    )

    val plugins: List<MarketPreset> = listOf(
        MarketPreset(
            id = "brief",
            nameRes = R.string.market_plugin_brief_name,
            summaryRes = R.string.market_plugin_brief_summary,
            tagsRes = R.string.market_plugin_brief_tags,
            category = MarketCategory.Plugin,
        ),
        MarketPreset(
            id = "translate",
            nameRes = R.string.market_plugin_translate_name,
            summaryRes = R.string.market_plugin_translate_summary,
            tagsRes = R.string.market_plugin_translate_tags,
            category = MarketCategory.Plugin,
        ),
        MarketPreset(
            id = "snippets",
            nameRes = R.string.market_plugin_snippets_name,
            summaryRes = R.string.market_plugin_snippets_summary,
            tagsRes = R.string.market_plugin_snippets_tags,
            category = MarketCategory.Plugin,
        ),
        MarketPreset(
            id = "focus",
            nameRes = R.string.market_plugin_focus_name,
            summaryRes = R.string.market_plugin_focus_summary,
            tagsRes = R.string.market_plugin_focus_tags,
            category = MarketCategory.Plugin,
        ),
        MarketPreset(
            id = "export",
            nameRes = R.string.market_plugin_export_name,
            summaryRes = R.string.market_plugin_export_summary,
            tagsRes = R.string.market_plugin_export_tags,
            category = MarketCategory.Plugin,
        ),
        MarketPreset(
            id = "expense",
            nameRes = R.string.market_plugin_expense_name,
            summaryRes = R.string.market_plugin_expense_summary,
            tagsRes = R.string.market_plugin_expense_tags,
            category = MarketCategory.Plugin,
        ),
        MarketPreset(
            id = "trip",
            nameRes = R.string.market_plugin_trip_name,
            summaryRes = R.string.market_plugin_trip_summary,
            tagsRes = R.string.market_plugin_trip_tags,
            category = MarketCategory.Plugin,
        ),
        MarketPreset(
            id = "reading",
            nameRes = R.string.market_plugin_reading_name,
            summaryRes = R.string.market_plugin_reading_summary,
            tagsRes = R.string.market_plugin_reading_tags,
            category = MarketCategory.Plugin,
        ),
    )
}

internal fun filterMarketPresets(
    presets: List<MarketPreset>,
    query: String,
    resolve: (MarketPreset) -> String,
): List<MarketPreset> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return presets
    return presets.filter { preset -> resolve(preset).contains(keyword, ignoreCase = true) }
}
