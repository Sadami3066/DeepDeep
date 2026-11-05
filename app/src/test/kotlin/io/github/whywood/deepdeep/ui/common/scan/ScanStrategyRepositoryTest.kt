package io.github.whywood.deepdeep.ui.common.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScanStrategyRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val official = strategy("com.example.official", hosts = listOf("official.com"))

    private fun strategy(
        packageName: String,
        hosts: List<String> = emptyList(),
        schemes: List<String> = emptyList(),
        contains: List<String> = emptyList(),
        priority: Int = 0,
    ): ScanStrategy = ScanStrategy(
        packageName = packageName,
        hosts = hosts,
        schemes = schemes,
        contains = contains,
        regexes = emptyList(),
        priority = priority,
        fallbackMain = true,
        launch = ScanLaunch.Uri("$packageName://scan"),
        note = null,
    )

    private fun repository(): Pair<ScanStrategyRepository, File> {
        val file = File(folder.root, "scan/strategies.json")
        val repo = ScanStrategyRepository(
            officialText = { ScanStrategyJson.render(listOf(official)) },
            customFile = file,
        )
        return repo to file
    }

    @Test
    fun loadsOfficialWithoutCustomFile() {
        val (repo, _) = repository()
        val snapshot = repo.load()
        assertEquals(listOf(official), snapshot.official)
        assertTrue(snapshot.custom.isEmpty())
        assertNull(snapshot.issue)
    }

    @Test
    fun corruptCustomFallsBackToOfficial() {
        val (repo, file) = repository()
        file.parentFile?.mkdirs()
        file.writeText("{not json")
        val snapshot = repo.load()
        assertEquals(listOf(official), snapshot.official)
        assertTrue(snapshot.custom.isEmpty())
        assertEquals(ScanStrategyIssue.CorruptCustom, snapshot.issue)
    }

    @Test
    fun blankCustomFileHasNoCustom() {
        val (repo, file) = repository()
        file.parentFile?.mkdirs()
        file.writeText("   \n")
        val snapshot = repo.load()
        assertTrue(snapshot.custom.isEmpty())
        assertNull(snapshot.issue)
    }

    @Test
    fun keepsCurrentVersionCustom() {
        val (repo, file) = repository()
        val custom = strategy("com.example.custom", hosts = listOf("custom.com"))
        file.parentFile?.mkdirs()
        file.writeText(ScanStrategyJson.render(listOf(custom), version = ScanStrategyJson.CURRENT_VERSION))
        val snapshot = repo.load()
        assertEquals(listOf(custom), snapshot.custom)
        assertNull(snapshot.issue)
    }

    @Test
    fun migratesLegacyMergedFileDroppingUntouchedOfficialCopies() {
        val (repo, file) = repository()
        val custom = strategy("com.example.custom", hosts = listOf("custom.com"))
        file.parentFile?.mkdirs()
        file.writeText(ScanStrategyJson.render(listOf(official, custom), version = 1))
        val snapshot = repo.load()
        assertEquals(listOf(custom), snapshot.custom)
        val rewritten = ScanStrategyJson.parse(file.readText())!!
        assertEquals(ScanStrategyJson.CURRENT_VERSION, rewritten.version)
        assertEquals(listOf("com.example.custom"), rewritten.strategies.map { it.packageName })
    }

    @Test
    fun keepsModifiedOfficialOverrideAsCustom() {
        val (repo, file) = repository()
        val override = strategy("com.example.official", hosts = listOf("changed.com"))
        file.parentFile?.mkdirs()
        file.writeText(ScanStrategyJson.render(listOf(official, override), version = 1))
        val snapshot = repo.load()
        assertEquals(listOf(override), snapshot.custom)
        assertEquals(2, snapshot.matching.size)
    }

    @Test
    fun restoreOfficialRemovesOverridesButKeepsCustomApps() {
        val (repo, file) = repository()
        val override = strategy("com.example.official", hosts = listOf("changed.com"))
        val custom = strategy("com.example.custom", hosts = listOf("custom.com"))
        file.parentFile?.mkdirs()
        file.writeText(ScanStrategyJson.render(listOf(override, custom)))
        val restored = repo.restoreOfficial(repo.load())
        assertEquals(listOf(custom), restored.custom)
        assertEquals(listOf(custom), repo.load().custom)
    }

    @Test
    fun reloadPicksUpExternalRewrite() {
        val (repo, file) = repository()
        file.parentFile?.mkdirs()
        file.writeText(ScanStrategyJson.render(listOf(strategy("com.example.first"))))
        assertEquals(listOf("com.example.first"), repo.load().custom.map { it.packageName })
        file.writeText(ScanStrategyJson.render(listOf(strategy("com.example.second"))))
        assertEquals(listOf("com.example.second"), repo.load().custom.map { it.packageName })
    }

    @Test
    fun importMigratesLegacyMergedContent() {
        val (repo, file) = repository()
        val custom = strategy("com.example.custom", hosts = listOf("custom.com"))
        val legacy = ScanStrategyJson.render(listOf(official, custom), version = 1)
        val result = repo.importCustom(legacy)
        assertNotNull(result.count)
        assertEquals(1, result.count)
        assertNull(result.issue)
        assertEquals(listOf(custom), repo.load().custom)
        assertTrue(file.isFile)
    }

    @Test
    fun importRejectsInvalidJson() {
        val (repo, _) = repository()
        val result = repo.importCustom("not json")
        assertNull(result.count)
        assertEquals(ScanStrategyIssue.CorruptCustom, result.issue)
    }

    @Test
    fun skippedEntriesReportIssue() {
        val (repo, file) = repository()
        file.parentFile?.mkdirs()
        file.writeText(
            """{"version":2,"apps":[{"package":""},{"package":"com.example.ok","scan":{"uri":"ok://scan"}}]}""",
        )
        val snapshot = repo.load()
        assertEquals(listOf("com.example.ok"), snapshot.custom.map { it.packageName })
        assertEquals(ScanStrategyIssue.SkippedEntries(1), snapshot.issue)
    }
}
