package com.webdavmusic.data.lyrics

import com.webdavmusic.data.model.LyricLine
import com.webdavmusic.data.model.LyricType
import com.webdavmusic.data.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.regex.Pattern

object LyricParser {

    // [mm:ss.xx] or [mm:ss:xx] or [mm:ss.xxx]
    private val TIME_RE = Pattern.compile("\\[(\\d{1,2}):(\\d{2})[.:(](\\d{2,3})\\]")
    private val OFFSET_RE = Pattern.compile("\\[offset:(-?\\d+)]", Pattern.CASE_INSENSITIVE)

    fun parseLrc(content: String, songId: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        var offset = 0L
        var hasTranslation = false

        content.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            // Offset tag
            OFFSET_RE.matcher(line).let { m ->
                if (m.find()) { offset = m.group(1)?.toLongOrNull() ?: 0L; return@forEach }
            }

            // Time tags
            val timeMatcher = TIME_RE.matcher(line)
            if (!timeMatcher.find()) return@forEach

            val min = timeMatcher.group(1)?.toLongOrNull() ?: 0L
            val sec = timeMatcher.group(2)?.toLongOrNull() ?: 0L
            val ms  = timeMatcher.group(3)?.let {
                if (it.length == 2) it.toLongOrNull()?.times(10) else it.toLongOrNull()
            } ?: 0L
            val timeMs = min * 60_000 + sec * 1000 + ms

            val text = TIME_RE.matcher(line).replaceAll("").trim()
            if (text.isEmpty()) return@forEach

            // Check for inline translation: text // translation  OR  text | translation
            val sep = when {
                "//" in text -> "//"
                " | " in text -> " | "
                else -> null
            }
            val (main, translated) = if (sep != null) {
                val parts = text.split(sep, limit = 2)
                parts[0].trim() to parts.getOrElse(1) { "" }.trim()
            } else text to ""

            if (translated.isNotEmpty()) hasTranslation = true
            lines += LyricLine(timeMs, main, translated)
        }

        lines.sort()
        return Lyrics(
            songId = songId,
            type = if (hasTranslation) LyricType.ENHANCED_LRC else LyricType.LRC,
            lines = lines.distinctBy { it.timeMs },
            hasTranslation = hasTranslation,
            offset = offset
        )
    }

    suspend fun loadFromFile(path: String, songId: String): Lyrics? = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(path)
            if (!f.exists()) return@withContext null
            parseLrc(f.readText(Charsets.UTF_8), songId)
        }.getOrElse { e -> Timber.e(e, "Load lyrics failed: $path"); null }
    }

    /** Given a song file path, return the .lrc sibling path if it exists */
    fun findLrcPath(songPath: String): String? {
        val base = songPath.substringBeforeLast('.')
        val lrc = "$base.lrc"
        return if (File(lrc).exists()) lrc else null
    }

    /** Given a WebDAV path like /music/song.mp3, return /music/song.lrc */
    fun webDavLrcPath(songPath: String) = songPath.substringBeforeLast('.') + ".lrc"
}
