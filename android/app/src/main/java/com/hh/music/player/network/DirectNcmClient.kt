package com.hh.music.player.network

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to NetEase Cloud Music directly from the device — no backend required.
 *
 * Two modes (both reach https://music.163.com):
 *  - [eapiPost]: eapi-encrypted POST to /eapi/<path> (used for the song-url endpoint).
 *  - [apiPost] : plain form POST to /api/<path> (search/lyric/playlist/recommend work fine
 *                this way with UA/Referer headers, mirroring Ncrust's NcmApi).
 *
 * Song playback additionally falls back to the public "outer url" when eapi
 * returns no link (common for copyrighted tracks without a login cookie).
 */
object DirectNcmClient {

    private const val HOST = "https://music.163.com"
    private const val HOST_INTERFACE = "https://interface3.music.163.com"
    private const val UPGRADE_INSECURE = false // not used; kept for clarity

    private val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** eapi-encrypted POST. Returns the raw response body string. */
    fun eapiPost(pathUnderEapi: String, payload: Map<String, Any>): String {
        val url = if (pathUnderEapi.startsWith("http")) pathUnderEapi else HOST + "/eapi/" + pathUnderEapi.trimStart('/')
        val params = EapiCrypto.encryptParams(url, payload)
        val body = FormBody.Builder().add("params", params).build()
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", UA)
            .header("Referer", HOST + "/")
            .header("Cookie", cookieOrEmpty())
            .build()
        client.newCall(req).execute().use { res ->
            return res.body?.string() ?: throw Exception("empty eapi response")
        }
    }

    /** Plain form POST to /api/<path> with NetEase-friendly headers. */
    fun apiPost(pathUnderApi: String, fields: Map<String, String>): String {
        val url = HOST + "/api/" + pathUnderApi.trimStart('/')
        val builder = FormBody.Builder()
        fields.forEach { (k, v) -> builder.add(k, v) }
        val req = Request.Builder()
            .url(url)
            .post(builder.build())
            .header("User-Agent", UA)
            .header("Referer", HOST + "/")
            .header("Cookie", cookieOrEmpty())
            .header("X-Real-IP", "220.181.108.0")
            .build()
        client.newCall(req).execute().use { res ->
            return res.body?.string() ?: throw Exception("empty api response")
        }
    }

    /** Public (no-login) playable URL — the Ncrust outer-url fallback. */
    fun outerUrl(songId: Long): String =
        HOST + "/song/media/outer/url?id=$songId.mp3"

    /** Optional login cookie (MUSIC_U). Null by default = anonymous guest. */
    @Volatile
    private var cookie: String? = null

    fun setCookie(value: String?) { cookie = value?.takeIf { it.isNotBlank() } }
    fun getCookie(): String? = cookie
    private fun cookieOrEmpty(): String = cookie ?: ""

    /** Parse a JSON body defensively, throwing on the known error payload. */
    fun parseJson(body: String): JSONObject {
        val json = JSONObject(body)
        val code = json.optInt("code", 200)
        if (code != 200) {
            // Many endpoints still return useful data with non-200 codes; surface message only when truly empty.
            if (json.length() == 0 || (json.has("code") && json.length() == 1)) {
                throw Exception("NetEase returned code=$code")
            }
        }
        return json
    }
}
