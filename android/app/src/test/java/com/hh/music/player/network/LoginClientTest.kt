package com.hh.music.player.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginClientTest {

    // ---------------- MUSIC_U extraction ----------------

    @Test
    fun `extracts music_u from set-cookie headers ignoring attributes`() {
        val headers = listOf(
            "__csrf=abc123; Path=/; HttpOnly",
            "MUSIC_U=0028f5f0b0c34d17ac7db9e94fde7d51; Max-Age=15552000; Expires=Thu, 01 Jan 2027 00:00:00 GMT; Path=/",
            "NMTID=00O89x; Path=/"
        )
        assertEquals("0028f5f0b0c34d17ac7db9e94fde7d51", LoginClient.extractMusicU(headers))
    }

    @Test
    fun `music_u extraction is case insensitive and rejects blanks`() {
        assertEquals("token", LoginClient.extractMusicU(listOf("music_u=token; Path=/")))
        assertNull(LoginClient.extractMusicU(listOf("MUSIC_U=; Path=/")))
        assertNull(LoginClient.extractMusicU(emptyList()))
    }

    @Test
    fun `accepts pasted full cookie strings as well as bare tokens`() {
        val pasted = "MUSIC_U=tok123; __csrf=xyz; os=pc"
        assertEquals("tok123", LoginClient.extractMusicU(listOf(pasted)))
    }

    // ---------------- account parsing ----------------

    @Test
    fun `parses profile fields from account get response`() {
        val body = """
            {"code":200,"account":{"id":99},"profile":{"userId":42,"nickname":"云村民","avatarUrl":"https://p.music.163.com/a.jpg"}}
        """.trimIndent()
        val info = LoginClient.parseAccount(body)
        assertTrue(info != null)
        assertEquals(42L, info!!.userId)
        assertEquals("云村民", info.nickname)
        assertEquals("https://p.music.163.com/a.jpg", info.avatarUrl)
    }

    @Test
    fun `account parsing fails closed on logged out or malformed payloads`() {
        assertNull(LoginClient.parseAccount("""{"code":301,"msg":"未登录"}"""))
        assertNull(LoginClient.parseAccount("""{"code":200}"""))
        assertNull(LoginClient.parseAccount("not json at all"))
    }

    // ---------------- poll status mapping ----------------

    @Test
    fun `poll status maps codes and carries the token on success`() {
        assertTrue(
            LoginClient.parsePollStatus("""{"code":801}""", emptyList()) is QrPollStatus.WaitingForScan
        )
        assertTrue(
            LoginClient.parsePollStatus("""{"code":802}""", emptyList()) is QrPollStatus.WaitingForConfirm
        )
        val expired = LoginClient.parsePollStatus("""{"code":800}""", emptyList())
        assertTrue(expired is QrPollStatus.Expired)

        val success = LoginClient.parsePollStatus(
            """{"code":803,"cookie":"MUSIC_U=zzz"}""",
            listOf("MUSIC_U=zzz; Path=/")
        )
        assertEquals(QrPollStatus.Success("zzz"), success)
    }

    @Test
    fun `success without a usable cookie degrades to an empty token for caller error handling`() {
        val status = LoginClient.parsePollStatus("""{"code":803}""", emptyList())
        assertEquals(QrPollStatus.Success(""), status)
    }

    @Test
    fun `unknown or missing code falls back to waiting-for-scan`() {
        assertTrue(LoginClient.parsePollStatus("""{}""", emptyList()) is QrPollStatus.WaitingForScan)
        assertTrue(LoginClient.parsePollStatus("garbage", emptyList()) is QrPollStatus.WaitingForScan)
    }

    // ---------------- qr content ----------------

    @Test
    fun `qr content embeds the unikey into the official login url`() {
        assertEquals(
            "https://music.163.com/login?codekey=KEY-1",
            LoginClient.qrContent("KEY-1")
        )
    }
}
