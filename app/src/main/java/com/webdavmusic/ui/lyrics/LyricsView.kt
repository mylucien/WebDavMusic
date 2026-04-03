package com.webdavmusic.ui.lyrics

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webdavmusic.R
import com.webdavmusic.data.model.LyricLine
import com.webdavmusic.data.model.Lyrics

class LyricsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val recyclerView: RecyclerView
    private val noLyricsContainer: View
    private val loadingView: View
    private val adapter = LyricAdapter()
    private val layoutManager = LinearLayoutManager(context)

    private var currentIndex = -1
    var autoScroll = true

    init {
        val v = LayoutInflater.from(context).inflate(R.layout.view_lyrics, this, true)
        recyclerView        = v.findViewById(R.id.recycler_lyrics)
        noLyricsContainer   = v.findViewById(R.id.no_lyrics_container)
        loadingView         = v.findViewById(R.id.lyrics_loading)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        // Disable focus/scroll interference when inside a BottomSheet
        recyclerView.isFocusable = false
    }

    fun setLyrics(lyrics: Lyrics?) {
        currentIndex = -1
        if (lyrics == null || lyrics.isEmpty) {
            recyclerView.visibility = View.GONE
            noLyricsContainer.visibility = View.VISIBLE
            loadingView.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            noLyricsContainer.visibility = View.GONE
            loadingView.visibility = View.GONE
            adapter.submitList(lyrics.lines)
        }
    }

    fun showLoading() {
        recyclerView.visibility = View.GONE
        noLyricsContainer.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
    }

    /** Call every ~500ms with current playback position */
    fun updateProgress(posMs: Long, lyrics: Lyrics?) {
        lyrics ?: return
        val idx = lyrics.findCurrentIndex(posMs)
        if (idx == currentIndex || idx < 0) return
        currentIndex = idx
        adapter.setHighlight(idx)
        if (autoScroll) recyclerView.smoothScrollToPosition(idx)
    }

    fun clear() {
        currentIndex = -1
        adapter.submitList(emptyList())
        recyclerView.visibility = View.GONE
        noLyricsContainer.visibility = View.VISIBLE
        loadingView.visibility = View.GONE
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class LyricAdapter : RecyclerView.Adapter<LyricAdapter.VH>() {

    private var items = listOf<LyricLine>()
    private var highlighted = -1

    fun submitList(list: List<LyricLine>) { items = list; notifyDataSetChanged() }

    fun setHighlight(index: Int) {
        val old = highlighted
        highlighted = index
        if (old >= 0) notifyItemChanged(old)
        notifyItemChanged(highlighted)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyric_line, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos], pos == highlighted)

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvMain: TextView       = v.findViewById(R.id.tv_lyric_main)
        private val tvTranslated: TextView = v.findViewById(R.id.tv_lyric_translated)

        fun bind(line: LyricLine, active: Boolean) {
            tvMain.text = line.text
            tvMain.setTextColor(if (active) 0xFF6750A4.toInt() else 0xFF79747E.toInt())
            tvMain.textSize = if (active) 18f else 15f
            tvMain.alpha    = if (active) 1f else 0.72f

            if (line.translated.isNotEmpty()) {
                tvTranslated.visibility = View.VISIBLE
                tvTranslated.text = line.translated
                tvTranslated.setTextColor(if (active) 0xFF6750A4.toInt() else 0xFF938F99.toInt())
                tvTranslated.textSize = if (active) 13f else 12f
            } else {
                tvTranslated.visibility = View.GONE
            }

            itemView.animate().scaleX(if (active) 1.04f else 1f)
                .scaleY(if (active) 1.04f else 1f).setDuration(180).start()
        }
    }
}
