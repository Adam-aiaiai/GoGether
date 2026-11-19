package com.gogether.app

import com.gogether.app.core.ui.displayTime
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Locale

class EnglishUiCopyTest {
    @Test fun appAndTestFixturesDoNotContainChineseLiteralsOrResources() {
        val source = listOf(File("src"), File("app/src")).firstOrNull { File(it, "main").isDirectory }
        assertNotNull("Cannot locate source for the English-copy audit", source)
        val han = Regex("[\\p{IsHan}]")
        val strings = Regex("\"(?:\\\\.|[^\"\\\\])*\"")
        val violations = source!!.walkTopDown().filter { it.isFile && it.extension in listOf("kt", "xml") }
            .flatMap { file ->
                val text = file.readText()
                val candidates = if (file.extension == "kt") strings.findAll(text).map { it.value }
                    else sequenceOf(text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), ""))
                candidates.filter { han.containsMatchIn(it) }.map { "${file.relativeTo(source)}: $it" }
            }.toList()
        assertTrue("Non-English app or test copy:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test fun eventDatesStayEnglishWhenDeviceLocaleIsChinese() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            val date = displayTime(Instant.parse("2026-11-19T12:00:00Z").toEpochMilli())
            assertTrue(date, date.contains("Nov"))
            assertFalse(date, Regex("[\\p{IsHan}]").containsMatchIn(date))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
