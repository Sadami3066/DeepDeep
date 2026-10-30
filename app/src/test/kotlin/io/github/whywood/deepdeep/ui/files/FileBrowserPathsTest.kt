package io.github.whywood.deepdeep.ui.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileBrowserPathsTest {
    @Test
    fun childPathFromRootIsTheName() {
        assertEquals("docs", FileBrowserPaths.childPath("", "docs"))
    }

    @Test
    fun childPathAppendsToCurrent() {
        assertEquals("docs/a.md", FileBrowserPaths.childPath("docs", "a.md"))
    }

    @Test
    fun childPathAppendsAcrossMultipleLevels() {
        assertEquals(
            "docs/guides/intro.md",
            FileBrowserPaths.childPath("docs/guides", "intro.md"),
        )
    }

    @Test
    fun childPathWithEmptyNameKeepsCurrent() {
        assertEquals("docs", FileBrowserPaths.childPath("docs", ""))
        assertEquals("", FileBrowserPaths.childPath("", ""))
    }

    @Test
    fun childPathIgnoresSurroundingSlashes() {
        assertEquals("docs/a.md", FileBrowserPaths.childPath("/docs/", "a.md"))
        assertEquals("docs/a.md", FileBrowserPaths.childPath("/docs/", "/a.md/"))
    }

    @Test
    fun childPathCollapsesRepeatedSlashes() {
        assertEquals("a/b/c", FileBrowserPaths.childPath("a//b", "c"))
        assertEquals("a/b", FileBrowserPaths.childPath("//a//", "b"))
    }

    @Test
    fun parentPathOfRootIsRoot() {
        assertEquals("", FileBrowserPaths.parentPath(""))
        assertEquals("", FileBrowserPaths.parentPath("/"))
        assertEquals("", FileBrowserPaths.parentPath("///"))
    }

    @Test
    fun parentPathOfTopLevelEntryIsRoot() {
        assertEquals("", FileBrowserPaths.parentPath("docs"))
        assertEquals("", FileBrowserPaths.parentPath("docs/"))
    }

    @Test
    fun parentPathDropsLastSegment() {
        assertEquals("docs", FileBrowserPaths.parentPath("docs/guides"))
        assertEquals("docs/guides", FileBrowserPaths.parentPath("docs/guides/intro.md"))
    }

    @Test
    fun parentPathCollapsesRepeatedSlashes() {
        assertEquals("a", FileBrowserPaths.parentPath("a//b"))
        assertEquals("docs", FileBrowserPaths.parentPath("/docs/guides/"))
    }

    @Test
    fun parentPathUndoesChildPath() {
        assertEquals("", FileBrowserPaths.parentPath(FileBrowserPaths.childPath("", "docs")))
        assertEquals("docs", FileBrowserPaths.parentPath(FileBrowserPaths.childPath("docs", "a.md")))
        assertEquals(
            "docs/guides",
            FileBrowserPaths.parentPath(FileBrowserPaths.childPath("docs/guides", "intro.md")),
        )
    }
}
