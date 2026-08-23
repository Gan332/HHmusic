package com.hh.music.player.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.7: playlist plaza parsing (top/playlist pages + catlist). */
class PlazaParserTest {

    @Test
    fun `plaza page parses playlists total and more`() {
        val root = JSONObject(
            """
            {
              "total": 61,
              "more": true,
              "playlists": [
                {
                  "id": 101,
                  "name": "华语热歌",
                  "coverImgUrl": "https://p.music.163.com/c1.jpg",
                  "playCount": 123456789,
                  "creator": {"id": 9, "nickname": "云音乐官方"}
                },
                {
                  "id": 102,
                  "name": "深夜学习",
                  "picUrl": "https://p.music.163.com/c2.jpg",
                  "playcount": 20000
                }
              ]
            }
            """.trimIndent()
        )

        val page = NcmParser.playlistPlazaPage(root)

        assertTrue(page.more)
        assertEquals(61, page.total)
        assertEquals(2, page.list.size)
        assertEquals("华语热歌", page.list[0].name)
        assertEquals("https://p.music.163.com/c1.jpg", page.list[0].picUrl)
        assertEquals(123456789L, page.list[0].playcount)
        assertEquals("云音乐官方", page.list[0].creatorName)
        // picUrl fallback when coverImgUrl is absent.
        assertEquals("https://p.music.163.com/c2.jpg", page.list[1].picUrl)
    }

    @Test
    fun `plaza page skips rows without valid id`() {
        val root = JSONObject(
            """
            {"playlists": [{"id": 0, "name": "bad"}, {"id": 5, "name": "good"}]}
            """.trimIndent()
        )

        val page = NcmParser.playlistPlazaPage(root)

        assertEquals(1, page.list.size)
        assertEquals("good", page.list[0].name)
    }

    @Test
    fun `empty or missing arrays yield empty page`() {
        val page = NcmParser.playlistPlazaPage(JSONObject("{}"))
        assertTrue(page.list.isEmpty())
        assertFalse(page.more)

        val empty = NcmParser.plazaPlaylists(JSONObject("""{"playlists": []}"""))
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `categories preserve upstream grouping order and append unknown names`() {
        val root = JSONObject(
            """
            {
              "categories": {"0": "语种", "1": "风格"},
              "sub": [
                {"name": "华语", "category": 0},
                {"name": "欧美", "category": 0},
                {"name": "流行", "category": 1},
                {"name": "摇滚", "category": 1}
              ]
            }
            """.trimIndent()
        )

        val cats = NcmParser.plazaCategories(root)

        // Group headers first (sorted by key), then flat sub names.
        assertEquals(listOf("语种", "风格", "华语", "欧美", "流行", "摇滚"), cats.map { it.name })
    }

    @Test
    fun `categories fall back to sub names only`() {
        val root = JSONObject(
            """
            {"sub": [{"name": "华语", "category": 0}, {"name": "流行", "category": 1}]}
            """.trimIndent()
        )

        val cats = NcmParser.plazaCategories(root)

        assertEquals(listOf("华语", "流行"), cats.map { it.name })
    }
}
