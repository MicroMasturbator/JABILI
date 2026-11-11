package com.nsfwbili.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.nsfwbili.app.R
import com.nsfwbili.app.databinding.ActivityBrowserBinding
import com.nsfwbili.app.player.PlayerActivity
import com.nsfwbili.app.site.SiteExtractor
import com.nsfwbili.app.site.SiteRegistry
import org.json.JSONObject

class BrowserActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_URL = "url"
        fun start(ctx: Context, url: String) {
            ctx.startActivity(Intent(ctx, BrowserActivity::class.java).putExtra(EXTRA_URL, url))
        }
    }

    private lateinit var b: ActivityBrowserBinding
    private lateinit var web: WebView

    /** 当前页面嗅探到的最后一个视频流地址 */
    @Volatile private var sniffedUrl: String? = null
    /** 从播放器回来后短时间内忽略网页的 play 事件，避免反复弹出 */
    private var ignorePlayUntil = 0L
    private var pageTitle: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(b.root)
        web = b.webView

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(true) // 配合 onCreateWindow=false，屏蔽弹窗广告
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        web.addJavascriptInterface(Bridge(), "NSFWBILI")
        web.webViewClient = Client()
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                b.loading.progress = p
                b.loading.visibility = if (p >= 100) View.GONE else View.VISIBLE
            }
            override fun onReceivedTitle(view: WebView, title: String?) { pageTitle = title ?: "" }
            override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, m: android.os.Message?) = false
        }

        b.takeoverBtn.setOnClickListener { requestTakeover(null, 0.0) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(intent.getStringExtra(EXTRA_URL) ?: SiteRegistry.current.homeUrl)
    }

    private val site: SiteExtractor get() = SiteRegistry.forUrl(web.url ?: "")

    // ---------------- 接管逻辑 ----------------

    /**
     * 拿到视频地址并打开原生播放器。优先级：站点 JS 变量 > 网络嗅探 > <video>.currentSrc
     */
    private fun requestTakeover(videoSrc: String?, startSec: Double) {
        val s = site
        val js = s.streamUrlJs
        if (js != null) {
            web.evaluateJavascript(js) { raw ->
                val fromPage = decodeJsString(raw)
                launchPlayer(fromPage ?: sniffedUrl ?: videoSrc?.takeUnless { it.startsWith("blob:") || it.isBlank() }, startSec, s)
            }
        } else {
            launchPlayer(sniffedUrl ?: videoSrc?.takeUnless { it.startsWith("blob:") || it.isBlank() }, startSec, s)
        }
    }

    private fun launchPlayer(streamUrl: String?, startSec: Double, s: SiteExtractor) {
        if (streamUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.no_stream, Toast.LENGTH_SHORT).show()
            return
        }
        val pageUrl = web.url ?: ""
        val cookies = CookieManager.getInstance().getCookie(streamUrl)
            ?: CookieManager.getInstance().getCookie(pageUrl)
        PlayerActivity.start(
            this,
            PlayerActivity.Args(
                url = streamUrl,
                referer = s.refererFor(pageUrl),
                userAgent = web.settings.userAgentString,
                cookies = cookies,
                title = pageTitle,
                startMs = (startSec * 1000).toLong(),
                pageUrl = pageUrl,
            )
        )
    }

    private fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        return try {
            JSONObject("{\"v\":$raw}").optString("v").takeIf { it.isNotBlank() && it != "null" }
        } catch (e: Exception) { null }
    }

    private fun updateTakeoverButton() {
        b.takeoverBtn.visibility = if (sniffedUrl != null) View.VISIBLE else View.GONE
    }

    private fun checkPageStream() {
        val js = site.streamUrlJs ?: return
        web.evaluateJavascript(js) { raw ->
            if (decodeJsString(raw) != null) b.takeoverBtn.visibility = View.VISIBLE
        }
    }

    inner class Bridge {
        /** 网页里任意 <video> 开始播放时被调用（见 HOOK_JS） */
        @JavascriptInterface
        fun onPlay(currentSrc: String?, currentTime: Double) {
            runOnUiThread {
                if (SystemClock.elapsedRealtime() < ignorePlayUntil) return@runOnUiThread
                ignorePlayUntil = SystemClock.elapsedRealtime() + 1500
                requestTakeover(currentSrc, currentTime)
            }
        }
    }

    /** 监听所有 video 的 play 事件：暂停网页播放，交给原生播放器 */
    private val hookJs = """
        (function(){
          if (window.__nsfwbiliHooked) return; window.__nsfwbiliHooked = true;
          document.addEventListener('play', function(e){
            var v = e.target;
            if (!v || v.tagName !== 'VIDEO') return;
            try { v.pause(); } catch(x) {}
            try { NSFWBILI.onPlay(v.currentSrc || v.src || '', v.currentTime || 0); } catch(x) {}
          }, true);
        })();
    """.trimIndent()

    inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            val uri = req.url
            if (uri.scheme != "http" && uri.scheme != "https") return true // 拦截 intent:// 等外跳
            val current = SiteRegistry.forUrl(view.url ?: "")
            if (current.allowedHosts.isEmpty()) return false
            val host = uri.host?.lowercase() ?: return true
            // 不在站点域名内的跳转一般是广告，直接拦掉
            return !current.matches(host)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            sniffedUrl = null
            updateTakeoverButton()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            view.evaluateJavascript(hookJs, null)
            checkPageStream()
        }

        override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
            val url = req.url.toString()
            val pageSite = SiteRegistry.forUrl(req.requestHeaders["Referer"] ?: url)
            if (pageSite.isStreamRequest(url) && req.method == "GET") {
                // 只记录主播放列表（第一个出现的），避免被分片 / 子清单覆盖
                if (sniffedUrl == null) {
                    sniffedUrl = url
                    runOnUiThread { updateTakeoverButton() }
                }
            }
            return null
        }
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        ignorePlayUntil = SystemClock.elapsedRealtime() + 1500
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
