package com.webdavmusic.ui.settings

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webdavmusic.data.model.WebDavAccount
import com.webdavmusic.databinding.ItemAccountBinding
import java.text.SimpleDateFormat
import java.util.*

class AccountAdapter(
    private val onEdit:   (WebDavAccount) -> Unit,
    private val onDelete: (WebDavAccount) -> Unit,
    private val onScan:   (WebDavAccount) -> Unit,
    private val onTest:   (WebDavAccount) -> Unit
) : ListAdapter<WebDavAccount, AccountAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemAccountBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    inner class VH(private val b: ItemAccountBinding) : RecyclerView.ViewHolder(b.root) {
        init { b.root.isFocusable = true }
        fun bind(a: WebDavAccount) {
            b.tvAccountName.text = a.name
            b.tvAccountUrl.text  = a.url
            b.tvLastScanned.text = if (a.lastScanned > 0)
                "上次扫描: " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(a.lastScanned)
            else "尚未扫描"
            b.btnEdit.setOnClickListener   { onEdit(a) }
            b.btnDelete.setOnClickListener { onDelete(a) }
            b.btnScan.setOnClickListener   { onScan(a) }
            b.btnTest.setOnClickListener   { onTest(a) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WebDavAccount>() {
            override fun areItemsTheSame(a: WebDavAccount, b: WebDavAccount) = a.id == b.id
            override fun areContentsTheSame(a: WebDavAccount, b: WebDavAccount) = a == b
        }
    }
}
