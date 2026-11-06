package io.github.whywood.deepdeep.ui.miniapp

import io.github.whywood.deepdeep.runtime.MiniappCreation
import io.github.whywood.deepdeep.runtime.MiniappInvalid
import io.github.whywood.deepdeep.runtime.MiniappInvalidReason
import io.github.whywood.deepdeep.runtime.MiniappManifest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniappFilterTest {
    private fun creation(id: String, name: String, description: String = "") = MiniappCreation(
        manifest = MiniappManifest(id = id, name = name, description = description),
        directory = File("/tmp/$id"),
    )

    private fun invalid(id: String) = MiniappInvalid(
        id = id,
        directory = File("/tmp/$id"),
        reason = MiniappInvalidReason.MANIFEST_INVALID,
        detail = null,
    )

    private val creations = listOf(
        creation("todo-app", "Todo", "A small todo board"),
        creation("weather", "Weather", "Forecast viewer"),
    )
    private val invalids = listOf(invalid("broken-widget"), invalid("todo-broken"))

    @Test
    fun filtersCreationsByNameIdDescriptionAndInvalidById() {
        assertEquals(listOf("todo-app"), filterMiniapp(creations, invalids, "todo board").creations.map { it.manifest.id })
        assertEquals(listOf("weather"), filterMiniapp(creations, invalids, "WEATHER").creations.map { it.manifest.id })
        assertEquals(listOf("weather"), filterMiniapp(creations, invalids, "weather").creations.map { it.manifest.id })
        assertEquals(listOf("todo-broken"), filterMiniapp(creations, invalids, "todo-broken").invalid.map { it.id })
        assertEquals(creations, filterMiniapp(creations, invalids, "").creations)
        assertEquals(invalids, filterMiniapp(creations, invalids, "").invalid)
        assertEquals(emptyList<MiniappCreation>(), filterMiniapp(creations, invalids, "missing").creations)
    }
}
