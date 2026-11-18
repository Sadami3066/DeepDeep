package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceToolPolicyTest {
    private val installed = setOf("a", "b", "c")

    @Test
    fun newBotStartsWithNothingEnabled() {
        val policy = WorkspaceToolPolicy()

        assertTrue(policy.enabledSkills(installed).isEmpty())
        assertTrue(policy.enabledPlugins(installed).isEmpty())
        assertTrue(policy.enabledMcpServers(installed).isEmpty())
        assertFalse(policy.screenTools)
        assertFalse(policy.miniappTools)
        assertTrue(policy.isEmpty)
    }

    @Test
    fun explicitSelectionOnlyEnablesChosenItems() {
        val policy = WorkspaceToolPolicy(skills = setOf("a"), plugins = setOf("b"), mcpServers = setOf("c"))

        assertEquals(setOf("a"), policy.enabledSkills(installed))
        assertEquals(setOf("b"), policy.enabledPlugins(installed))
        assertEquals(setOf("c"), policy.enabledMcpServers(installed))
    }

    @Test
    fun defaultAllEnablesEverythingExceptExcludedItems() {
        val policy = WorkspaceToolPolicy(allCapabilities = true, skills = setOf("b"))

        assertEquals(setOf("a", "c"), policy.enabledSkills(installed))
        assertEquals(installed, policy.enabledPlugins(installed))
    }

    @Test
    fun unknownInstalledIdsAreIgnored() {
        val policy = WorkspaceToolPolicy(skills = setOf("missing"))

        assertTrue(policy.enabledSkills(installed).isEmpty())
    }

    @Test
    fun toolSwitchesMakePolicyNonEmpty() {
        assertTrue(WorkspaceToolPolicy().isEmpty)
        assertFalse(WorkspaceToolPolicy(screenTools = true).isEmpty)
        assertFalse(WorkspaceToolPolicy(miniappTools = true).isEmpty)
    }

    @Test
    fun allCapabilitiesAloneIsNotEmpty() {
        assertFalse(WorkspaceToolPolicy(allCapabilities = true).isEmpty)
    }
}
