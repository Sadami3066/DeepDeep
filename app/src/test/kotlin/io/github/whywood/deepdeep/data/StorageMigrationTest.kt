package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageMigrationTest {
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
}
