package com.webdavmusic.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webdavmusic.data.model.Song
import com.webdavmusic.databinding.ItemAlbumBinding
import com.webdavmusic.databinding.ItemArtistBinding
import com.webdavmusic.databinding.ItemSongBinding

class SongAdapter(
    private val onSongClick: (Song) -> Unit
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onSongClick(getItem(pos))
            }
            // TV / D-pad focus ring
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
        }

        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.tvAlbum.text = song.album
            binding.tvFormat.text = song.format.extension.uppercase()
            val minutes = (song.duration / 1000) / 60
            val seconds = (song.duration / 1000) % 60
            binding.tvDuration.text = if (song.duration > 0) "%d:%02d".format(minutes, seconds) else ""
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
            override fun areContentsTheSame(a: Song, b: Song) = a == b
        }
    }
}

// ─── Album Adapter ────────────────────────────────────────────────────────────

class AlbumAdapter(
    private val onAlbumClick: (String) -> Unit
) : ListAdapter<String, AlbumAdapter.AlbumViewHolder>(StringDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlbumViewHolder(private val binding: ItemAlbumBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onAlbumClick(getItem(pos))
            }
            binding.root.isFocusable = true
        }
        fun bind(album: String) { binding.tvAlbumName.text = album }
    }
}

// ─── Artist Adapter ───────────────────────────────────────────────────────────

class ArtistAdapter(
    private val onArtistClick: (String) -> Unit
) : ListAdapter<String, ArtistAdapter.ArtistViewHolder>(StringDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val binding = ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ArtistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ArtistViewHolder(private val binding: ItemArtistBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onArtistClick(getItem(pos))
            }
            binding.root.isFocusable = true
        }
        fun bind(artist: String) { binding.tvArtistName.text = artist }
    }
}

private val StringDiff = object : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(a: String, b: String) = a == b
    override fun areContentsTheSame(a: String, b: String) = a == b
}
