package com.webdavmusic.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.webdavmusic.R
import com.webdavmusic.databinding.FragmentPlayerSheetBinding
import com.webdavmusic.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayerSheetFragment : BottomSheetDialogFragment() {

    private var _b: FragmentPlayerSheetBinding? = null
    private val b get() = _b!!
    private val vm: MainViewModel by activityViewModels()

    private val ticker = Handler(Looper.getMainLooper())
    private val tickTask = object : Runnable {
        override fun run() {
            updateSeek()
            updateLyrics()
            ticker.postDelayed(this, 500)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentPlayerSheetBinding.inflate(i, c, false).also { _b = it }.root

    override fun onStart() {
        super.onStart()
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)
            ?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        setupControls()
        observePlayer()
        observeLyrics()
    }

    private fun setupControls() {
        // TV: all buttons focusable, no touch-mode focus
        listOf(b.btnPlayPause, b.btnNext, b.btnPrev,
               b.btnShuffle, b.btnRepeat, b.btnFavorite,
               b.btnToggleLyrics, b.btnClose).forEach {
            it.isFocusable = true; it.isFocusableInTouchMode = false
        }
        b.seekBar.isFocusable = true

        b.btnClose.setOnClickListener       { dismiss() }
        b.btnPlayPause.setOnClickListener   { vm.togglePlayPause() }
        b.btnNext.setOnClickListener        { vm.skipNext() }
        b.btnPrev.setOnClickListener        { vm.skipPrevious() }
        b.btnShuffle.setOnClickListener     { vm.toggleShuffle() }
        b.btnRepeat.setOnClickListener      { vm.cycleRepeat() }
        b.btnToggleLyrics.setOnClickListener {
            val visible = b.lyricsPanel.isVisible
            b.lyricsPanel.isVisible = !visible
            b.btnToggleLyrics.alpha = if (!visible) 1f else 0.5f
        }
        b.btnFavorite.setOnClickListener {
            val id = vm.playerState.value.currentId
            (vm.allSongs.value + vm.localSongs.value).find { it.id == id }
                ?.let { vm.toggleFavorite(it) }
        }

        b.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) { val d = vm.player.duration(); if (d > 0) vm.seekTo(p.toLong() * d / 1000L) }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
        })

        // Rotary knob / car dial
        b.seekBar.setOnGenericMotionListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_SCROLL) {
                vm.seekTo(vm.player.currentPosition() + (ev.getAxisValue(MotionEvent.AXIS_SCROLL) * 5000L).toLong())
                true
            } else false
        }

        // D-pad: focus order in player
        b.btnPrev.nextFocusRightId      = R.id.btn_play_pause
        b.btnPlayPause.nextFocusLeftId  = R.id.btn_prev
        b.btnPlayPause.nextFocusRightId = R.id.btn_next
        b.btnNext.nextFocusLeftId       = R.id.btn_play_pause
    }

    private fun observePlayer() {
        lifecycleScope.launch {
            vm.playerState.collect { st ->
                b.tvTitle.text  = st.title.ifEmpty { getString(R.string.no_track) }
                b.tvArtist.text = st.artist
                b.tvAlbum.text  = st.album

                b.btnPlayPause.setImageResource(if (st.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                b.loadingSpinner.isVisible = st.isBuffering
                b.btnShuffle.alpha = if (st.shuffle) 1f else 0.38f
                b.btnRepeat.setImageResource(if (st.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
                b.btnRepeat.alpha = if (st.repeatMode == Player.REPEAT_MODE_OFF) 0.38f else 1f

                val isFav = (vm.allSongs.value + vm.localSongs.value).find { it.id == st.currentId }?.isFavorite == true
                b.btnFavorite.setImageResource(if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border)

                if (st.artworkUri.isNotEmpty()) {
                    Glide.with(this@PlayerSheetFragment).load(st.artworkUri)
                        .placeholder(R.drawable.ic_music_note_large).into(b.ivAlbumArt)
                } else {
                    b.ivAlbumArt.setImageResource(R.drawable.ic_music_note_large)
                }
            }
        }
    }

    private fun observeLyrics() {
        lifecycleScope.launch {
            vm.lyricsLoading.collect { loading ->
                if (loading) b.lyricsView.showLoading()
            }
        }
        lifecycleScope.launch {
            vm.lyrics.collect { lyrics ->
                b.lyricsView.setLyrics(lyrics)
                // Auto-show lyrics panel when lyrics are available
                if (lyrics != null && !lyrics.isEmpty) {
                    b.lyricsPanel.isVisible = true
                    b.btnToggleLyrics.alpha = 1f
                }
            }
        }
    }

    private fun updateSeek() {
        if (_b == null) return
        val pos = vm.player.currentPosition()
        val dur = vm.player.duration()
        if (dur > 0) {
            b.seekBar.progress = (pos * 1000L / dur).toInt()
            b.tvCurrentTime.text = fmtMs(pos)
            b.tvTotalTime.text   = fmtMs(dur)
        }
    }

    private fun updateLyrics() {
        if (_b == null) return
        val pos = vm.player.currentPosition()
        b.lyricsView.updateProgress(pos, vm.lyrics.value)
    }

    private fun fmtMs(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

    override fun onResume()      { super.onResume();  ticker.post(tickTask) }
    override fun onPause()       { ticker.removeCallbacks(tickTask); super.onPause() }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
