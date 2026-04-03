package com.webdavmusic.ui.home

import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.webdavmusic.R
import com.webdavmusic.data.model.Playlist
import com.webdavmusic.data.model.Song
import com.webdavmusic.databinding.ItemAlbumRowBinding
import com.webdavmusic.databinding.ItemArtistRowBinding
import com.webdavmusic.databinding.ItemPlaylistRowBinding
import com.webdavmusic.databinding.ItemSongRowBinding

private const val VT_SONG = 0; private const val VT_ALBUM = 1
private const val VT_ARTIST = 2; private const val VT_PLAYLIST = 3

class SongListAdapter(
    private val onPlay:     (Song) -> Unit,
    private val onFavorite: (Song) -> Unit,
    private val onQueue:    (Song) -> Unit,
    private val onPlayAll:  (List<Song>, Int) -> Unit,
    private val onPlaylist: (Playlist) -> Unit,
    private val onManagePlaylist: (Playlist) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class Mode { SONGS, ALBUMS, ARTISTS, PLAYLISTS }

    private var mode       = Mode.SONGS
    private var songs      = listOf<Song>()
    private var albums     = listOf<String>()
    private var artists    = listOf<String>()
    private var playlists  = listOf<Playlist>()
    private var allSongsCtx = listOf<Song>()       // full list for album/artist filtering
    private var nowPlayingId = ""

    fun setMode(m: Mode) { mode = m }
    fun setNowPlaying(id: String) { nowPlayingId = id; notifyDataSetChanged() }

    fun submitSongs(list: List<Song>) { songs = list; allSongsCtx = list; notifyDataSetChanged() }
    fun submitAlbums(list: List<String>, all: List<Song>) { albums = list; allSongsCtx = all; notifyDataSetChanged() }
    fun submitArtists(list: List<String>, all: List<Song>) { artists = list; allSongsCtx = all; notifyDataSetChanged() }
    fun submitPlaylists(list: List<Playlist>) { playlists = list; notifyDataSetChanged() }

    override fun getItemViewType(p: Int) = when (mode) {
        Mode.SONGS -> VT_SONG; Mode.ALBUMS -> VT_ALBUM
        Mode.ARTISTS -> VT_ARTIST; Mode.PLAYLISTS -> VT_PLAYLIST
    }
    override fun getItemCount() = when (mode) {
        Mode.SONGS -> songs.size; Mode.ALBUMS -> albums.size
        Mode.ARTISTS -> artists.size; Mode.PLAYLISTS -> playlists.size
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(p.context)
        return when (vt) {
            VT_SONG    -> SongVH(ItemSongRowBinding.inflate(inf, p, false))
            VT_ALBUM   -> AlbumVH(ItemAlbumRowBinding.inflate(inf, p, false))
            VT_ARTIST  -> ArtistVH(ItemArtistRowBinding.inflate(inf, p, false))
            else       -> PlaylistVH(ItemPlaylistRowBinding.inflate(inf, p, false))
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) = when (h) {
        is SongVH     -> h.bind(songs[pos], pos)
        is AlbumVH    -> h.bind(albums[pos])
        is ArtistVH   -> h.bind(artists[pos])
        is PlaylistVH -> h.bind(playlists[pos])
        else -> Unit
    }

    // ── Song row ──────────────────────────────────────────────────────────────
    inner class SongVH(private val b: ItemSongRowBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.isFocusable = true; b.root.isFocusableInTouchMode = false
            b.btnPlay.isFocusable = true; b.btnFavorite.isFocusable = true; b.btnQueue.isFocusable = true
            // D-pad next focus: play → favorite → queue → next row
            b.btnPlay.nextFocusRightId     = R.id.btn_favorite
            b.btnFavorite.nextFocusLeftId  = R.id.btn_play
            b.btnFavorite.nextFocusRightId = R.id.btn_queue
            b.btnQueue.nextFocusLeftId     = R.id.btn_favorite
        }
        fun bind(song: Song, pos: Int) {
            b.tvTitle.text    = song.title
            b.tvArtist.text   = song.artist
            b.tvAlbum.text    = song.album
            b.tvFormat.text   = song.format.extension.uppercase()
            b.tvDuration.text = fmtMs(song.duration)
            b.tvSource.text   = if (song.accountId == -1L) "📱" else "☁️"
            b.btnFavorite.setImageResource(if (song.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            // Highlight currently playing
            b.root.isSelected = song.id == nowPlayingId
            b.tvTitle.alpha   = if (song.id == nowPlayingId) 1f else 0.95f

            b.root.setOnClickListener        { onPlayAll(songs, pos) }
            b.btnPlay.setOnClickListener     { onPlayAll(songs, pos) }
            b.btnFavorite.setOnClickListener { onFavorite(song) }
            b.btnQueue.setOnClickListener    { onQueue(song) }
        }
    }

    inner class AlbumVH(private val b: ItemAlbumRowBinding) : RecyclerView.ViewHolder(b.root) {
        init { b.root.isFocusable = true; b.root.isFocusableInTouchMode = false }
        fun bind(album: String) {
            b.tvAlbumName.text = album
            b.root.setOnClickListener {
                val s = allSongsCtx.filter { it.album == album }
                if (s.isNotEmpty()) onPlayAll(s, 0)
            }
        }
    }

    inner class ArtistVH(private val b: ItemArtistRowBinding) : RecyclerView.ViewHolder(b.root) {
        init { b.root.isFocusable = true; b.root.isFocusableInTouchMode = false }
        fun bind(artist: String) {
            b.tvArtistName.text = artist
            b.root.setOnClickListener {
                val s = allSongsCtx.filter { it.artist == artist }
                if (s.isNotEmpty()) onPlayAll(s, 0)
            }
        }
    }

    inner class PlaylistVH(private val b: ItemPlaylistRowBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.isFocusable = true; b.root.isFocusableInTouchMode = false
        }
        fun bind(pl: Playlist) {
            b.tvPlaylistName.text  = pl.name
            b.tvPlaylistBadge.text = if (pl.isAutoGenerated) "自动" else "手动"
            b.root.setOnClickListener  { onPlaylist(pl) }
            b.root.setOnLongClickListener { onManagePlaylist(pl); true }
        }
    }
}

fun fmtMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
}
