package com.webdavmusic.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.webdavmusic.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            _state.value = _state.value.copy(
                currentId = item?.mediaId ?: "",
                title = item?.mediaMetadata?.title?.toString() ?: "",
                artist = item?.mediaMetadata?.artist?.toString() ?: "",
                album = item?.mediaMetadata?.albumTitle?.toString() ?: "",
                artworkUri = item?.mediaMetadata?.artworkUri?.toString() ?: ""
            )
        }
        override fun onPlaybackStateChanged(s: Int) {
            _state.value = _state.value.copy(isBuffering = s == Player.STATE_BUFFERING)
        }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _state.value = _state.value.copy(shuffle = enabled)
        }
        override fun onRepeatModeChanged(mode: Int) {
            _state.value = _state.value.copy(repeatMode = mode)
        }
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            _state.value = _state.value.copy(currentIndex = controller?.currentMediaItemIndex ?: 0)
        }
    }

    fun connect() {
        // BUG FIX: was using await() which requires a coroutine context; use addListener instead
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture!!.addListener({
            runCatching {
                controller = controllerFuture!!.get()
                controller!!.addListener(listener)
                // Restore state from current session
                controller!!.currentMediaItem?.let { item ->
                    _state.value = _state.value.copy(
                        currentId = item.mediaId,
                        title = item.mediaMetadata.title?.toString() ?: "",
                        artist = item.mediaMetadata.artist?.toString() ?: "",
                        album = item.mediaMetadata.albumTitle?.toString() ?: "",
                        isPlaying = controller!!.isPlaying,
                        shuffle = controller!!.shuffleModeEnabled,
                        repeatMode = controller!!.repeatMode
                    )
                }
                Timber.d("MediaController connected")
            }.onFailure { Timber.e(it, "MediaController connect failed") }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val items = songs.map { PlaybackService.buildMediaItem(it) }
        controller?.run {
            setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
            prepare()
            play()
        }
    }

    fun playSong(song: Song) = playSongs(listOf(song), 0)

    fun addToQueue(song: Song) {
        controller?.addMediaItem(PlaybackService.buildMediaItem(song))
    }

    fun togglePlayPause() = controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun skipNext() = controller?.seekToNextMediaItem()
    fun skipPrevious() {
        controller?.let {
            if (it.currentPosition > 3000L) it.seekTo(0L)
            else it.seekToPreviousMediaItem()
        }
    }
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun currentPosition() = controller?.currentPosition ?: 0L
    fun duration() = controller?.duration?.takeIf { it > 0 } ?: 0L
    fun isConnected() = controller != null
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentId: String = "",
    val currentIndex: Int = 0,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String = "",
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
) {
    val hasTrack get() = title.isNotEmpty()
}
