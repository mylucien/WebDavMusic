package com.webdavmusic.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webdavmusic.data.model.WebDavAccount
import com.webdavmusic.databinding.ItemAccountBinding
import java.text.SimpleDateFormat
import java.util.*

class AccountAdapter(
    private val onEdit: (WebDavAccount) -> Unit,
    private val onDelete: (WebDavAccount) -> Unit,
    private val onScan: (WebDavAccount) -> Unit,
    private val onTest: (WebDavAccount) -> Unit
) : ListAdapter<WebDavAccount, AccountAdapter.AccountViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val b = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AccountViewHolder(b)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AccountViewHolder(private val b: ItemAccountBinding) :
        RecyclerView.ViewHolder(b.root) {

        init { b.root.isFocusable = true }

        fun bind(account: WebDavAccount) {
            b.tvAccountName.text = account.name
            b.tvAccountUrl.text = account.url
            b.tvLastScanned.text = if (account.lastScanned > 0) {
                "上次扫描: " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(Date(account.lastScanned))
            } else "尚未扫描"

            b.btnEdit.setOnClickListener { onEdit(account) }
            b.btnDelete.setOnClickListener { onDelete(account) }
            b.btnScan.setOnClickListener { onScan(account) }
            b.btnTest.setOnClickListener { onTest(account) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WebDavAccount>() {
            override fun areItemsTheSame(a: WebDavAccount, b: WebDavAccount) = a.id == b.id
            override fun areContentsTheSame(a: WebDavAccount, b: WebDavAccount) = a == b
        }
    }
}
