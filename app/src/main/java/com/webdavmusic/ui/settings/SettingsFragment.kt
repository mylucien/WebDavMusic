package com.webdavmusic.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webdavmusic.data.model.WebDavAccount
import com.webdavmusic.databinding.FragmentSettingsBinding
import com.webdavmusic.databinding.DialogAccountBinding
import com.webdavmusic.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var accountAdapter: AccountAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        accountAdapter = AccountAdapter(
            onEdit = { showAccountDialog(it) },
            onDelete = { confirmDeleteAccount(it) },
            onScan = { viewModel.scanAccount(it) },
            onTest = { account ->
                viewModel.testConnection(account) { success, msg ->
                    requireActivity().runOnUiThread {
                        com.google.android.material.snackbar.Snackbar
                            .make(binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        )

        binding.rvAccounts.apply {
            adapter = accountAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        binding.fabAddAccount.setOnClickListener { showAccountDialog(null) }

        lifecycleScope.launch {
            viewModel.accounts.collect { accountAdapter.submitList(it) }
        }
    }

    private fun showAccountDialog(existing: WebDavAccount?) {
        val dialogBinding = DialogAccountBinding.inflate(layoutInflater)

        existing?.let {
            dialogBinding.etName.setText(it.name)
            dialogBinding.etUrl.setText(it.url)
            dialogBinding.etUsername.setText(it.username)
            dialogBinding.etPassword.setText(it.password)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "添加 WebDAV 账户" else "编辑账户")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val url = dialogBinding.etUrl.text.toString().trim()
                val user = dialogBinding.etUsername.text.toString().trim()
                val pass = dialogBinding.etPassword.text.toString()

                if (name.isEmpty() || url.isEmpty()) return@setPositiveButton

                val account = WebDavAccount(
                    id = existing?.id ?: 0,
                    name = name,
                    url = url,
                    username = user,
                    password = pass
                )

                if (existing == null) viewModel.addAccount(account)
                else viewModel.updateAccount(account)
            }
            .setNeutralButton("测试连接") { _, _ ->
                val account = WebDavAccount(
                    id = existing?.id ?: 0,
                    name = dialogBinding.etName.text.toString(),
                    url = dialogBinding.etUrl.text.toString().trim(),
                    username = dialogBinding.etUsername.text.toString().trim(),
                    password = dialogBinding.etPassword.text.toString()
                )
                viewModel.testConnection(account) { success, msg ->
                    requireActivity().runOnUiThread {
                        com.google.android.material.snackbar.Snackbar
                            .make(binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteAccount(account: WebDavAccount) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除账户")
            .setMessage("确定删除「${account.name}」？这将同时删除该账户下所有已扫描的歌曲。")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteAccount(account) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
