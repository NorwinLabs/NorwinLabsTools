package com.example.norwinlabstools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry is now the only place a tool is declared, so these invariants are what stop a
 * bad entry from silently breaking Home (a duplicate id shadowing a tool, a saved layout
 * failing to restore, or a section quietly sorting to the bottom).
 */
class ToolRegistryTest {

    @Test
    fun `tool ids are unique`() {
        val duplicates = ToolRegistry.all
            .groupBy { it.id }
            .filter { (_, tools) -> tools.size > 1 }
            .keys
        assertTrue("Duplicate tool ids: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `tool names are unique`() {
        val duplicates = ToolRegistry.all
            .groupBy { it.name }
            .filter { (_, tools) -> tools.size > 1 }
            .keys
        assertTrue("Duplicate tool names: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `byId resolves every registered tool`() {
        ToolRegistry.all.forEach { tool ->
            assertEquals(tool, ToolRegistry.byId(tool.id))
        }
    }

    @Test
    fun `byId returns null for an unknown id`() {
        val unknown = (ToolRegistry.all.maxOf { it.id }) + 1
        assertNull(ToolRegistry.byId(unknown))
    }

    @Test
    fun `byIds preserves the saved order and drops unknown ids`() {
        val unknown = (ToolRegistry.all.maxOf { it.id }) + 1
        val requested = listOf(ToolRegistry.all[2].id, unknown, ToolRegistry.all[0].id)

        val resolved = ToolRegistry.byIds(requested)

        assertEquals(listOf(ToolRegistry.all[2], ToolRegistry.all[0]), resolved)
    }

    @Test
    fun `every category is listed in categoryOrder`() {
        val unordered = ToolRegistry.all
            .map { it.category }
            .distinct()
            .filterNot { it in ToolRegistry.categoryOrder }
        assertTrue("Categories missing from categoryOrder: $unordered", unordered.isEmpty())
    }

    @Test
    fun `no tool is left unreachable`() {
        val unreachable = ToolRegistry.all.filter { it.action == ToolAction.ComingSoon }
        assertTrue("Tools with no action wired up: ${unreachable.map { it.name }}", unreachable.isEmpty())
    }
}
