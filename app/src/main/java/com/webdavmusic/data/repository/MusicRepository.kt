package com.webdavmusic.data.repository

import com.webdavmusic.data.AccountDao
import com.webdavmusic.data.SongDao
import com.webdavmusic.data.PlaylistDao
import com.webdavmusic.data.model.*
import com.webdavmusic.data.webdav.WebDavClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val webDavClient: WebDavClient
) {

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress

    // ─── Accounts ─────────────────────────────────────────────────────────────

    fun getAllAccounts(): Flow<List<WebDavAccount>> = accountDao.getAllAccounts()

    suspend fun addAccount(account: WebDavAccount): Result<Long> = runCatching {
        accountDao.insertAccount(account)
    }

    suspend fun updateAccount(account: WebDavAccount) {
        webDavClient.invalidateClient(account.id)
        accountDao.updateAccount(account)
    }

    suspend fun deleteAccount(account: WebDavAccount) {
        accountDao.deleteAccount(account)
        songDao.deleteSongsByAccount(account.id)
        webDavClient.invalidateClient(account.id)
    }

    suspend fun testConnection(account: WebDavAccount): Result<Unit> =
        webDavClient.testConnection(account)

    // ─── Songs ────────────────────────────────────────────────────────────────

    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()
    fun getSongsByAccount(accountId: Long): Flow<List<Song>> = songDao.getSongsByAccount(accountId)
    fun getArtists(accountId: Long = -1): Flow<List<String>> = songDao.getArtists(accountId)
    fun getAlbums(accountId: Long = -1): Flow<List<String>> = songDao.getAlbums(accountId)
    fun searchSongs(query: String): Flow<List<Song>> = songDao.search(query)
    fun getSongsByAlbum(album: String): Flow<List<Song>> = songDao.getSongsByAlbum(album)
    fun getSongsByArtist(artist: String): Flow<List<Song>> = songDao.getSongsByArtist(artist)
    fun getTotalCount(): Flow<Int> = songDao.getTotalCount()

    // ─── Playlists ────────────────────────────────────────────────────────────

    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> = playlistDao.getPlaylistSongs(playlistId)

    suspend fun createPlaylist(name: String, accountId: Long? = null): Long {
        return playlistDao.insertPlaylist(Playlist(name = name, accountId = accountId))
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: String, position: Int) {
        playlistDao.insertPlaylistSong(PlaylistSong(playlistId, songId, position))
    }

    // ─── Scan ─────────────────────────────────────────────────────────────────

    /**
     * Scan all active accounts for audio files and populate the database
     */
    suspend fun scanAllAccounts() {
        val accounts = mutableListOf<WebDavAccount>()
        accountDao.getAllAccounts().collect { list ->
            accounts.addAll(list.filter { it.isActive })
            return@collect
        }
        accounts.forEach { scanAccount(it) }
    }

    suspend fun scanAccount(account: WebDavAccount) {
        Timber.d("Scanning account: ${account.name}")

        _scanProgress.value = ScanProgress(
            accountName = account.name,
            currentPath = "/",
            found = 0,
            total = 0,
            isRunning = true
        )

        val result = webDavClient.scanForAudio(
            account = account,
            path = "/"
        ) { path, count ->
            _scanProgress.value = ScanProgress(
                accountName = account.name,
                currentPath = path,
                found = count,
                total = 0,
                isRunning = true
            )
        }

        result.onSuccess { files ->
            val songs = files.map { file ->
                val title = file.name.substringBeforeLast('.')
                val ext = file.name.substringAfterLast('.', "")
                val format = AudioFormat.fromExtension(ext) ?: AudioFormat.MP3
                val id = "${account.id}:${file.path}".hashCode().toString()

                Song(
                    id = id,
                    accountId = account.id,
                    path = file.path,
                    url = webDavClient.buildStreamUrl(account, file.path),
                    title = title,
                    fileSize = file.size,
                    mimeType = file.mimeType,
                    format = format,
                    lastModified = file.lastModified
                )
            }

            // Clear old songs and insert new ones
            songDao.deleteSongsByAccount(account.id)
            songDao.insertSongs(songs)
            accountDao.updateLastScanned(account.id, System.currentTimeMillis())

            _scanProgress.value = ScanProgress(
                accountName = account.name,
                currentPath = "Done",
                found = songs.size,
                total = songs.size,
                isRunning = false
            )

            Timber.d("Scan complete for ${account.name}: ${songs.size} songs")

            // Auto-create playlist from this account
            autoCreateAccountPlaylist(account, songs)

        }.onFailure { e ->
            Timber.e(e, "Scan failed for ${account.name}")
            _scanProgress.value = ScanProgress(
                accountName = account.name,
                currentPath = "",
                found = 0,
                total = 0,
                isRunning = false,
                error = e.message
            )
        }
    }

    private suspend fun autoCreateAccountPlaylist(account: WebDavAccount, songs: List<Song>) {
        val playlistName = "📁 ${account.name}"
        val playlistId = playlistDao.insertPlaylist(
            Playlist(
                name = playlistName,
                accountId = account.id,
                isAutoGenerated = true
            )
        )
        playlistDao.clearPlaylist(playlistId)
        songs.forEachIndexed { index, song ->
            playlistDao.insertPlaylistSong(PlaylistSong(playlistId, song.id, index))
        }
    }

    fun buildStreamUrl(account: WebDavAccount, song: Song): String =
        webDavClient.buildStreamUrl(account, song.path)
}
