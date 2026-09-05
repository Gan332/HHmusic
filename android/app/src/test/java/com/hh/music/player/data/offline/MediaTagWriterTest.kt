package com.hh.music.player.data.offline

import com.hh.music.player.data.Album
import com.hh.music.player.data.Artist
import com.hh.music.player.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v1.7: ID3v2.3 tag building for downloaded MP3s. */
class MediaTagWriterTest {

    private fun song() = Song(
        id = 42,
        name = "晴天",
        artists = listOf(Artist(1, "周杰伦")),
        album = Album(11, "叶惠美")
    )

    @Test
    fun `only mp3 is supported`() {
        assertTrue(MediaTagWriter.supports("mp3"))
        assertTrue(MediaTagWriter.supports("MP3"))
        assertFalse(MediaTagWriter.supports("flac"))
        assertFalse(MediaTagWriter.supports("m4a"))
        assertFalse(MediaTagWriter.supports(null))
    }

    @Test
    fun `tag starts with ID3 header version 2_3`() {
        val tag = MediaTagWriter.buildId3v23(song())

        assertEquals('I'.code.toByte(), tag[0])
        assertEquals('D'.code.toByte(), tag[1])
        assertEquals('3'.code.toByte(), tag[2])
        assertEquals(0x03, tag[3].toInt()) // major version 2.3
        assertEquals(0x00, tag[4].toInt()) // revision
        assertEquals(0x00, tag[5].toInt()) // flags
    }

    @Test
    fun `syncsafe size matches frame payload length`() {
        val tag = MediaTagWriter.buildId3v23(song())

        val size = ((tag[6].toInt() and 0x7F) shl 21) or
            ((tag[7].toInt() and 0x7F) shl 14) or
            ((tag[8].toInt() and 0x7F) shl 7) or
            (tag[9].toInt() and 0x7F)

        assertEquals(tag.size - 10, size)
    }

    @Test
    fun `tag contains title artist and album frames`() {
        val tag = MediaTagWriter.buildId3v23(song())
        val text = String(tag, Charsets.ISO_8859_1)

        assertTrue(text.contains("TIT2"))
        assertTrue(text.contains("TPE1"))
        assertTrue(text.contains("TALB"))
        // UTF-8 payload decodes to the expected values.
        val utf8 = String(tag, Charsets.UTF_8)
        assertTrue(utf8.contains("晴天"))
        assertTrue(utf8.contains("周杰伦"))
        assertTrue(utf8.contains("叶惠美"))
    }

    @Test
    fun `blank album is omitted`() {
        val noAlbum = song().copy(album = Album(0, ""))
        val tag = MediaTagWriter.buildId3v23(noAlbum)
        val text = String(tag, Charsets.ISO_8859_1)

        assertTrue(text.contains("TIT2"))
        assertFalse(text.contains("TALB"))
    }

    @Test
    fun `writeId3v2 prepends tag and preserves audio bytes`() {
        val dir = createTempDir()
        val file = File(dir, "42.mp3")
        val audio = ByteArray(2048) { (it % 251).toByte() }
        file.writeBytes(audio)

        val ok = MediaTagWriter.writeId3v2(file, song())

        assertTrue(ok)
        val bytes = file.readBytes()
        // Tag prefix + original audio untouched: prefix length == a freshly
        // built tag (10-byte header included).
        assertEquals(bytes.size - audio.size, MediaTagWriter.buildId3v23(song()).size)
        assertTrue(bytes.copyOfRange(bytes.size - audio.size, bytes.size).contentEquals(audio))
        assertTrue(MediaTagWriter.hasId3(file))
        // Temp file cleaned up.
        assertFalse(File(dir, "42.mp3.tagtmp").exists())
        dir.deleteRecursively()
    }

    @Test
    fun `hasId3 detects existing tag`() {
        val dir = createTempDir()
        val tagged = File(dir, "tagged.mp3")
        tagged.writeBytes(MediaTagWriter.buildId3v23(song()) + ByteArray(64))
        val plain = File(dir, "plain.mp3")
        plain.writeBytes(ByteArray(64))

        assertTrue(MediaTagWriter.hasId3(tagged))
        assertFalse(MediaTagWriter.hasId3(plain))
        dir.deleteRecursively()
    }
}
