package org.samc.core

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.*

object AppUpdates {
    const val VERSION = "6.26.2"
    const val RELEASES = "https://github.com/ZTweng/Smart-Checker/releases"
    data class Release(val version: String, val notes: String, val page: String)

    fun isNewer(candidate: String, installed: String = VERSION): Boolean {
        fun parts(value: String): List<Int>? {
            if (!value.matches(Regex("v?\\d+\\.\\d+\\.\\d+"))) return null
            return value.removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }
        }
        val next = parts(candidate) ?: return false
        val current = parts(installed) ?: return false
        for (i in 0..2) if (next[i] != current[i]) return next[i] > current[i]
        return false
    }

    /** Call off the UI thread. Downloads metadata only; never executes remote content. */
    fun latest(): Release? {
        val connection = URL("https://api.github.com/repos/ZTweng/Smart-Checker/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Smart-Checker/$VERSION")
            if (connection.responseCode == 404) return null
            require(connection.responseCode == 200) { "更新服务暂不可用（${connection.responseCode}），请稍后重试" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 1024 * 1024) { "更新信息过大" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size <= 1024 * 1024) { "更新信息过大" }
            val json = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            if (json["draft"]?.jsonPrimitive?.booleanOrNull == true ||
                json["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
            val tag = json.getValue("tag_name").jsonPrimitive.content
            if (!isNewer(tag)) return null
            val page = json.getValue("html_url").jsonPrimitive.content
            require(page.startsWith("$RELEASES/tag/")) { "更新地址不属于官方发布仓库" }
            return Release(tag.removePrefix("v"), json["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(4000), page)
        } finally {
            connection.disconnect()
        }
    }
}
