package com.hh.music.player.data.offline

import com.hh.music.player.data.Song
import java.io.File
import java.io.RandomAccessFile

/**
 * v1.7: writes ID3v2.3 metadata (title / artist / album) into downloaded MP3
 * files so they show up correctly in the local-music screen and any external
 * player. FLAC/m4a are left untouched (their tagging formats differ and a bad
 * write would corrupt playback — MP3 ID3 is append-safe at the file head).
 *
 * The frame-building logic is pure and JVM-testable; only [writeId3v2] touches
 * the filesystem.
 */
object MediaTagWriter {

    /** True when this extension is safe to tag. */
    fun supports(ext: String?): Boolean =
        ext?.lowercase() == "mp3"

    /**
     * Build an ID3v2.3 tag with TIT2 (title), TPE1 (artist), TALB (album).
     * Text frames use encoding byte 0x03 (UTF-8, valid in v2.4; widely accepted
     * by Android readers for v2.3 too) — simplest correct-enough approach.
     */
    fun buildId3v23(song: Song): ByteArray {
        val frames = ArrayList<ByteArray>()
        addTextFrame(frames, "TIT2", song.name)
        addTextFrame(frames, "TPE1", song.artistText)
        if (song.album.name.isNotBlank()) addTextFrame(frames, "TALB", song.album.name)
        val totalSize = frames.sumOf { it.size }
        // Header: "ID3" + version 2.3 + flags 0 + syncsafe size.
        val header = byteArrayOf(
            0x49, 0x44, 0x33, // "ID3"
            0x03, 0x00,       // version 2.3
            0x00,             // flags
            0x00, 0x00, 0x00, 0x00 // placeholder for size
        )
        writeSyncsafe(header, 6, totalSize)
        val out = ByteArray(header.size + totalSize)
        header.copyInto(out)
        var pos = header.size
        for (f in frames) {
            f.copyInto(out, pos)
            pos += f.size
        }
        return out
    }

    private fun addTextFrame(out: MutableList<ByteArray>, id: String, text: String) {
        if (text.isBlank()) return
        val payload = text.toByteArray(Charsets.UTF_8)
        val size = 1 + payload.size // encoding byte + text
        val frame = ByteArray(10 + size)
        // Frame id is ASCII, exactly 4 chars.
        val idBytes = id.toByteArray(Charsets.ISO_8859_1)
        idBytes.copyInto(frame)
        // Size as plain 32-bit big-endian (v2.3 does NOT use syncsafe here).
        frame[4] = ((size ushr 24) and 0xFF).toByte()
        frame[5] = ((size ushr 16) and 0xFF).toByte()
        frame[6] = ((size ushr 8) and 0xFF).toByte()
        frame[7] = (size and 0xFF).toByte()
        frame[9] = 0x03 // UTF-8 encoding
        payload.copyInto(frame, 10)
        out += frame
    }

    private fun writeSyncsafe(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value shr 21) and 0x7F).toByte()
        buf[offset + 1] = ((value shr 14) and 0x7F).toByte()
        buf[offset + 2] = ((value shr 7) and 0x7F).toByte()
        buf[offset + 3] = (value and 0x7F).toByte()
    }

    /**
     * Prepend the tag to [file] atomically: read old content, write tag+content
     * to a temp sibling, then swap. Any failure leaves the original intact.
     */
    fun writeId3v2(file: File, song: Song): Boolean = runCatching {
        if (!file.isFile || !file.canWrite()) return false
        val tmp = File(file.parentFile, file.name + ".tagtmp")
        RandomAccessFile(file, "r").use { src ->
            tmp.outputStream().use { dst ->
                dst.write(buildId3v23(song))
                src.channel.transferTo(0, src.length(), dst.channel)
            }
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            return false
        }
        true
    }.getOrDefault(false)

    /** True when the file already starts with an ID3 tag (skip re-tagging). */
    fun hasId3(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < 10) return false
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(3)
            raf.readFully(head)
            head[0] == 0x49.toByte() && head[1] == 0x44.toByte() && head[2] == 0x33.toByte()
        }
    }.getOrDefault(false)
}
