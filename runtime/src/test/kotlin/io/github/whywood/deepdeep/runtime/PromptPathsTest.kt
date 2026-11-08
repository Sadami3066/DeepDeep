package io.github.whywood.deepdeep.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPathsTest {
    @Test
    fun pathsSectionDescribesSkillsAndAppData() {
        val section = buildPathsSection(
            skillsDir = "/data/app/skills",
            appDataDir = "/data/app",
            sharedStorage = false,
            mounts = emptyList(),
        )

        assertTrue(section.contains("Skills: /data/app/skills."))
        assertTrue(section.contains("App data: /data/app."))
        assertFalse(section.contains("Shared storage"))
        assertFalse(section.contains("Mounted directories"))
    }

    @Test
    fun sharedStorageLineOnlyAppearsWhenEnabled() {
        val enabled = buildPathsSection("/s", "/a", sharedStorage = true, mounts = emptyList())
        val disabled = buildPathsSection("/s", "/a", sharedStorage = false, mounts = emptyList())

        assertTrue(enabled.contains("Shared storage: /storage/emulated/0"))
        assertFalse(disabled.contains("Shared storage"))
    }

    @Test
    fun mountsAreListedWithNamePathAndDescription() {
        val section = buildPathsSection(
            skillsDir = "/s",
            appDataDir = "/a",
            sharedStorage = false,
            mounts = listOf(
                StorageMountInfo("Obsidian", "我的笔记仓库", "/storage/emulated/0/Obsidian"),
                StorageMountInfo("Photos", "", "/storage/emulated/0/DCIM/Camera"),
            ),
        )

        assertTrue(section.contains("Mounted directories:"))
        assertTrue(section.contains("- Obsidian (/storage/emulated/0/Obsidian): 我的笔记仓库"))
        assertTrue(section.contains("- Photos (/storage/emulated/0/DCIM/Camera)"))
        assertFalse(section.contains("Camera): "))
    }
}
