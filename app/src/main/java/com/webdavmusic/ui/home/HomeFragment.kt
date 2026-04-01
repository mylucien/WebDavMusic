package com.webdavmusic.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.webdavmusic.R
import com.webdavmusic.databinding.FragmentHomeBinding
import com.webdavmusic.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons()
        observeState()
    }

    private fun setupButtons() {
        binding.btnScanAll.setOnClickListener { viewModel.scanAllAccounts() }
        binding.btnAddAccount.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_settings)
        }
        binding.btnBrowse.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_library)
        }
        binding.miniPlayerCard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_player)
        }
        binding.btnMiniPlayPause.setOnClickListener { viewModel.togglePlayPause() }
        binding.btnMiniNext.setOnClickListener { viewModel.skipNext() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.totalSongCount.collect { count ->
                binding.tvSongCount.text = getString(R.string.songs_count, count)
            }
        }

        lifecycleScope.launch {
            viewModel.accounts.collect { accounts ->
                binding.tvAccountCount.text = getString(R.string.accounts_count, accounts.size)
                binding.emptyState.isVisible = accounts.isEmpty()
                binding.contentGroup.isVisible = accounts.isNotEmpty()
            }
        }

        lifecycleScope.launch {
            viewModel.scanProgress.collect { progress ->
                if (progress != null) {
                    binding.scanProgressGroup.isVisible = progress.isRunning
                    binding.tvScanStatus.text = if (progress.isRunning) {
                        "扫描中: ${progress.accountName}\n${progress.currentPath}\n已找到 ${progress.found} 首"
                    } else if (progress.error != null) {
                        "❌ 扫描失败: ${progress.error}"
                    } else {
                        "✅ 扫描完成，共 ${progress.found} 首"
                    }
                    binding.scanProgressBar.isVisible = progress.isRunning
                }
            }
        }

        lifecycleScope.launch {
            viewModel.playerState.collect { state ->
                val hasMedia = state.title.isNotEmpty()
                binding.miniPlayerCard.isVisible = hasMedia
                if (hasMedia) {
                    binding.tvMiniTitle.text = state.title
                    binding.tvMiniArtist.text = state.artist
                    binding.btnMiniPlayPause.setImageResource(
                        if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
