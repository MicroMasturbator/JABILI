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
import com.nsfwbili.app.site.SiteRegistry

/** 打开网站自己的登录页，用户自己输入账号密码；检测到已登录就返回 */
class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityChallengeBinding
    private val h = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChallengeBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tip.text = "在下面的 ${SiteRegistry.current.displayName} 官方页面登录，登录成功后会自动返回"
        b.webView.settings.javaScriptEnabled = true
        b.webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(b.webView, true)
        b.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: android.webkit.WebResourceRequest): Boolean {
                val host = req.url.host ?: return true
                return !SiteRegistry.current.matches(host) && !host.endsWith("cloudflare.com")
            }
        }
        b.webView.loadUrl(SiteRegistry.current.loginUrl ?: SiteRegistry.current.homeUrl)
        h.postDelayed(::check, 1500)
    }

    private fun check() {
        if (isFinishing) return
        b.webView.evaluateJavascript("!!((window.pageContext && pageContext.userId) || document.querySelector('a[href*=\"logout\"]'))") { r ->
            if (r == "true") {
                CookieManager.getInstance().flush()
                setResult(RESULT_OK); finish()
            } else h.postDelayed(::check, 1000)
        }
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); b.webView.destroy(); super.onDestroy() }
}
