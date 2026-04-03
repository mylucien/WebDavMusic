package com.webdavmusic.ui

import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.webdavmusic.databinding.FragmentSettingsPanelBinding
import com.webdavmusic.ui.folder.FolderPickerFragment
import com.webdavmusic.ui.settings.AccountAdapter
import com.webdavmusic.ui.settings.AccountDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsPanelFragment : BottomSheetDialogFragment() {

    private var _b: FragmentSettingsPanelBinding? = null
    private val b get() = _b!!
    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSettingsPanelBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        val adapter = AccountAdapter(
            onEdit   = { AccountDialogFragment.newInstance(it).show(childFragmentManager, "edit") },
            onDelete = { vm.deleteAccount(it) },
            onScan   = { a ->
                // Open folder picker for this account
                FolderPickerFragment.forWebDav(a.id).show(childFragmentManager, "fp_${a.id}")
                dismiss()
            },
            onTest = { a ->
                vm.testConnection(a) { _, msg ->
                    com.google.android.material.snackbar.Snackbar
                        .make(b.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                }
            }
        )

        b.rvAccounts.apply { this.adapter = adapter; layoutManager = LinearLayoutManager(requireContext()) }
        b.btnAddAccount.setOnClickListener { AccountDialogFragment().show(childFragmentManager, "add") }
        b.btnScanAll.setOnClickListener    { vm.scanAll(); dismiss() }
        b.btnScanLocal.setOnClickListener  {
            FolderPickerFragment.forLocal().show(childFragmentManager, "fp_local"); dismiss()
        }

        lifecycleScope.launch {
            vm.accounts.collect {
                adapter.submitList(it)
                b.tvNoAccounts.isVisible = it.isEmpty()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
