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

    /** Body plus Set-Cookie headers — needed by the QR-login poll (code 803 carries the session). */
    class HttpTextResponse(val body: String, val setCookies: List<String>)

    fun apiPostWithResponse(pathUnderApi: String, fields: Map<String, String>): HttpTextResponse {
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
            val body = res.body?.string() ?: throw Exception("empty api response")
            return HttpTextResponse(body, res.headers("Set-Cookie"))
        }
    }

    /** Plain GET to /api/<path> (e.g. v1/album/{id}) with NetEase-friendly headers. */
    fun apiGet(pathUnderApi: String): String {
        val url = HOST + "/api/" + pathUnderApi.trimStart('/')
        val req = Request.Builder()
            .url(url)
            .get()
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

    /** Anonymous device session ("MUSIC_A=…"), obtained once and reused. */
    @Volatile
    private var anonCookie: String? = null

    private val anonLock = Any()

    fun setCookie(value: String?) { cookie = value?.takeIf { it.isNotBlank() } }
    fun getCookie(): String? = cookie

    /**
     * Cookie header for outgoing calls: a real login wins; otherwise the cached
     * anonymous device session is used so standard-quality URLs resolve without
     * an account (免登录播放).
     */
    fun cookieOrEmpty(): String {
        cookie?.let { return it }
        return anonCookie ?: ""
    }

    /**
     * Register an anonymous NetEase device profile and cache its MUSIC_A cookie.
     * Mirrors the well-known register/anonimous handshake used by community
     * clients: an eapi POST whose Set-Cookie carries the anonymous token.
     * Idempotent and best-effort — any failure leaves playback on the old path.
     */
    fun ensureAnonymousCookie() {
        if (cookie != null || anonCookie != null) return
        synchronized(anonLock) {
            if (cookie != null || anonCookie != null) return
            runCatching {
                val url = HOST + "/eapi/register/anonimous"
                val params = EapiCrypto.encryptParams(url, mapOf("username" to "", "password" to "", "rememberLogin" to "true"))
                val body = FormBody.Builder().add("params", params).build()
                val req = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", UA)
                    .header("Referer", HOST + "/")
                    .header("Cookie", "os=pc; appver=2.10.13")
                    .build()
                client.newCall(req).execute().use { res ->
                    val musicA = res.headers("Set-Cookie").asSequence()
                        .map { it.substringBefore(';').trim() }
                        .firstOrNull { it.startsWith("MUSIC_A=") && it.length > "MUSIC_A=".length }
                    if (musicA != null) anonCookie = musicA
                }
            }
        }
    }

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
