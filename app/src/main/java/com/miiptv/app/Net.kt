package com.miiptv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0 Mobile Safari/537.36"

/** Descarga texto siguiendo hasta 5 redirecciones (incluye http -> https). */
suspend fun fetchText(startUrl: String): String = withContext(Dispatchers.IO) {
    var current = startUrl
    repeat(6) {
        val conn = URL(current).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            when (code) {
                in 200..299 -> return@withContext conn.inputStream.bufferedReader().use { it.readText() }
                301, 302, 303, 307, 308 -> {
                    val loc = conn.getHeaderField("Location")
                        ?: error("Redirección sin destino (HTTP $code)")
                    current = URI(current).resolve(loc).toString()
                }
                else -> error("El servidor respondió HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }
    error("Demasiadas redirecciones")
}
