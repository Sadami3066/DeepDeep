package io.github.whywood.deepdeep.ui.common.scan

import java.io.File

internal sealed interface ScanStrategyIssue {
    data object CorruptCustom : ScanStrategyIssue
    data object UnreadableCustom : ScanStrategyIssue
    data class SkippedEntries(val count: Int) : ScanStrategyIssue
}

internal class ScanStrategyRepository(
    private val officialText: () -> String?,
    private val customFile: File,
) {
    data class Snapshot(
        val official: List<ScanStrategy>,
        val custom: List<ScanStrategy>,
        val issue: ScanStrategyIssue?,
    ) {
        val matching: List<ScanStrategy> get() = custom + official
    }

    data class ImportResult(val count: Int?, val issue: ScanStrategyIssue?)

    fun load(): Snapshot {
        val officialParsed = officialText()?.let { ScanStrategyJson.parse(it) }
        val official = officialParsed?.strategies.orEmpty()
        var issue: ScanStrategyIssue? = null
        var custom: List<ScanStrategy> = emptyList()
        if (customFile.isFile) {
            val text = runCatching { customFile.readText() }.getOrNull()
            if (text == null) {
                issue = ScanStrategyIssue.UnreadableCustom
            } else if (text.isNotBlank()) {
                val parsed = ScanStrategyJson.parse(text)
                if (parsed == null) {
                    issue = ScanStrategyIssue.CorruptCustom
                } else {
                    custom = if (parsed.version < ScanStrategyJson.CURRENT_VERSION) {
                        val migrated = migrate(parsed.strategies, official)
                        writeCustom(migrated)
                        migrated
                    } else {
                        parsed.strategies
                    }
                    if (parsed.skipped > 0) issue = ScanStrategyIssue.SkippedEntries(parsed.skipped)
                }
            }
        }
        return Snapshot(official, custom, issue)
    }

    fun saveCustom(custom: List<ScanStrategy>) {
        writeCustom(custom)
    }

    fun restoreOfficial(snapshot: Snapshot): Snapshot {
        val officialPackages = snapshot.official.mapTo(mutableSetOf()) { it.packageName }
        val restored = snapshot.custom.filterNot { it.packageName in officialPackages }
        writeCustom(restored)
        return snapshot.copy(custom = restored)
    }

    fun importCustom(text: String): ImportResult {
        val parsed = ScanStrategyJson.parse(text)
            ?: return ImportResult(null, ScanStrategyIssue.CorruptCustom)
        val official = load().official
        val imported = if (parsed.version < ScanStrategyJson.CURRENT_VERSION) {
            migrate(parsed.strategies, official)
        } else {
            parsed.strategies
        }
        writeCustom(imported)
        val issue = parsed.skipped.takeIf { it > 0 }?.let { ScanStrategyIssue.SkippedEntries(it) }
        return ImportResult(imported.size, issue)
    }

    private fun migrate(old: List<ScanStrategy>, official: List<ScanStrategy>): List<ScanStrategy> {
        val officialByPackage = official.associateBy { it.packageName }
        return old.filterNot { entry ->
            val officialEntry = officialByPackage[entry.packageName] ?: return@filterNot false
            sameStrategy(entry, officialEntry)
        }
    }

    private fun writeCustom(custom: List<ScanStrategy>) {
        customFile.parentFile?.mkdirs()
        customFile.writeText(ScanStrategyJson.render(custom))
    }

    private fun sameStrategy(left: ScanStrategy, right: ScanStrategy): Boolean =
        left.packageName == right.packageName &&
            left.hosts == right.hosts &&
            left.schemes == right.schemes &&
            left.contains == right.contains &&
            left.regexes.map { it.pattern } == right.regexes.map { it.pattern } &&
            left.priority == right.priority &&
            left.fallbackMain == right.fallbackMain &&
            left.launch == right.launch &&
            left.note == right.note
}
