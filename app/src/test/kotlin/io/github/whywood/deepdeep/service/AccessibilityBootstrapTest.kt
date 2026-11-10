package io.github.whywood.deepdeep.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityBootstrapTest {
    private val component = "io.github.whywood.deepdeep/io.github.whywood.deepdeep.service.GuiAccessibilityService"
    private val shortComponent = "io.github.whywood.deepdeep/.service.GuiAccessibilityService"

    @Test
    fun mergeAddsComponentToEmptySettings() {
        assertEquals(component, mergeAccessibilityServices(null, component))
        assertEquals(component, mergeAccessibilityServices("", component))
        assertEquals(component, mergeAccessibilityServices("  ", component))
    }

    @Test
    fun mergeKeepsExistingServices() {
        assertEquals(
            "com.example.one/.ServiceOne:com.example.two/com.example.two.ServiceTwo:$component",
            mergeAccessibilityServices(
                "com.example.one/.ServiceOne:com.example.two/com.example.two.ServiceTwo",
                component,
            ),
        )
    }

    @Test
    fun mergeDoesNotDuplicateComponent() {
        assertEquals(
            "com.example.one/.ServiceOne:$component",
            mergeAccessibilityServices("com.example.one/.ServiceOne:$component", component),
        )
        assertEquals(
            "com.example.one/.ServiceOne:$shortComponent",
            mergeAccessibilityServices("com.example.one/.ServiceOne:$shortComponent", component),
        )
    }

    @Test
    fun mergeNormalizesBlankEntries() {
        assertEquals(
            "com.example.one/.ServiceOne:$component",
            mergeAccessibilityServices(" com.example.one/.ServiceOne :: ", component),
        )
    }

    @Test
    fun mergeKeepsInvalidEntriesVerbatim() {
        assertEquals(
            "garbage:$component",
            mergeAccessibilityServices("garbage", component),
        )
    }

    @Test
    fun parseReadsBothSettings() {
        assertEquals(
            AccessibilitySettingsSnapshot(
                enabledServices = "com.example/.Service",
                accessibilityEnabled = 1,
            ),
            parseAccessibilitySettings("S=com.example/.Service\nE=1\n"),
        )
        assertEquals(
            AccessibilitySettingsSnapshot(enabledServices = null, accessibilityEnabled = 0),
            parseAccessibilitySettings("S=null\nE=0\n"),
        )
    }

    @Test
    fun parseRejectsIncompleteOutput() {
        assertNull(parseAccessibilitySettings(""))
        assertNull(parseAccessibilitySettings("S=com.example/.Service\n"))
        assertNull(parseAccessibilitySettings("E=1\n"))
    }

    @Test
    fun planIsEmptyWhenAlreadyConfigured() {
        assertNull(planAccessibilityBootstrap(component, component, 1))
        assertNull(planAccessibilityBootstrap(component, "com.example/.Service:$shortComponent", 1))
    }

    @Test
    fun planWritesServicesOnly() {
        val plan = planAccessibilityBootstrap(component, "com.example/.Service", 1)
        assertEquals(
            AccessibilityBootstrapPlan(services = "com.example/.Service:$component", enableAccessibility = false),
            plan,
        )
    }

    @Test
    fun planEnablesAccessibilityWhenMissing() {
        val plan = planAccessibilityBootstrap(component, component, 0)
        assertEquals(AccessibilityBootstrapPlan(services = null, enableAccessibility = true), plan)
        assertEquals(
            AccessibilityBootstrapPlan(services = null, enableAccessibility = true),
            planAccessibilityBootstrap(component, component, null),
        )
    }

    @Test
    fun planCoversBothFieldsAtOnce() {
        val plan = planAccessibilityBootstrap(component, "com.example/.Service", 0)
        assertEquals(
            AccessibilityBootstrapPlan(
                services = "com.example/.Service:$component",
                enableAccessibility = true,
            ),
            plan,
        )
    }

    @Test
    fun readCommandQueriesBothSettings() {
        val command = accessibilityReadCommand()
        assertTrue(command.contains("settings get secure enabled_accessibility_services"))
        assertTrue(command.contains("settings get secure accessibility_enabled"))
    }

    @Test
    fun writeCommandChainsOnlyNeededLines() {
        assertEquals(
            "settings put secure enabled_accessibility_services 'com.example/.Service:$component'",
            accessibilityWriteCommand(
                AccessibilityBootstrapPlan(services = "com.example/.Service:$component", enableAccessibility = false),
            ),
        )
        assertEquals(
            "settings put secure accessibility_enabled 1",
            accessibilityWriteCommand(AccessibilityBootstrapPlan(services = null, enableAccessibility = true)),
        )
        assertEquals(
            "settings put secure enabled_accessibility_services 'com.example/.Service' && " +
                "settings put secure accessibility_enabled 1",
            accessibilityWriteCommand(
                AccessibilityBootstrapPlan(services = "com.example/.Service", enableAccessibility = true),
            ),
        )
    }

    @Test
    fun writeCommandQuotesServicesValue() {
        assertEquals("'a b'", shellQuote("a b"))
        assertEquals("'it'\\''s'", shellQuote("it's"))
    }

    @Test
    fun rebindPlanNeedsOwnService() {
        assertNull(planAccessibilityRebind(component, null))
        assertNull(planAccessibilityRebind(component, "com.example/.Service"))
    }

    @Test
    fun rebindPlanDropsOwnServiceFromList() {
        assertEquals(
            AccessibilityRebindPlan(
                withoutService = "com.example/.Service",
                withService = "com.example/.Service:$component",
            ),
            planAccessibilityRebind(component, "com.example/.Service:$component"),
        )
        assertEquals(
            AccessibilityRebindPlan(
                withoutService = "com.example/.Service",
                withService = "com.example/.Service:$shortComponent",
            ),
            planAccessibilityRebind(component, "com.example/.Service:$shortComponent"),
        )
    }

    @Test
    fun rebindPlanDeletesSettingWhenOwnServiceIsTheOnlyOne() {
        assertEquals(
            AccessibilityRebindPlan(withoutService = null, withService = component),
            planAccessibilityRebind(component, component),
        )
    }

    @Test
    fun rebindCommandTogglesServiceAndEnablesAccessibility() {
        assertEquals(
            "settings delete secure enabled_accessibility_services && sleep 1 && " +
                "settings put secure enabled_accessibility_services 'com.example/.Service:$component' && " +
                "settings put secure accessibility_enabled 1",
            accessibilityRebindCommand(
                AccessibilityRebindPlan(
                    withoutService = null,
                    withService = "com.example/.Service:$component",
                ),
            ),
        )
        assertEquals(
            "settings put secure enabled_accessibility_services 'com.example/.Service' && sleep 1 && " +
                "settings put secure enabled_accessibility_services 'com.example/.Service:$component' && " +
                "settings put secure accessibility_enabled 1",
            accessibilityRebindCommand(
                AccessibilityRebindPlan(
                    withoutService = "com.example/.Service",
                    withService = "com.example/.Service:$component",
                ),
            ),
        )
    }
}
