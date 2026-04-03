package com.webdavmusic.ui

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.webdavmusic.R
import com.webdavmusic.data.model.*
import com.webdavmusic.databinding.FragmentMainBinding
import com.webdavmusic.ui.folder.FolderPickerFragment
import com.webdavmusic.ui.home.SongListAdapter
import com.webdavmusic.ui.player.PlayerSheetFragment
import com.webdavmusic.ui.playlist.PlaylistManageDialogFragment
import com.webdavmusic.ui.settings.AccountDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainFragment : Fragment() {

    private var _b: FragmentMainBinding? = null
    private val b get() = _b!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var songAdapter: SongListAdapter
    private var listJob: Job? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMainBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        initNavLabels()
        setupList()
        setupNav()
        setupNowPlaying()
        setupFab()
        setupSearch()
        observe()
        vm.setTab(LibraryTab.ALL)
    }

    // ── Nav ───────────────────────────────────────────────────────────────────
    private fun initNavLabels() {
        mapOf(
            b.navAll       to "🎵  所有歌曲",
            b.navLocal     to "📱  本地音乐",
            b.navAlbums    to "💿  专辑",
            b.navArtists   to "🎤  艺术家",
            b.navFavorites to "❤️  收藏",
            b.navPlaylists to "📋  播放列表",
            b.navSettings  to "⚙️  账户与设置"
        ).forEach { (inc, text) -> (inc.root as? TextView)?.text = text }

        // D-pad: RIGHT from any nav button → list; LEFT from list → nav
        val navRoots = listOf(b.navAll.root, b.navLocal.root, b.navAlbums.root,
            b.navArtists.root, b.navFavorites.root, b.navPlaylists.root, b.navSettings.root)
        navRoots.forEach { it.nextFocusRightId = R.id.recycler_view }
        b.recyclerView.nextFocusLeftId = R.id.nav_all
        b.recyclerView.nextFocusDownId = R.id.btn_bar_play_pause
    }

    private fun setupNav() {
        b.navAll.root.setOnClickListener       { vm.setTab(LibraryTab.ALL) }
        b.navLocal.root.setOnClickListener     { vm.setTab(LibraryTab.LOCAL) }
        b.navAlbums.root.setOnClickListener    { vm.setTab(LibraryTab.ALBUMS) }
        b.navArtists.root.setOnClickListener   { vm.setTab(LibraryTab.ARTISTS) }
        b.navFavorites.root.setOnClickListener { vm.setTab(LibraryTab.FAVORITES) }
        b.navPlaylists.root.setOnClickListener { vm.setTab(LibraryTab.PLAYLISTS) }
        b.navSettings.root.setOnClickListener  { openSettings() }
        b.btnAddAccount.setOnClickListener     { AccountDialogFragment().show(childFragmentManager, "add") }
    }

    // ── Song list ─────────────────────────────────────────────────────────────
    private fun setupList() {
        songAdapter = SongListAdapter(
            onPlay     = { song -> vm.playSong(song) },
            onFavorite = { song -> vm.toggleFavorite(song) },
            onQueue    = { song -> vm.addToQueue(song) },
            onPlayAll  = { songs, idx -> vm.playSongs(songs, idx) },
            onPlaylist = { pl ->
                lifecycleScope.launch {
                    val songs = vm.getPlaylistSongs(pl.id).first()
                    if (songs.isNotEmpty()) vm.playSongs(songs)
                }
            },
            onManagePlaylist = { pl ->
                PlaylistManageDialogFragment.of(pl).show(childFragmentManager, "plManage")
            }
        )
        b.recyclerView.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(requireContext())
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
    }

    // ── Now-playing bar ───────────────────────────────────────────────────────
    private fun setupNowPlaying() {
        b.nowPlayingBar.setOnClickListener   { openPlayer() }
        b.btnBarPlayPause.setOnClickListener { vm.togglePlayPause() }
        b.btnBarNext.setOnClickListener      { vm.skipNext() }
        b.btnBarPrev.setOnClickListener      { vm.skipPrevious() }
        listOf(b.btnBarPlayPause, b.btnBarNext, b.btnBarPrev).forEach {
            it.nextFocusUpId = R.id.recycler_view
        }
    }

    // ── FAB: scan (with folder picker) ───────────────────────────────────────
    private fun setupFab() {
        b.fabScan.setOnClickListener {
            when (vm.activeTab.value) {
                LibraryTab.LOCAL -> openFolderPicker(isLocal = true)
                else -> {
                    val accounts = vm.accounts.value
                    when {
                        accounts.isEmpty() -> vm.scanAll()      // will show "please add account"
                        accounts.size == 1 -> openFolderPicker(isLocal = false, accountId = accounts[0].id)
                        else -> openScanMenu(accounts)
                    }
                }
            }
        }
        // Long-press = scan all without folder picker
        b.fabScan.setOnLongClickListener {
            when (vm.activeTab.value) {
                LibraryTab.LOCAL -> (activity as? MainActivity)?.requestStoragePermission()
                else -> vm.scanAll()
            }
            true
        }
    }

    private fun openFolderPicker(isLocal: Boolean, accountId: Long = -1L) {
        val tag = "folderPicker"
        if (childFragmentManager.findFragmentByTag(tag) != null) return
        if (isLocal) FolderPickerFragment.forLocal()
        else FolderPickerFragment.forWebDav(accountId)
        .show(childFragmentManager, tag)
    }

    private fun openScanMenu(accounts: List<WebDavAccount>) {
        val items = accounts.map { it.name }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择账户扫描")
            .setItems(items) { _, idx ->
                openFolderPicker(isLocal = false, accountId = accounts[idx].id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupSearch() {
        b.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = true.also { vm.setSearchQuery(q ?: "") }
            override fun onQueryTextChange(q: String?) = true.also { vm.setSearchQuery(q ?: "") }
        })
    }

    // ── Observe ───────────────────────────────────────────────────────────────
    private fun observe() {
        lifecycleScope.launch { vm.activeTab.collectLatest { switchTab(it) } }

        lifecycleScope.launch {
            vm.playerState.collectLatest { st ->
                b.nowPlayingBar.isVisible = st.hasTrack
                b.tvBarTitle.text         = st.title
                b.tvBarArtist.text        = st.artist
                b.btnBarPlayPause.setImageResource(if (st.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                b.barProgress.isVisible   = st.isBuffering
                songAdapter.setNowPlaying(st.currentId)
            }
        }

        lifecycleScope.launch {
            vm.scanProgress.collectLatest { p ->
                val show = p.isRunning || p.error != null
                b.scanStatusBar.isVisible = show
                b.tvScanStatus.text = when {
                    p.isRunning     -> "🔍 扫描「${p.accountName}」… ${p.found} 首"
                    p.error != null -> "❌ ${p.error}"
                    else            -> ""
                }
            }
        }

        lifecycleScope.launch {
            vm.accounts.collectLatest { accs ->
                b.btnAddAccount.isVisible = accs.isEmpty()
                b.tvEmptyHint.isVisible   = accs.isEmpty() && vm.totalCount.value == 0
            }
        }

        lifecycleScope.launch {
            vm.searchQuery.collectLatest { q ->
                if (q.length >= 2) {
                    listJob?.cancel()
                    listJob = lifecycleScope.launch { vm.searchResults.collectLatest { showSongs(it) } }
                }
            }
        }
    }

    // ── Tab switching ─────────────────────────────────────────────────────────
    private fun switchTab(tab: LibraryTab) {
        highlightNav(tab)
        b.searchView.setQuery("", false); vm.setSearchQuery("")
        b.tvSectionTitle.text = tabLabel(tab)

        listJob?.cancel()
        listJob = lifecycleScope.launch {
            when (tab) {
                LibraryTab.ALL       -> vm.allSongs.collectLatest   { showSongs(it) }
                LibraryTab.LOCAL     -> vm.localSongs.collectLatest { showSongs(it) }
                LibraryTab.FAVORITES -> vm.favorites.collectLatest  { showSongs(it) }
                LibraryTab.PLAYLISTS -> vm.playlists.collectLatest  { showPlaylists(it) }
                LibraryTab.ALBUMS    -> {
                    launch { vm.allSongs.collectLatest { songAdapter.submitAlbums(vm.albums.value, it) } }
                    vm.albums.collectLatest { songAdapter.submitAlbums(it, vm.allSongs.value) }
                }
                LibraryTab.ARTISTS   -> {
                    launch { vm.allSongs.collectLatest { songAdapter.submitArtists(vm.artists.value, it) } }
                    vm.artists.collectLatest { songAdapter.submitArtists(it, vm.allSongs.value) }
                }
            }
        }
    }

    private fun showSongs(list: List<Song>) {
        songAdapter.setMode(SongListAdapter.Mode.SONGS)
        songAdapter.submitSongs(list)
        b.tvEmpty.isVisible     = list.isEmpty()
        b.tvEmpty.text          = "没有找到歌曲"
        b.tvEmptyHint.isVisible = false
    }

    private fun showPlaylists(list: List<Playlist>) {
        songAdapter.setMode(SongListAdapter.Mode.PLAYLISTS)
        songAdapter.submitPlaylists(list)
        b.tvEmpty.isVisible     = list.isEmpty()
        b.tvEmpty.text          = "还没有播放列表"
        b.tvEmptyHint.isVisible = false
    }

    private fun highlightNav(tab: LibraryTab) {
        mapOf(
            LibraryTab.ALL       to b.navAll.root,
            LibraryTab.LOCAL     to b.navLocal.root,
            LibraryTab.ALBUMS    to b.navAlbums.root,
            LibraryTab.ARTISTS   to b.navArtists.root,
            LibraryTab.FAVORITES to b.navFavorites.root,
            LibraryTab.PLAYLISTS to b.navPlaylists.root
        ).forEach { (t, v) -> v.isSelected = (t == tab) }
    }

    private fun tabLabel(t: LibraryTab) = when (t) {
        LibraryTab.ALL -> "所有歌曲"; LibraryTab.LOCAL -> "本地音乐"
        LibraryTab.ALBUMS -> "专辑"; LibraryTab.ARTISTS -> "艺术家"
        LibraryTab.FAVORITES -> "我的收藏"; LibraryTab.PLAYLISTS -> "播放列表"
    }

    private fun openPlayer() {
        if (childFragmentManager.findFragmentByTag("player") == null)
            PlayerSheetFragment().show(childFragmentManager, "player")
    }

    private fun openSettings() {
        if (childFragmentManager.findFragmentByTag("settings") == null)
            SettingsPanelFragment().show(childFragmentManager, "settings")
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
