package io.github.whywood.deepdeep.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDependenciesTest {
    @Test
    fun fakeImplementsEveryDependencyGroup() {
        val homeDir = File("/fake/home")
        val dependencies: AppDependencies = FakeAppDependencies(homeDir = homeDir)

        assertSame(homeDir, dependencies.homeDir)
        assertTrue(dependencies is RuntimeDependencies)
        assertTrue(dependencies is StorageDependencies)
        assertTrue(dependencies is FeatureDependencies)
        assertTrue(dependencies is ServiceDependencies)
    }

    @Test
    fun fakeFailsFastOnMembersItDoesNotProvide() {
        val dependencies: AppDependencies = FakeAppDependencies()

        val error = runCatching { dependencies.host }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun resolveAttachmentRootUsesConversationCwdWhenPresent() {
        val homeDir = File("/fake/home")

        assertEquals(
            File("/fake/workspaces/project-a"),
            resolveAttachmentRoot("/fake/workspaces/project-a", homeDir),
        )
    }

    @Test
    fun resolveAttachmentRootFallsBackToHomeWhenCwdIsBlank() {
        val homeDir = File("/fake/home")

        assertSame(homeDir, resolveAttachmentRoot("", homeDir))
        assertSame(homeDir, resolveAttachmentRoot("   ", homeDir))
    }
}
