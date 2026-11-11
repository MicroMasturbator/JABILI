package com.nsfwbili.app.ui

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * 所有页面的基类：统一处理"网站要求验证"和"登录"这两种跳转回调，
 * 不再在每个页面里各写一遍 onActivityResult。
 */
open class BaseActivity : AppCompatActivity() {
    private var pending: ((Boolean) -> Unit)? = null
    private var challengeOpen = false

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        challengeOpen = false
        val cb = pending; pending = null
        cb?.invoke(r.resultCode == RESULT_OK)
    }

    /** 打开验证页；同一时间只开一个，回来后调用 [onDone] */
    fun requestChallenge(url: String, onDone: (passed: Boolean) -> Unit) {
        if (challengeOpen) return
        challengeOpen = true
        pending = onDone
        launcher.launch(Intent(this, ChallengeActivity::class.java).putExtra("url", url))
    }

    fun requestLogin(onDone: (ok: Boolean) -> Unit) {
        pending = onDone
        launcher.launch(Intent(this, LoginActivity::class.java))
    }
}
