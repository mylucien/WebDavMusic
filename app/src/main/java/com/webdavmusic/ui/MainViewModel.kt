package com.webdavmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webdavmusic.data.model.*
import com.webdavmusic.data.repository.MusicRepository
import com.webdavmusic.player.PlayerController
import com.webdavmusic.player.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { ALL, LOCAL, ALBUMS, ARTISTS, FAVORITES, PLAYLISTS }

@HiltViewModel
class MainViewModel @Inject constructor(
    val repo: MusicRepository,
    val player: PlayerController
) : ViewModel() {

    val accounts     = repo.getAllAccounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allSongs     = repo.getAllSongs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val localSongs   = repo.getLocalSongs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val artists      = repo.getArtists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val albums       = repo.getAlbums().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favorites    = repo.getFavorites().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val playlists    = repo.getAllPlaylists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val totalCount   = repo.getTotalCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val scanProgress = repo.scanProgress
    val playerState: StateFlow<PlayerState> = player.state

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    val searchResults = _searchQuery.debounce(300).flatMapLatest { q ->
        if (q.length >= 2) repo.searchSongs(q) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    private val _activeTab = MutableStateFlow(LibraryTab.ALL)
    val activeTab = _activeTab.asStateFlow()
    fun setTab(t: LibraryTab) { _activeTab.value = t }

    // Lyrics state
    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics = _lyrics.asStateFlow()
    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading = _lyricsLoading.asStateFlow()

    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _msg.asSharedFlow()
    private fun msg(s: String) = viewModelScope.launch { _msg.emit(s) }

    // ── Accounts ──────────────────────────────────────────────────────────────
    fun addAccount(a: WebDavAccount) = viewModelScope.launch {
        repo.addAccount(a).onSuccess { msg("✅ 账户「${a.name}」已添加") }.onFailure { msg("❌ 失败: ${it.message}") }
    }
    fun updateAccount(a: WebDavAccount) = viewModelScope.launch { repo.updateAccount(a); msg("✅ 账户已更新") }
    fun deleteAccount(a: WebDavAccount) = viewModelScope.launch { repo.deleteAccount(a); msg("🗑️ 账户已删除") }
    fun testConnection(a: WebDavAccount, cb: (Boolean, String) -> Unit) = viewModelScope.launch {
        repo.testConnection(a).onSuccess { cb(true, "✅ 连接成功") }.onFailure { cb(false, "❌ ${it.message}") }
    }

    // ── Scan ─────────────────────────────────────────────────────────────────
    fun scanAll() = viewModelScope.launch {
        if (accounts.value.isEmpty()) { msg("请先添加 WebDAV 账户"); return@launch }
        repo.scanAllAccounts()
    }
    fun scanAccount(a: WebDavAccount, folders: List<String> = emptyList()) = viewModelScope.launch { repo.scanAccount(a, folders) }
    fun scanLocalMusic(folders: List<String> = emptyList()) = viewModelScope.launch { repo.scanLocalMusic(folders) }

    // ── Browse (for folder picker) ────────────────────────────────────────────
    suspend fun listWebDavFolder(account: WebDavAccount, path: String) = repo.listWebDavFolder(account, path)
    fun getLocalTopFolders(): List<String> = repo.getLocalTopFolders()

    // ── Playback ──────────────────────────────────────────────────────────────
    fun playSongs(songs: List<Song>, index: Int = 0) {
        if (songs.isEmpty()) return
        player.playSongs(songs, index.coerceIn(0, songs.size - 1))
    }
    fun playSong(song: Song) {
        val list = currentList(); val idx = list.indexOfFirst { it.id == song.id }
        if (idx >= 0) player.playSongs(list, idx) else player.playSong(song)
        loadLyricsFor(song)
    }
    fun addToQueue(song: Song) { player.addToQueue(song); msg("已添加到队列") }
    fun togglePlayPause() = player.togglePlayPause()
    fun skipNext()        = player.skipNext()
    fun skipPrevious()    = player.skipPrevious()
    fun seekTo(ms: Long)  = player.seekTo(ms)
    fun toggleShuffle()   = player.toggleShuffle()
    fun cycleRepeat()     = player.cycleRepeat()

    // ── Favorites ─────────────────────────────────────────────────────────────
    fun toggleFavorite(song: Song) = viewModelScope.launch {
        repo.toggleFavorite(song); msg(if (song.isFavorite) "取消收藏" else "❤️ 已收藏")
    }

    // ── Playlists ─────────────────────────────────────────────────────────────
    fun createPlaylist(name: String) = viewModelScope.launch { repo.createPlaylist(name); msg("✅ 播放列表已创建") }
    fun deletePlaylist(p: Playlist)  = viewModelScope.launch { repo.deletePlaylist(p); msg("🗑️ 播放列表已删除") }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { repo.renamePlaylist(id, name); msg("✅ 已重命名") }
    fun clearPlaylist(id: Long)      = viewModelScope.launch { repo.clearPlaylist(id); msg("✅ 播放列表已清空") }
    fun removeFromPlaylist(pid: Long, song: Song) = viewModelScope.launch { repo.removeFromPlaylist(pid, song.id); msg("已移除") }
    fun addSongToPlaylist(pid: Long, song: Song) = viewModelScope.launch {
        val pl = playlists.value.find { it.id == pid } ?: return@launch
        val count = repo.getPlaylistSongs(pid).first().size
        repo.addToPlaylist(pid, song.id, count); msg("已添加到「${pl.name}」")
    }
    fun playPlaylist(id: Long) = viewModelScope.launch {
        val songs = repo.getPlaylistSongs(id).first()
        if (songs.isNotEmpty()) playSongs(songs) else msg("播放列表为空")
    }
    fun getPlaylistSongs(id: Long) = repo.getPlaylistSongs(id)

    // ── Lyrics ────────────────────────────────────────────────────────────────
    fun loadLyricsFor(song: Song) = viewModelScope.launch {
        _lyricsLoading.value = true
        _lyrics.value = repo.loadLyrics(song)
        _lyricsLoading.value = false
    }

    // On track change, auto-load lyrics
    init {
        viewModelScope.launch {
            playerState.collect { st ->
                if (st.currentId.isNotEmpty()) {
                    val song = allSongs.value.find { it.id == st.currentId }
                        ?: localSongs.value.find { it.id == st.currentId }
                    if (song != null && song.id != _lyrics.value?.songId) {
                        loadLyricsFor(song)
                    }
                }
            }
        }
    }

    private fun currentList() = when (_activeTab.value) {
        LibraryTab.LOCAL     -> localSongs.value
        LibraryTab.FAVORITES -> favorites.value
        else                 -> allSongs.value
    }
}
