package com.webdavmusic.data.webdav

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import com.webdavmusic.data.model.AudioFormat
import com.webdavmusic.data.model.WebDavAccount
import com.webdavmusic.data.model.WebDavFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavClient @Inject constructor() {

    private val clients = mutableMapOf<Long, OkHttpSardine>()
    private val okClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getClient(account: WebDavAccount): OkHttpSardine {
        return clients.getOrPut(account.id) {
            OkHttpSardine(okClient).also {
                it.setCredentials(account.username, account.password)
            }
        }
    }

    fun invalidateClient(accountId: Long) {
        clients.remove(accountId)
    }

    /**
     * Test if a WebDAV account is reachable and credentials are valid
     */
    suspend fun testConnection(account: WebDavAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sardine = getClient(account)
            sardine.list(account.url)
        }
    }

    /**
     * List files and directories at the given path
     */
    suspend fun listFiles(account: WebDavAccount, path: String): Result<List<WebDavFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sardine = getClient(account)
                val url = buildUrl(account.url, path)
                val resources: List<DavResource> = sardine.list(url)

                resources
                    .drop(1) // First element is the directory itself
                    .map { res ->
                        WebDavFile(
                            path = res.path,
                            name = res.name,
                            isDirectory = res.isDirectory,
                            size = res.contentLength ?: 0L,
                            lastModified = res.modified?.time ?: 0L,
                            mimeType = res.contentType ?: "",
                            contentType = res.contentType ?: ""
                        )
                    }
            }
        }

    /**
     * Recursively scan a directory and collect all audio files
     */
    suspend fun scanForAudio(
        account: WebDavAccount,
        path: String = "/",
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val audioFiles = mutableListOf<WebDavFile>()
            var found = 0

            fun isAudioFile(file: WebDavFile): Boolean {
                if (file.isDirectory) return false
                val ext = file.name.substringAfterLast('.', "")
                val mimeOk = AudioFormat.allMimeTypes.any { file.mimeType.startsWith(it) }
                val extOk = AudioFormat.fromExtension(ext) != null
                return mimeOk || extOk
            }

            suspend fun scanDir(dirPath: String) {
                onProgress(dirPath, found)
                val result = listFiles(account, dirPath).getOrElse {
                    Timber.e(it, "Failed to list $dirPath")
                    return
                }

                for (file in result) {
                    if (file.isDirectory) {
                        scanDir(file.path)
                    } else if (isAudioFile(file)) {
                        audioFiles.add(file)
                        found++
                        if (found % 10 == 0) onProgress(dirPath, found)
                    }
                }
            }

            scanDir(path)
            audioFiles
        }
    }

    /**
     * Build a streaming URL with embedded credentials
     */
    fun buildStreamUrl(account: WebDavAccount, filePath: String): String {
        val baseUrl = account.url.trimEnd('/')
        val cleanPath = if (filePath.startsWith("/")) filePath else "/$filePath"

        // Build URL with auth for ExoPlayer
        val urlWithCreds = if (account.username.isNotEmpty()) {
            val proto = if (baseUrl.startsWith("https")) "https" else "http"
            val rest = baseUrl.removePrefix("https://").removePrefix("http://")
            "$proto://${account.username}:${account.password}@$rest$cleanPath"
        } else {
            "$baseUrl$cleanPath"
        }

        return urlWithCreds
    }

    private fun buildUrl(base: String, path: String): String {
        val cleanBase = base.trimEnd('/')
        val cleanPath = path.trimStart('/')
        return if (cleanPath.isEmpty()) cleanBase else "$cleanBase/$cleanPath"
    }
}
