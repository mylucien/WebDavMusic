package com.webdavmusic.ui.settings

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import com.webdavmusic.data.model.WebDavAccount
import com.webdavmusic.databinding.DialogAccountBinding
import com.webdavmusic.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountDialogFragment : DialogFragment() {

    private var _b: DialogAccountBinding? = null
    private val b get() = _b!!
    private val vm: MainViewModel by activityViewModels()

    // Read existing account from individual Bundle extras (avoids Serializable issues)
    private val existingId    by lazy { arguments?.getLong(ARG_ID, -1L) ?: -1L }
    private val existingName  by lazy { arguments?.getString(ARG_NAME, "") ?: "" }
    private val existingUrl   by lazy { arguments?.getString(ARG_URL, "") ?: "" }
    private val existingUser  by lazy { arguments?.getString(ARG_USER, "") ?: "" }
    private val existingPass  by lazy { arguments?.getString(ARG_PASS, "") ?: "" }
    private val isEditing     get() = existingId != -1L

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        DialogAccountBinding.inflate(i, c, false).also { _b = it }.root

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.tvTitle.text = if (isEditing) "编辑账户" else "添加 WebDAV 账户"

        if (isEditing) {
            b.etName.setText(existingName)
            b.etUrl.setText(existingUrl)
            b.etUsername.setText(existingUser)
            b.etPassword.setText(existingPass)
        }

        b.etPassword.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }
        b.btnSave.setOnClickListener   { save() }
        b.btnCancel.setOnClickListener { dismiss() }
        b.btnTest.setOnClickListener   { test() }
    }

    private fun buildAccount(): WebDavAccount? {
        val name = b.etName.text?.toString()?.trim() ?: ""
        val url  = b.etUrl.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) { b.etName.error = "请输入账户名称"; return null }
        if (url.isEmpty())  { b.etUrl.error  = "请输入 WebDAV 地址"; return null }
        return WebDavAccount(
            id       = if (isEditing) existingId else 0L,
            name     = name,
            url      = url,
            username = b.etUsername.text?.toString()?.trim() ?: "",
            password = b.etPassword.text?.toString() ?: ""
        )
    }

    private fun save() {
        val a = buildAccount() ?: return
        if (isEditing) vm.updateAccount(a) else vm.addAccount(a)
        dismiss()
    }

    private fun test() {
        val a = buildAccount() ?: return
        b.btnTest.isEnabled = false
        b.btnTest.text = "测试中…"
        vm.testConnection(a) { _, msg ->
            if (isAdded) {
                b.btnTest.isEnabled = true
                b.btnTest.text = "测试连接"
                Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    companion object {
        private const val ARG_ID   = "id"
        private const val ARG_NAME = "name"
        private const val ARG_URL  = "url"
        private const val ARG_USER = "user"
        private const val ARG_PASS = "pass"

        fun newInstance(a: WebDavAccount) = AccountDialogFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID,   a.id)
                putString(ARG_NAME, a.name)
                putString(ARG_URL,  a.url)
                putString(ARG_USER, a.username)
                putString(ARG_PASS, a.password)
            }
        }
    }
}
