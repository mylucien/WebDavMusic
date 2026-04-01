package com.webdavmusic.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.webdavmusic.R
import com.webdavmusic.databinding.FragmentPlayerBinding
import com.webdavmusic.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupControls()
        observePlayerState()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener { viewModel.togglePlayPause() }
        binding.btnNext.setOnClickListener { viewModel.skipNext() }
        binding.btnPrevious.setOnClickListener { viewModel.skipPrevious() }
        binding.btnShuffle.setOnClickListener { viewModel.toggleShuffle() }
        binding.btnRepeat.setOnClickListener { viewModel.toggleRepeat() }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = viewModel.playerController.getDuration()
                    if (duration > 0) {
                        viewModel.seekTo((progress.toLong() * duration) / 1000L)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {}
        })

        // D-pad / rotary: make buttons focusable
        listOf(
            binding.btnPlayPause, binding.btnNext, binding.btnPrevious,
            binding.btnShuffle, binding.btnRepeat, binding.seekBar
        ).forEach {
            it.isFocusable = true
            it.nextFocusDownId = View.NO_ID
        }

        // Rotary scroll on seekbar (car / TV knob)
        binding.seekBar.setOnGenericMotionListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_SCROLL) {
                val scroll = event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL)
                val current = viewModel.playerController.getCurrentPosition()
                viewModel.seekTo(current + (scroll * 5000L).toLong())
                true
            } else false
        }
    }

    private fun observePlayerState() {
        lifecycleScope.launch {
            viewModel.playerState.collect { state ->
                binding.tvTitle.text = state.title.ifEmpty { getString(R.string.no_track) }
                binding.tvArtist.text = state.artist
                binding.tvAlbum.text = state.album

                binding.btnPlayPause.setImageResource(
                    if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
                binding.progressBar.visibility =
                    if (state.isBuffering) View.VISIBLE else View.GONE

                // Shuffle
                binding.btnShuffle.alpha = if (state.shuffleEnabled) 1.0f else 0.4f

                // Repeat
                binding.btnRepeat.setImageResource(
                    when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                        Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                        else -> R.drawable.ic_repeat
                    }
                )
                binding.btnRepeat.alpha = if (state.repeatMode != Player.REPEAT_MODE_OFF) 1.0f else 0.4f
            }
        }
    }

    private fun updateProgress() {
        val controller = viewModel.playerController
        val pos = controller.getCurrentPosition()
        val dur = controller.getDuration()
        if (dur > 0) {
            binding.seekBar.progress = ((pos * 1000L) / dur).toInt()
            binding.tvCurrentTime.text = formatMs(pos)
            binding.tvTotalTime.text = formatMs(dur)
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    override fun onResume() {
        super.onResume()
        progressHandler.post(progressRunnable)
    }

    override fun onPause() {
        progressHandler.removeCallbacks(progressRunnable)
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
