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

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MusicRepository,
    val playerController: PlayerController
) : ViewModel() {

    val accounts = repository.getAllAccounts().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val allSongs = repository.getAllSongs().stateIn(
        viewModelScope, SharingStarted.Lazily, emptyList()
    )

    val artists = repository.getArtists().stateIn(
        viewModelScope, SharingStarted.Lazily, emptyList()
    )

    val albums = repository.getAlbums().stateIn(
        viewModelScope, SharingStarted.Lazily, emptyList()
    )

    val playlists = repository.getAllPlaylists().stateIn(
        viewModelScope, SharingStarted.Lazily, emptyList()
    )

    val scanProgress = repository.scanProgress

    val playerState: StateFlow<PlayerState> = playerController.playerState

    val totalSongCount = repository.getTotalCount().stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )

    private val _searchQuery = MutableStateFlow("")
    val searchResults = _searchQuery
        .debounce(300)
        .filter { it.length >= 2 }
        .flatMapLatest { repository.searchSongs(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage = _uiMessage.asSharedFlow()

    // ─── Account Management ───────────────────────────────────────────────────

    fun addAccount(account: WebDavAccount) = viewModelScope.launch {
        repository.addAccount(account)
            .onSuccess { _uiMessage.emit("✅ 账户已添加") }
            .onFailure { _uiMessage.emit("❌ 添加失败: ${it.message}") }
    }

    fun updateAccount(account: WebDavAccount) = viewModelScope.launch {
        repository.updateAccount(account)
        _uiMessage.emit("✅ 账户已更新")
    }

    fun deleteAccount(account: WebDavAccount) = viewModelScope.launch {
        repository.deleteAccount(account)
        _uiMessage.emit("🗑️ 账户已删除")
    }

    fun testConnection(account: WebDavAccount, onResult: (Boolean, String) -> Unit) =
        viewModelScope.launch {
            repository.testConnection(account)
                .onSuccess { onResult(true, "连接成功 ✅") }
                .onFailure { onResult(false, "连接失败: ${it.message}") }
        }

    // ─── Scan ─────────────────────────────────────────────────────────────────

    fun scanAllAccounts() = viewModelScope.launch {
        if (accounts.value.isEmpty()) {
            _uiMessage.emit("请先添加 WebDAV 账户")
            return@launch
        }
        repository.scanAllAccounts()
    }

    fun scanAccount(account: WebDavAccount) = viewModelScope.launch {
        repository.scanAccount(account)
    }

    // ─── Playback ─────────────────────────────────────────────────────────────

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        playerController.playSongs(songs, startIndex)
    }

    fun playSong(song: Song) {
        playerController.playSong(song)
    }

    fun getAlbumSongs(album: String): Flow<List<Song>> = repository.getSongsByAlbum(album)
    fun getArtistSongs(artist: String): Flow<List<Song>> = repository.getSongsByArtist(artist)
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> = repository.getPlaylistSongs(playlistId)

    // ─── Search ───────────────────────────────────────────────────────────────

    fun search(query: String) { _searchQuery.value = query }

    // ─── Player Controls ──────────────────────────────────────────────────────

    fun togglePlayPause() = playerController.togglePlayPause()
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun toggleShuffle() = playerController.toggleShuffle()
    fun toggleRepeat() = playerController.toggleRepeat()
}
