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

    @Test
    fun `artist albums page reads hotAlbums with pic fallback and more flag`() {
        val root = JSONObject(
            """
            {
              "more": true,
              "hotAlbums": [
                {"id": 111, "name": "叶惠美", "picUrl": "https://p.music.163.com/al.jpg", "publishTime": 1056969600000, "size": 11},
                {"id": 222, "name": "无封面专辑", "blurPicUrl": "https://p.music.163.com/blur.jpg", "publishTime": 0, "size": 10},
                {"id": 0, "name": "无效项应被丢弃"}
              ],
              "artist": {"id": 42, "name": "周杰伦"}
            }
            """.trimIndent()
        )

        val page = NcmParser.artistAlbumsPage(root)

        assertTrue(page.more)
        assertEquals(2, page.albums.size)
        assertEquals("叶惠美", page.albums[0].name)
        assertEquals("https://p.music.163.com/al.jpg", page.albums[0].picUrl)
        assertEquals(1056969600000L, page.albums[0].publishTime)
        assertEquals(11, page.albums[0].songCount)
        assertEquals("https://p.music.163.com/blur.jpg", page.albums[1].picUrl)
    }

    @Test
    fun `album detail parses album object and song list`() {
        val root = JSONObject(
            """
            {
              "album": {
                "id": 18879,
                "name": "叶惠美",
                "picUrl": "https://p.music.163.com/cover.jpg",
                "publishTime": 1056969600000,
                "description": "第四张创作专辑"
              },
              "songs": [
                {
                  "id": 185811,
                  "name": "以父之名",
                  "ar": [{"id": 42, "name": "周杰伦"}],
                  "al": {"id": 18879, "name": "叶惠美"},
                  "dt": 342000
                }
              ]
            }
            """.trimIndent()
        )

        val detail = NcmParser.albumDetail(root)

        assertEquals(18879, detail.id)
        assertEquals("叶惠美", detail.name)
        assertEquals("https://p.music.163.com/cover.jpg", detail.coverImgUrl)
        assertEquals("第四张创作专辑", detail.description)
        assertEquals(1056969600000L, detail.publishTime)
        assertEquals(1, detail.songs.size)
        assertEquals("以父之名", detail.songs[0].name)
    }

    @Test
    fun `user playlists normalizes rows and flags the liked-songs list`() {
        val root = JSONObject(
            """
            {
              "code": 200,
              "playlist": [
                {
                  "id": 111,
                  "name": "我喜欢的音乐",
                  "coverImgUrl": "https://p.music.163.com/liked.jpg",
                  "trackCount": 42,
                  "specialType": 5,
                  "creator": {"userId": 9, "nickname": "我"}
                },
                {
                  "id": 222,
                  "name": "歌单2",
                  "coverImgUrl": null,
                  "creator": {"userId": 9, "nickname": "我"}
                },
                {"id": 0, "name": "无效项应被丢弃"},
                {"name": "没有 id 也丢弃"}
              ]
            }
            """.trimIndent()
        )

        val rows = NcmParser.userPlaylists(root)

        assertEquals(2, rows.size)
        assertEquals(111L, rows[0].id)
        assertEquals("我喜欢的音乐", rows[0].name)
        assertTrue(rows[0].isLikedSongs)
        assertEquals(9L, rows[0].creator?.id)
        assertEquals("我", rows[0].creator?.nickname)
        assertEquals(222L, rows[1].id)
        assertEquals(null, rows[1].coverImgUrl)
        // specialType absent -> 0 -> not the liked-songs list.
        assertTrue(!rows[1].isLikedSongs)
    }

    @Test
    fun `user playlists tolerates missing playlist array and blank covers`() {
        assertTrue(NcmParser.userPlaylists(JSONObject("{\"code\":200}")).isEmpty())
        // Empty object: no playlist array at all.
        assertTrue(NcmParser.userPlaylists(JSONObject()).isEmpty())

        val weird = NcmParser.userPlaylists(
            JSONObject(
                """
                {"playlist": [
                  {"id": 7, "name": "n", "coverImgUrl": ""},
                  {"id": 8, "name": "m", "coverImgUrl": "null"}
                ]}
                """.trimIndent()
            )
        )
        assertEquals(2, weird.size)
        assertEquals(null, weird[0].coverImgUrl)
        assertEquals(null, weird[1].coverImgUrl)
    }
}
