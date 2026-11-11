package com.nsfwbili.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import com.nsfwbili.app.MainActivity
import com.nsfwbili.app.data.LocalStore
import com.nsfwbili.app.databinding.ActivitySetupBinding
import com.nsfwbili.app.site.SiteLock
import com.nsfwbili.app.site.SiteRegistry

/** 首次启动引导：像输密码一样填站点地址，填对才进入 App */
class SetupActivity : BaseActivity() {
    private lateinit var b: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.unlock.setOnClickListener { submit() }
        b.addr.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { submit(); true } else false }
        b.addr.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { b.addrLayout.error = null }
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        })
    }

    private fun submit() {
        val input = b.addr.text?.toString().orEmpty()
        if (!SiteLock.unlocks(input)) { b.addrLayout.error = "地址不正确"; return }
        LocalStore.siteAddress = SiteLock.normalize(input)
        SiteRegistry.reload()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
