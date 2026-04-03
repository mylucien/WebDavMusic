package com.webdavmusic.data.repository

import android.content.Context
import com.webdavmusic.data.*
import com.webdavmusic.data.local.LocalMusicScanner
import com.webdavmusic.data.lyrics.LyricParser
import com.webdavmusic.data.model.*
import com.webdavmusic.data.webdav.WebDavClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val accountDao: AccountDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val webDavClient: WebDavClient,
    private val localScanner: LocalMusicScanner,
    private val okHttp: OkHttpClient
) {
    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    // ── Accounts ──────────────────────────────────────────────────────────────
    fun getAllAccounts() = accountDao.getAll()
    suspend fun getAccountById(id: Long) = accountDao.getById(id)
    suspend fun addAccount(a: WebDavAccount) = withContext(Dispatchers.IO) { runCatching { accountDao.insert(a) } }
    suspend fun updateAccount(a: WebDavAccount) = withContext(Dispatchers.IO) { webDavClient.invalidate(a.id); accountDao.update(a) }
    suspend fun deleteAccount(a: WebDavAccount) = withContext(Dispatchers.IO) {
        songDao.deleteByAccount(a.id); playlistDao.deleteAutoFor(a.id); accountDao.delete(a); webDavClient.invalidate(a.id)
    }
    suspend fun testConnection(a: WebDavAccount) = webDavClient.testConnection(a)

    // ── Songs ─────────────────────────────────────────────────────────────────
    fun getAllSongs()        = songDao.getAll()
    fun getLocalSongs()     = songDao.getLocal()
    fun getFavorites()      = songDao.getFavorites()
    fun getArtists()        = songDao.getArtists()
    fun getAlbums()         = songDao.getAlbums()
    fun searchSongs(q: String) = songDao.search(q)
    fun getTotalCount()     = songDao.count()
    suspend fun toggleFavorite(song: Song) = withContext(Dispatchers.IO) { songDao.setFav(song.id, !song.isFavorite) }

    // ── Playlists ─────────────────────────────────────────────────────────────
    fun getAllPlaylists()       = playlistDao.getAll()
    fun getPlaylistSongs(id: Long) = playlistDao.getSongs(id)
    suspend fun createPlaylist(name: String) = playlistDao.insert(Playlist(name = name))
    suspend fun deletePlaylist(p: Playlist)  = playlistDao.delete(p)
    suspend fun renamePlaylist(id: Long, name: String) = withContext(Dispatchers.IO) { playlistDao.rename(id, name) }
    suspend fun clearPlaylist(id: Long)      = withContext(Dispatchers.IO) { playlistDao.clear(id) }
    suspend fun addToPlaylist(pid: Long, sid: String, pos: Int) = playlistDao.insertSong(PlaylistSong(pid, sid, pos))
    suspend fun removeFromPlaylist(pid: Long, sid: String) = withContext(Dispatchers.IO) {
        // rebuild positions after removal
        val songs = playlistDao.getSongs(pid).first()
        playlistDao.clear(pid)
        songs.filter { it.id != sid }.forEachIndexed { i, s -> playlistDao.insertSong(PlaylistSong(pid, s.id, i)) }
    }

    // ── WebDAV browse (for folder picker) ────────────────────────────────────
    suspend fun listWebDavFolder(account: WebDavAccount, path: String): Result<List<BrowseItem>> =
        withContext(Dispatchers.IO) {
            webDavClient.listDirectory(account, path).map { files ->
                files.map { f ->
                    BrowseItem(f.path, f.name, f.isDirectory)
                }.sortedWith(compareByDescending<BrowseItem> { it.isDirectory }.thenBy { it.name.lowercase() })
            }
        }

    fun getLocalTopFolders() = localScanner.getTopLevelFolders()

    // ── Scanning ──────────────────────────────────────────────────────────────
    suspend fun scanAllAccounts() {
        accountDao.getAll().first().filter { it.isActive }.forEach { scanAccount(it) }
    }

    suspend fun scanAccount(account: WebDavAccount, folderPaths: List<String> = emptyList()) =
        withContext(Dispatchers.IO) {
            _scanProgress.value = ScanProgress(account.name, "/", 0, true)
            webDavClient.scanForAudio(account, folderPaths) { path, count ->
                _scanProgress.value = ScanProgress(account.name, path, count, true)
            }.onSuccess { files ->
                val songs = files.mapNotNull { file ->
                    runCatching {
                        val ext = file.name.substringAfterLast('.', "")
                        val id  = "webdav:${account.id}:${file.path}".hashCode()
                            .let { if (it < 0) "n${-it}" else "p$it" }
                        Song(
                            id = id, accountId = account.id, source = SongSource.WEBDAV,
                            path = file.path, url = webDavClient.streamUrl(account, file.path),
                            title = file.name.substringBeforeLast('.').ifBlank { file.name },
                            fileSize = file.size, mimeType = file.mimeType,
                            format = AudioFormat.fromExtension(ext) ?: AudioFormat.MP3,
                            lastModified = file.lastModified
                        )
                    }.getOrNull()
                }
                songDao.deleteByAccount(account.id)
                songDao.insertAll(songs)
                accountDao.updateScanned(account.id, System.currentTimeMillis())
                buildAutoPlaylist(account, songs)
                _scanProgress.value = ScanProgress(account.name, "", songs.size, false)
            }.onFailure { e ->
                Timber.e(e, "Scan failed: ${account.name}")
                _scanProgress.value = ScanProgress(account.name, "", 0, false, e.message ?: "未知错误")
            }
        }

    suspend fun scanLocalMusic(folderPaths: List<String> = emptyList()) = withContext(Dispatchers.IO) {
        _scanProgress.value = ScanProgress("本地音乐", "", 0, true)
        runCatching {
            val songs = localScanner.scan(folderPaths) { count ->
                _scanProgress.value = ScanProgress("本地音乐", "扫描中…", count, true)
            }
            songDao.deleteLocal()
            songDao.insertAll(songs)
            _scanProgress.value = ScanProgress("本地音乐", "", songs.size, false)
        }.onFailure { e ->
            _scanProgress.value = ScanProgress("本地音乐", "", 0, false, e.message)
        }
    }

    // ── Lyrics ────────────────────────────────────────────────────────────────
    suspend fun loadLyrics(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        when (song.source) {
            SongSource.LOCAL -> {
                val lrcPath = LyricParser.findLrcPath(song.path)
                if (lrcPath != null) LyricParser.loadFromFile(lrcPath, song.id) else null
            }
            SongSource.WEBDAV -> {
                val account = accountDao.getById(song.accountId) ?: return@withContext null
                loadWebDavLyrics(account, song)
            }
        }
    }

    private suspend fun loadWebDavLyrics(account: WebDavAccount, song: Song): Lyrics? {
        val lrcPath = LyricParser.webDavLrcPath(song.path)
        val url = webDavClient.streamUrl(account, lrcPath)
        return runCatching {
            val req = Request.Builder().url(url)
                .apply { if (account.username.isNotEmpty()) header("Authorization", Credentials.basic(account.username, account.password)) }
                .build()
            val resp = okHttp.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching null
            val text = resp.body?.string() ?: return@runCatching null
            LyricParser.parseLrc(text, song.id)
        }.getOrNull()
    }

    // ── Private ───────────────────────────────────────────────────────────────
    private suspend fun buildAutoPlaylist(account: WebDavAccount, songs: List<Song>) {
        val existing = playlistDao.getAll().first().firstOrNull { it.accountId == account.id && it.isAutoGenerated }
        val pid = existing?.id ?: playlistDao.insert(Playlist(name = "📁 ${account.name}", accountId = account.id, isAutoGenerated = true))
        playlistDao.clear(pid)
        songs.forEachIndexed { i, s -> playlistDao.insertSong(PlaylistSong(pid, s.id, i)) }
    }
}
