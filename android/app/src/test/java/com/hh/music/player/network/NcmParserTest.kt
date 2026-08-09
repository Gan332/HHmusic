package com.hh.music.player.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NcmParserTest {

    @Test
    fun `artist search page keeps artist count and img1v1 fallback url`() {
        val root = JSONObject(
            """
            {
              "result": {
                "artistCount": 2,
                "artists": [
                  {"id": 1, "name": "周杰伦", "img1v1Url": "https://p.music.163.com/a.jpg"},
                  {"id": 2, "name": "林俊杰", "picUrl": "https://p.music.163.com/b.jpg"}
                ]
              }
            }
            """.trimIndent()
        )

        val page = NcmParser.artistSearchPage(root)

        assertEquals(2, page.total)
        assertEquals(2, page.artists.size)
        assertEquals("周杰伦", page.artists[0].name)
        assertEquals("https://p.music.163.com/a.jpg", page.artists[0].picUrl)
        assertEquals("林俊杰", page.artists[1].name)
        assertEquals("https://p.music.163.com/b.jpg", page.artists[1].picUrl)
    }

    @Test
    fun `artist songs page normalizes tracks and total`() {
        val root = JSONObject(
            """
            {
              "total": 3,
              "songs": [
                {
                  "id": 1,
                  "name": "晴天",
                  "ar": [{"id": 11, "name": "周杰伦"}],
                  "al": {"id": 111, "name": "叶惠美", "picUrl": "cover"},
                  "dt": 269000,
                  "fee": 0
                }
              ]
            }
            """.trimIndent()
        )

        val page = NcmParser.artistSongsPage(root)

        assertEquals(3, page.total)
        assertEquals(1, page.songs.size)
        assertEquals("晴天", page.songs[0].name)
        assertEquals("周杰伦", page.songs[0].artistText)
        assertEquals(269_000L, page.songs[0].duration)
        assertEquals("cover", page.songs[0].coverUrl)
    }

    @Test
    fun `song search page falls back to result total`() {
        val root = JSONObject(
            """
            {
              "result": {
                "songCount": 12,
                "songs": [
                  {"id": 5, "name": "夜曲", "ar": [{"name": "周杰伦"}], "al": {"name": "11月的萧邦"}}
                ]
              }
            }
            """.trimIndent()
        )

        val page = NcmParser.searchPage(root)

        assertEquals(12, page.total)
        assertEquals(1, page.songs.size)
        assertEquals("夜曲", page.songs[0].name)
        assertTrue(page.songs[0].artists.first().name == "周杰伦")
    }
}
