package com.miiptv.app

import java.net.URI

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
)

object M3uParser {

    private const val NO_GROUP = "Sin grupo"

    // KEY="valor" o KEY=valor (sin comillas)
    private val attrRegex = Regex("([A-Za-z0-9_-]+)=(?:\"([^\"]*)\"|([^,\\s]*))")

    private fun attrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { m ->
            val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
            m.groupValues[1].lowercase() to value
        }

    private fun resolve(base: String, ref: String): String =
        if (ref.startsWith("http://", true) || ref.startsWith("https://", true)) ref
        else try {
            URI(base).resolve(ref).toString()
        } catch (e: Exception) {
            ref
        }

    /**
     * Soporta tres casos:
     *  1) Lista M3U de canales (#EXTINF + URL)       -> un item por canal
     *  2) Master HLS (#EXT-X-STREAM-INF + URL)       -> un item por variante
     *  3) Playlist HLS de medios (segmentos .ts)     -> UN solo item: la propia URL
     */
    fun parse(text: String, baseUrl: String): List<Channel> {
        val isMediaPlaylist = text.contains("#EXT-X-TARGETDURATION") ||
            text.contains("#EXT-X-MEDIA-SEQUENCE")
        if (isMediaPlaylist) {
            return listOf(Channel("Transmisión en vivo", baseUrl, null, NO_GROUP))
        }

        val out = ArrayList<Channel>()
        var name: String? = null
        var logo: String? = null
        var group = NO_GROUP
        var variantName: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTINF") -> {
                    val a = attrs(line)
                    logo = a["tvg-logo"]?.ifBlank { null }
                    group = a["group-title"]?.ifBlank { null } ?: NO_GROUP
                    val title = line.substringAfterLast(',', "").trim()
                    name = title.ifBlank { a["tvg-name"] }
                }

                line.startsWith("#EXT-X-STREAM-INF") -> {
                    val a = attrs(line)
                    variantName = a["name"]?.ifBlank { null }
                        ?: a["resolution"]?.ifBlank { null }
                        ?: a["bandwidth"]?.toLongOrNull()?.let { "${it / 1000} kbps" }
                }

                line.startsWith("#") -> Unit

                else -> {
                    val fallback = line.substringAfterLast('/').substringBefore('?')
                    out += Channel(
                        name = name ?: variantName ?: fallback,
                        url = resolve(baseUrl, line),
                        logo = logo,
                        group = group,
                    )
                    name = null
                    logo = null
                    group = NO_GROUP
                    variantName = null
                }
            }
        }
        return out
    }
}
