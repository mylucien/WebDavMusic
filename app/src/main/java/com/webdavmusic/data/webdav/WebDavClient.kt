package com.webdavmusic.data.webdav

import com.webdavmusic.data.model.AudioFormat
import com.webdavmusic.data.model.WebDavAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.StringReader
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class WebDavFile(val path: String, val name: String, val isDirectory: Boolean,
                      val size: Long = 0L, val lastModified: Long = 0L, val mimeType: String = "")

@Singleton
class WebDavClient @Inject constructor() {
    private val clients = mutableMapOf<Long, OkHttpClient>()

    private fun buildClient(a: WebDavAccount) = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = if (a.username.isNotEmpty())
                chain.request().newBuilder().header("Authorization", Credentials.basic(a.username, a.password)).build()
            else chain.request()
            chain.proceed(req)
        }.build()

    private fun client(a: WebDavAccount) = clients.getOrPut(a.id) { buildClient(a) }
    fun invalidate(id: Long) { clients.remove(id) }
    fun authenticatedClient(a: WebDavAccount) = client(a)

    fun normalize(url: String) = url.trim().let { if (!it.startsWith("http")) "http://$it" else it }.trimEnd('/')
    fun streamUrl(a: WebDavAccount, path: String): String {
        val base = normalize(a.url); val p = path.trimStart('/')
        return if (p.isEmpty()) base else "$base/$p"
    }

    suspend fun testConnection(a: WebDavAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(a.url.isNotBlank()) { "WebDAV 地址不能为空" }
            val resp = client(a).newCall(Request.Builder().url(normalize(a.url)).method("OPTIONS", null).build()).execute()
            val code = resp.code; resp.close()
            if (code == 401) error("认证失败，请检查用户名和密码")
            check(code in 200..299) { "服务器错误: HTTP $code" }
        }
    }

    suspend fun listDirectory(a: WebDavAccount, path: String): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = streamUrl(a, path)
            val body = """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><resourcetype/><getcontentlength/><getcontenttype/><getlastmodified/></prop></propfind>"""
            val resp = client(a).newCall(Request.Builder().url(url)
                .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
                .header("Depth", "1").build()).execute()
            val xml = resp.body?.string() ?: ""; resp.close()
            check(resp.code in 200..299) { "PROPFIND 失败: HTTP ${resp.code}" }
            parsePropfind(xml, normalize(a.url)).drop(1)
        }
    }

    /** Scan account for audio. If folderPaths is non-empty, only scan those paths. */
    suspend fun scanForAudio(
        a: WebDavAccount,
        folderPaths: List<String> = emptyList(),
        onProgress: (String, Int) -> Unit
    ): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        runCatching {
            require(a.url.isNotBlank()) { "WebDAV 地址不能为空" }
            val results = mutableListOf<WebDavFile>()

            suspend fun recurse(dir: String) {
                onProgress(dir, results.size)
                listDirectory(a, dir).getOrElse { Timber.w(it, "Skip $dir"); return }.forEach { f ->
                    if (f.isDirectory) recurse(f.path)
                    else if (AudioFormat.isAudioFile(f.name)) { results += f; if (results.size % 20 == 0) onProgress(dir, results.size) }
                }
            }

            if (folderPaths.isEmpty()) recurse("/")
            else folderPaths.forEach { recurse(it) }
            results
        }
    }

    private fun parsePropfind(xml: String, baseUrl: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        if (xml.isBlank()) return files
        try {
            val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
            p.setInput(StringReader(xml))
            var path = ""; var isDir = false; var size = 0L; var mime = ""; var mod = 0L
            var inResp = false; var tag = ""
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                val ln = p.name?.substringAfterLast(':') ?: ""
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        tag = ln
                        if (ln == "response") { inResp = true; path=""; isDir=false; size=0L; mime=""; mod=0L }
                        if (ln == "collection" && inResp) isDir = true
                    }
                    XmlPullParser.TEXT -> if (inResp) {
                        val t = p.text?.trim() ?: ""
                        when (tag) {
                            "href" -> path = decodeHref(t, baseUrl)
                            "getcontentlength" -> size = t.toLongOrNull() ?: 0L
                            "getcontenttype" -> mime = t
                            "getlastmodified" -> mod = parseDate(t)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (ln == "response" && inResp && path.isNotEmpty()) {
                            val n = path.trimEnd('/').substringAfterLast('/')
                            if (n.isNotEmpty()) files += WebDavFile(path, n, isDir, size, mod, mime)
                            inResp = false
                        }
                        tag = ""
                    }
                }
                ev = p.next()
            }
        } catch (e: Exception) { Timber.e(e, "XML parse") }
        return files
    }

    private fun decodeHref(href: String, base: String) = runCatching {
        val d = URLDecoder.decode(href, "UTF-8")
        if (d.startsWith("http://") || d.startsWith("https://")) d.removePrefix(base.trimEnd('/')) else d
    }.getOrElse { href }

    private fun parseDate(d: String) = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(d)?.time ?: 0L
    }.getOrElse { 0L }
}
