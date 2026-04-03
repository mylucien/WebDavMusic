package com.webdavmusic.ui.folder

import android.os.Bundle
import android.view.*
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.webdavmusic.R
import com.webdavmusic.data.model.BrowseItem
import com.webdavmusic.data.model.WebDavAccount
import com.webdavmusic.databinding.FragmentFolderPickerBinding
import com.webdavmusic.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Folder picker bottom sheet:
 * - WebDAV mode: navigate WebDAV directories, select one or more, click Scan
 * - Local mode: list device music directories (from MediaStore), checkboxes
 */
@AndroidEntryPoint
class FolderPickerFragment : BottomSheetDialogFragment() {

    private var _b: FragmentFolderPickerBinding? = null
    private val b get() = _b!!
    private val vm: MainViewModel by activityViewModels()

    private val accountId by lazy { arguments?.getLong(ARG_ACCOUNT_ID, -1L) ?: -1L }
    private val isLocal   by lazy { accountId == -1L }

    private lateinit var adapter: FolderAdapter
    private val selectedPaths = mutableSetOf<String>()
    private val pathStack = ArrayDeque<String>()  // navigation history for WebDAV

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentFolderPickerBinding.inflate(i, c, false).also { _b = it }.root

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.85).toInt())
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        b.tvFolderTitle.text = if (isLocal) "选择本地音乐文件夹" else "选择 WebDAV 文件夹"

        adapter = FolderAdapter(
            onToggle = { item -> if (item.isDirectory) toggleSelect(item.path) else toggleSelect(item.path) },
            onNavigate = { item -> if (item.isDirectory && !isLocal) navigate(item.path) },
            selected = selectedPaths
        )

        b.rvFolders.adapter = adapter
        b.rvFolders.layoutManager = LinearLayoutManager(requireContext())

        b.btnSelectAll.setOnClickListener { selectAll() }
        b.btnCancelFolder.setOnClickListener { dismiss() }
        b.btnConfirmScan.setOnClickListener { startScan() }

        loadFolder("/")
    }

    private fun loadFolder(path: String) {
        if (isLocal) {
            loadLocalFolders()
        } else {
            loadWebDavFolder(path)
        }
        updateBreadcrumb()
    }

    private fun loadLocalFolders() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val folders = vm.getLocalTopFolders()
            val items = folders.map { BrowseItem(it, it.substringAfterLast('/').ifEmpty { it }, false) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                adapter.submitList(items)
            }
        }
    }

    private fun loadWebDavFolder(path: String) {
        lifecycleScope.launch {
            val account = vm.accounts.value.find { it.id == accountId } ?: return@launch
            b.rvFolders.isVisible = false
            val result = vm.listWebDavFolder(account, path)
            result.onSuccess { items ->
                // Show directories first, then audio files
                val displayItems = items.sortedWith(compareByDescending<BrowseItem> { it.isDirectory }.thenBy { it.name.lowercase() })
                adapter.submitList(displayItems)
                b.rvFolders.isVisible = true
            }.onFailure {
                b.rvFolders.isVisible = true
            }
        }
    }

    private fun navigate(path: String) {
        pathStack.addLast(path)
        loadWebDavFolder(path)
        updateBreadcrumb()
    }

    private fun updateBreadcrumb() {
        b.breadcrumbBar.removeAllViews()
        val root = TextView(requireContext()).apply {
            text = "/"
            textSize = 14f
            setPadding(8, 0, 8, 0)
            setOnClickListener { while (pathStack.size > 1) pathStack.removeLast(); loadFolder("/") }
        }
        b.breadcrumbBar.addView(root)
        pathStack.forEach { seg ->
            val arrow = TextView(requireContext()).apply { text = " › "; textSize = 14f }
            val label = TextView(requireContext()).apply {
                text = seg.trimEnd('/').substringAfterLast('/')
                textSize = 14f; setPadding(8, 0, 8, 0)
            }
            b.breadcrumbBar.addView(arrow)
            b.breadcrumbBar.addView(label)
        }
    }

    private fun toggleSelect(path: String) {
        if (path in selectedPaths) selectedPaths.remove(path) else selectedPaths.add(path)
        adapter.notifyDataSetChanged()
        b.btnConfirmScan.text = if (selectedPaths.isEmpty()) "扫描全部" else "扫描选中 (${selectedPaths.size})"
    }

    private fun selectAll() {
        val items = adapter.currentList
        if (selectedPaths.size == items.size) {
            selectedPaths.clear()
        } else {
            selectedPaths.addAll(items.map { it.path })
        }
        adapter.notifyDataSetChanged()
        b.btnConfirmScan.text = if (selectedPaths.isEmpty()) "扫描全部" else "扫描选中 (${selectedPaths.size})"
    }

    private fun startScan() {
        val folders = if (selectedPaths.isEmpty()) emptyList() else selectedPaths.toList()
        if (isLocal) {
            vm.scanLocalMusic(folders)
        } else {
            val account = vm.accounts.value.find { it.id == accountId } ?: return
            vm.scanAccount(account, folders)
        }
        dismiss()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    companion object {
        private const val ARG_ACCOUNT_ID = "account_id"
        fun forWebDav(accountId: Long) = FolderPickerFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ACCOUNT_ID, accountId) }
        }
        fun forLocal() = FolderPickerFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ACCOUNT_ID, -1L) }
        }
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class FolderAdapter(
    private val onToggle:   (BrowseItem) -> Unit,
    private val onNavigate: (BrowseItem) -> Unit,
    private val selected:   Set<String>
) : ListAdapter<BrowseItem, FolderAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_folder, p, false)
    )

    override fun getItemCount() = currentList.size

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val icon:    ImageView = v.findViewById(R.id.iv_folder_icon)
        private val name:    TextView  = v.findViewById(R.id.tv_folder_name)
        private val cb:      CheckBox  = v.findViewById(R.id.cb_folder)
        private val chevron: ImageView = v.findViewById(R.id.iv_chevron)

        fun bind(item: BrowseItem) {
            name.text = item.name
            icon.setImageResource(if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_music_note_large)
            cb.isChecked = item.path in selected
            chevron.isVisible = item.isDirectory

            itemView.setOnClickListener {
                if (item.isDirectory) {
                    onNavigate(item)
                } else {
                    onToggle(item)
                }
            }
            itemView.setOnLongClickListener { onToggle(item); true }
            cb.setOnClickListener { onToggle(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BrowseItem>() {
            override fun areItemsTheSame(a: BrowseItem, b: BrowseItem) = a.path == b.path
            override fun areContentsTheSame(a: BrowseItem, b: BrowseItem) = a == b
        }
    }
}
