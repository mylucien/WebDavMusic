package com.webdavmusic.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.webdavmusic.databinding.FragmentLibraryBinding
import com.webdavmusic.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var songAdapter: SongAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var artistAdapter: ArtistAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupTabs()
        observeData()
    }

    private fun setupAdapters() {
        songAdapter = SongAdapter { song ->
            val songs = viewModel.allSongs.value
            val index = songs.indexOf(song)
            viewModel.playSongs(songs, index.coerceAtLeast(0))
        }

        albumAdapter = AlbumAdapter { album ->
            lifecycleScope.launch {
                viewModel.getAlbumSongs(album).collect { songs ->
                    viewModel.playSongs(songs)
                }
            }
        }

        artistAdapter = ArtistAdapter { artist ->
            lifecycleScope.launch {
                viewModel.getArtistSongs(artist).collect { songs ->
                    viewModel.playSongs(songs)
                }
            }
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showSongs()
                    1 -> showAlbums()
                    2 -> showArtists()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        showSongs()
    }

    private fun showSongs() {
        binding.recyclerView.adapter = songAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        lifecycleScope.launch {
            viewModel.allSongs.collect { songAdapter.submitList(it) }
        }
    }

    private fun showAlbums() {
        binding.recyclerView.adapter = albumAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        lifecycleScope.launch {
            viewModel.albums.collect { albumAdapter.submitList(it) }
        }
    }

    private fun showArtists() {
        binding.recyclerView.adapter = artistAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        lifecycleScope.launch {
            viewModel.artists.collect { artistAdapter.submitList(it) }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.allSongs.collect { songs ->
                if (binding.tabLayout.selectedTabPosition == 0) {
                    songAdapter.submitList(songs)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
