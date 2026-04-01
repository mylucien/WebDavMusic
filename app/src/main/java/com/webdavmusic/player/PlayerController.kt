package com.webdavmusic.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.webdavmusic.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _playerState.value = _playerState.value.copy(
                currentMediaItem = mediaItem,
                title = mediaItem?.mediaMetadata?.title?.toString() ?: "",
                artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "",
                album = mediaItem?.mediaMetadata?.albumTitle?.toString() ?: ""
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _playerState.value = _playerState.value.copy(
                isBuffering = playbackState == Player.STATE_BUFFERING
            )
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playerState.value = _playerState.value.copy(shuffleEnabled = shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playerState.value = _playerState.value.copy(repeatMode = repeatMode)
        }
    }

    suspend fun connect(): Result<Unit> = runCatching {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controller = controllerFuture!!.await()
        controller!!.addListener(playerListener)
        Timber.d("MediaController connected")
    }

    fun disconnect() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val mediaItems = songs.map { song ->
            PlaybackService.buildMediaItem(
                id = song.id,
                url = song.url,
                title = song.title,
                artist = song.artist,
                album = song.album
            )
        }
        controller?.run {
            setMediaItems(mediaItems, startIndex, 0L)
            prepare()
            play()
        }
    }

    fun playSong(song: Song) {
        val item = PlaybackService.buildMediaItem(
            id = song.id,
            url = song.url,
            title = song.title,
            artist = song.artist,
            album = song.album
        )
        controller?.run {
            setMediaItem(item)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() = controller?.seekToNextMediaItem()
    fun skipPrevious() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun toggleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun getCurrentPosition(): Long = controller?.currentPosition ?: 0L
    fun getDuration(): Long = controller?.duration ?: 0L
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentMediaItem: MediaItem? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)
