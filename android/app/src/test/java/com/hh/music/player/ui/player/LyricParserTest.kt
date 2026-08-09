package com.hh.music.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricParserTest {

    @Test
    fun `empty and blank lyrics yield no lines`() {
        assertTrue(LyricParser.parse(null).isEmpty())
        assertTrue(LyricParser.parse("").isEmpty())
        assertTrue(LyricParser.parse("   \n\n").isEmpty())
    }

    @Test
    fun `long lyrics retain order and timing`() {
        val raw = """
            [00:00.00]灯光也暗了
            [00:05.50]音乐低声了
            [00:10.30]口中的棉花糖也融化了
            [03:45.10]回到最初的美好
        """.trimIndent()
        val lines = LyricParser.parse(raw)
        assertEquals(4, lines.size)
        assertEquals(0L, lines[0].timeMs)
        assertEquals("灯光也暗了", lines[0].text)
        assertEquals(5_500L, lines[1].timeMs)
        assertEquals(10_300L, lines[2].timeMs)
        assertEquals(225_100L, lines[3].timeMs)
        assertTrue(lines.map { it.timeMs }.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `multiple time tags on one line expand to several lines`() {
        val lines = LyricParser.parse("[00:01.00][00:30.00][01:00.00]重复的副歌")
        assertEquals(3, lines.size)
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals(30_000L, lines[1].timeMs)
        assertEquals(60_000L, lines[2].timeMs)
        assertTrue(lines.all { it.text == "重复的副歌" })
    }

    @Test
    fun `header metadata and non-tagged garbage are skipped`() {
        val lines = LyricParser.parse(
            "[ti:晴天]\n[ar:周杰伦]\n[al:叶惠美]\n故事的小黄花\n[00:12.00]从出生那年就飘着"
        )
        assertEquals(1, lines.size)
        assertEquals("从出生那年就飘着", lines[0].text)
        assertEquals(12_000L, lines[0].timeMs)
    }

    @Test
    fun `translation map is keyed by exact time`() {
        val t = LyricParser.translations("[00:12.00]飘啊飘\n[00:30.12]没有回答")
        assertEquals(2, t.size)
        assertEquals("飘啊飘", t[12_000L])
        assertEquals("没有回答", t[30_120L])
    }

    @Test
    fun `romanization map is keyed by exact time`() {
        val r = LyricParser.romanizations("[00:03.00]piao a piao\n[01:02.50]mei you hui da")
        assertEquals(2, r.size)
        assertEquals("piao a piao", r[3_000L])
        assertEquals("mei you hui da", r[62_500L])
    }

    @Test
    fun `fractional seconds with one or two digits scale correctly`() {
        // ".5" = 500ms, ".05" = 50ms, ".1" = 100ms
        assertEquals(500L, LyricParser.parse("[00:00.5]a")[0].timeMs)
        assertEquals(50L, LyricParser.parse("[00:00.05]a")[0].timeMs)
        assertEquals(100L, LyricParser.parse("[00:00.1]a")[0].timeMs)
    }
}
