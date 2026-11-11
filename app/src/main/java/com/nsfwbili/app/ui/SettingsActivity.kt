package com.nsfwbili.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.nsfwbili.app.data.Account
import com.nsfwbili.app.data.ChallengeException
import com.nsfwbili.app.data.LocalStore
import com.nsfwbili.app.databinding.ActivitySettingsBinding
import com.nsfwbili.app.site.SiteRegistry
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {
    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"

        b.addressRow.text = "站点地址：••••••"
        b.addressRow.setOnClickListener { editAddress() }
        b.accountGroup.visibility = if (SiteRegistry.unlocked) android.view.View.VISIBLE else android.view.View.GONE
        val n = SiteRegistry.current.displayName
        b.syncHint.text = "打开后，在 App 里点的爱心和收藏会同步加到 $n 账号里；关闭时只保存在本机。\n爱心 = $n 的爱心，收藏 = $n 的稍後觀看。"
        b.syncSwitch.isChecked = LocalStore.syncToAccount
        b.syncSwitch.setOnCheckedChangeListener { _, on ->
            if (on && !LocalStore.loggedIn) {
                b.syncSwitch.isChecked = false
                Toast.makeText(this, "请先登录账号", Toast.LENGTH_SHORT).show()
            } else LocalStore.syncToAccount = on
        }
        val themes = arrayOf("跟随系统", "浅色", "深色")
        b.themeRow.text = "深色模式：${themes[LocalStore.themeMode]}"
        b.themeRow.setOnClickListener {
            AlertDialog.Builder(this).setTitle("深色模式")
                .setSingleChoiceItems(themes, LocalStore.themeMode) { d, which ->
                    d.dismiss()
                    LocalStore.themeMode = which
                    com.nsfwbili.app.App.applyTheme()
                }.show()
        }
        b.dynamicSwitch.isChecked = LocalStore.dynamicColor
        b.dynamicSwitch.isEnabled = com.google.android.material.color.DynamicColors.isDynamicColorAvailable()
        b.dynamicSwitch.setOnCheckedChangeListener { _, on ->
            LocalStore.dynamicColor = on
            Toast.makeText(this, "重新打开 App 后生效", Toast.LENGTH_SHORT).show()
        }
        b.translateSwitch.isChecked = LocalStore.translateTitles
        b.translateSwitch.setOnCheckedChangeListener { _, on -> LocalStore.translateTitles = on }

        b.loginBtn.setOnClickListener {
            if (LocalStore.loggedIn) {
                AlertDialog.Builder(this).setMessage("退出登录？本地的爱心和收藏会保留。")
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            Account.logout(); LocalStore.syncToAccount = false; b.syncSwitch.isChecked = false; render()
                        }
                    }.setNegativeButton(android.R.string.cancel, null).show()
            } else requestLogin { ok ->
                if (ok) { LocalStore.loggedIn = true; render(); pull() } // 登录后自动把账号的爱心和收藏拉到本地
            }
        }
        b.pullBtn.setOnClickListener { pull() }
        render()
        // 进来时确认一下登录是否还有效
        lifecycleScope.launch {
            runCatching { Account.checkLogin() }
            render()
        }
    }

    private fun editAddress() {
        val layout = com.google.android.material.textfield.TextInputLayout(this).apply {
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            hint = "站点地址"
            setPadding(48, 16, 48, 0)
        }
        val edit = com.google.android.material.textfield.TextInputEditText(layout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        layout.addView(edit)
        AlertDialog.Builder(this).setTitle("修改站点地址").setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val v = edit.text?.toString()?.trim().orEmpty()
                if (v.isEmpty()) return@setPositiveButton
                LocalStore.siteAddress = com.nsfwbili.app.site.SiteLock.normalize(v)
                SiteRegistry.reload()
                // 整个界面按新地址重建
                startActivity(Intent(this, com.nsfwbili.app.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun render() {
        val logged = LocalStore.loggedIn
        b.accountStatus.text = if (logged) {
            val t = LocalStore.lastSync
            "已登录" + if (t > 0) "，上次同步 " + DateFormat.format("MM-dd HH:mm", t) else ""
        } else "未登录"
        b.loginBtn.text = if (logged) "退出登录" else "登录 ${SiteRegistry.current.displayName} 账号"
        b.pullBtn.isEnabled = logged
    }

    private fun pull() {
        b.pullBtn.isEnabled = false
        b.accountStatus.text = "同步中…"
        lifecycleScope.launch {
            try {
                val (likes, favs) = Account.pull()
                Toast.makeText(this@SettingsActivity, "已同步：爱心 $likes 个，收藏 $favs 个", Toast.LENGTH_LONG).show()
            } catch (e: ChallengeException) {
                requestChallenge(e.url) { if (it) pull() }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "同步失败：${e.message}", Toast.LENGTH_LONG).show()
            }
            render()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
