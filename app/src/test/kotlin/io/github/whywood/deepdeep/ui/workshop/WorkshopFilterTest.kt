package io.github.whywood.deepdeep.ui.workshop

import io.github.whywood.deepdeep.runtime.WorkshopCreation
import io.github.whywood.deepdeep.runtime.WorkshopInvalid
import io.github.whywood.deepdeep.runtime.WorkshopInvalidReason
import io.github.whywood.deepdeep.runtime.WorkshopManifest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkshopFilterTest {
    private fun creation(id: String, name: String, description: String = "") = WorkshopCreation(
        manifest = WorkshopManifest(id = id, name = name, description = description),
        directory = File("/tmp/$id"),
    )

    private fun invalid(id: String) = WorkshopInvalid(
        id = id,
        directory = File("/tmp/$id"),
        reason = WorkshopInvalidReason.MANIFEST_INVALID,
        detail = null,
    )

    private val creations = listOf(
        creation("todo-app", "Todo", "A small todo board"),
        creation("weather", "Weather", "Forecast viewer"),
    )
    private val invalids = listOf(invalid("broken-widget"), invalid("todo-broken"))

    @Test
    fun filtersCreationsByNameIdDescriptionAndInvalidById() {
        assertEquals(listOf("todo-app"), filterWorkshop(creations, invalids, "todo board").creations.map { it.manifest.id })
        assertEquals(listOf("weather"), filterWorkshop(creations, invalids, "WEATHER").creations.map { it.manifest.id })
        assertEquals(listOf("weather"), filterWorkshop(creations, invalids, "weather").creations.map { it.manifest.id })
        assertEquals(listOf("todo-broken"), filterWorkshop(creations, invalids, "todo-broken").invalid.map { it.id })
        assertEquals(creations, filterWorkshop(creations, invalids, "").creations)
        assertEquals(invalids, filterWorkshop(creations, invalids, "").invalid)
        assertEquals(emptyList<WorkshopCreation>(), filterWorkshop(creations, invalids, "missing").creations)
    }
}
