package com.nsfwbili.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.nsfwbili.app.databinding.ActivityChallengeBinding

/** 网站弹 Cloudflare 验证时，给用户看一个真网页过验证，过了自动关掉 */
class ChallengeActivity : AppCompatActivity() {
    private lateinit var b: ActivityChallengeBinding
    private val h = Handler(Looper.getMainLooper())
    private var loaded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChallengeBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.webView.settings.javaScriptEnabled = true
        b.webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(b.webView, true)
        b.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) { loaded = true }
        }
        b.webView.loadUrl(intent.getStringExtra("url")!!)
        h.postDelayed(::check, 1500)
    }

    private fun check() {
        if (isFinishing) return
        b.webView.evaluateJavascript(
            "(function(){var t=document.title||'';return !(/just a moment|attention required|请稍候|請稍候|稍等/i.test(t)||document.querySelector('#challenge-form,#cf-wrapper,.cf-turnstile,#challenge-stage,#challenge-running'))})()"
        ) { r ->
            if (loaded && r == "true") {
                CookieManager.getInstance().flush()
                setResult(RESULT_OK); finish()
            } else h.postDelayed(::check, 1000)
        }
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); b.webView.destroy(); super.onDestroy() }
}
