package io.github.whywood.deepdeep.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageMigrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun leavesWorkspacePathsUntouched() {
        assertEquals(
            "/data/user/0/io.github.whywood.deepdeep/files/workspaces/abc",
            StorageCwdMigration.migrate("/data/user/0/io.github.whywood.deepdeep/files/workspaces/abc"),
        )
    }

    @Test
    fun migratesLegacyWorkspacePathWithoutEatingWorkspacesPrefix() {
        assertEquals(
            "/data/user/0/io.github.whywood.deepdeep/files/home",
            StorageCwdMigration.migrate("/data/user/0/io.github.whywood.deepdeep/files/workspace"),
        )
        assertEquals(
            "/data/user/0/io.github.whywood.deepdeep/files/home/project",
            StorageCwdMigration.migrate("/data/user/0/io.github.whywood.deepdeep/files/workspace/project"),
        )
    }

    @Test
    fun migratesLegacyProjectsPath() {
        assertEquals(
            "/data/user/0/io.github.whywood.deepdeep/files/workspaces/abc",
            StorageCwdMigration.migrate("/data/user/0/io.github.whywood.deepdeep/files/projects/abc"),
        )
    }

    @Test
    fun repairsHomesPathWrittenByOldPrefixBug() {
        assertEquals(
            "/data/user/0/io.github.whywood.deepdeep/files/workspaces/abc",
            StorageCwdMigration.migrate("/data/user/0/io.github.whywood.deepdeep/files/homes/abc"),
        )
    }

    @Test
    fun writesCompletionMarkerAndSkipsNextRun() {
        val files = temporaryFolder.newFolder("files-marker")
        File(files, "workspace").mkdirs()

        StorageMigration.migrate(files)

        assertFalse(File(files, "workspace").exists())
        assertTrue(File(files, "home").isDirectory)
        assertTrue(File(files, ".storage-migrated").isFile)

        File(files, "workspace").mkdirs()
        StorageMigration.migrate(files)

        assertTrue(File(files, "workspace").isDirectory)
        assertTrue(File(files, "home").isDirectory)
    }

    @Test
    fun rerunsWhenMarkerVersionDiffers() {
        val files = temporaryFolder.newFolder("files-version")
        File(files, ".storage-migrated").writeText("0")
        File(files, "workspace").mkdirs()

        StorageMigration.migrate(files)

        assertFalse(File(files, "workspace").exists())
        assertTrue(File(files, "home").isDirectory)
    }

    @Test
    fun replacesEmptyHomeCreatedBeforeMigration() {
        val files = temporaryFolder.newFolder("files-race")
        File(files, "workspace").mkdirs()
        File(files, "workspace/old.txt").writeText("old")
        File(files, "home").mkdirs()

        StorageMigration.migrate(files)

        assertFalse(File(files, "workspace").exists())
        assertTrue(File(files, "home/old.txt").isFile)
    }
}
