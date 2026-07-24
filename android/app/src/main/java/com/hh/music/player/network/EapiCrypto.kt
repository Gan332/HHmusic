package com.hh.music.player.network

import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * NetEase eapi encryption, ported from GuitaristRin/Ncrust.
 *
 * eapi uses AES-128-ECB with a fixed key, no RSA. The encrypted payload is:
 *   "<apiPath>-36cd479b6b5-<payloadJson>-36cd479b6b5-<md5(nobody<apiPath>use<payloadJson>md5forencrypt)>"
 * hex-encoded, and posted as the form field `params` to https://music.163.com/eapi/...
 *
 * This lets the app talk to NetEase directly with no backend proxy.
 */
object EapiCrypto {
    private val AES_KEY = "e82ckenh8dichen8".toByteArray()
    private const val MAGIC_PREFIX = "nobody"
    private const val MAGIC_SUFFIX = "md5forencrypt"
    private const val SEPARATOR = "-36cd479b6b5-"

    fun encryptParams(url: String, payload: Map<String, Any>): String {
        val parsedUrl = java.net.URL(url)
        // /eapi/<path>  ->  /api/<path>
        val urlPath = parsedUrl.path.replace("/eapi/", "/api/")
        val payloadJson = JSONObject(payload).toString()

        val digest = md5("$MAGIC_PREFIX${urlPath}use${payloadJson}$MAGIC_SUFFIX")
        val paramsStr = "${urlPath}${SEPARATOR}${payloadJson}${SEPARATOR}${digest}"

        return aesEncrypt(paramsStr)
    }

    private fun aesEncrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(AES_KEY, "AES"))
        return bytesToHex(cipher.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    private fun md5(input: String): String =
        bytesToHex(MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)))

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
