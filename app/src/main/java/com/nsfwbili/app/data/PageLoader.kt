package com.nsfwbili.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 用一个看不见的 WebView 加载网页，再在页面里跑解析脚本，把结果以 JSON 交回来。
 * 这样和网页版共用 Cookie / UA，Cloudflare 放行一次以后就都能用。
 */
object PageLoader {
    private const val TIMEOUT_MS = 25_000L
    private const val CHALLENGE_GIVE_UP_MS = 8_000L
    private const val POLL_MS = 400L

    private val main = Handler(Looper.getMainLooper())
    private val mutex = Mutex()
    private var web: WebView? = null
    /** 当前占用 WebView 的任务；旧任务的回调只在自己仍是 current 时才动 WebView */
    private var current: Job? = null
    private lateinit var appContext: Context
    private val scripts = HashMap<String, String>()

    fun init(ctx: Context) { appContext = ctx.applicationContext }

    fun userAgent(): String = WebSettings.getDefaultUserAgent(appContext)

    private fun asset(path: String) = scripts.getOrPut(path) {
        appContext.assets.open(path).bufferedReader().use { it.readText() }
    }

    /**
     * 加载 [url]，反复执行 [scriptPath] 直到 [isDone] 认为数据齐了（或超时）。
     * 返回脚本输出的 JSON。
     */
    suspend fun load(
        url: String,
        commonPath: String,
        scriptPath: String,
        allowedHosts: List<String>,
        isDone: (JSONObject) -> Boolean,
    ): JSONObject = mutex.withLock {
        val js = asset(commonPath) + "\n" + asset(scriptPath)
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont -> Job(url, js, allowedHosts, isDone, cont).start() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun webView(): WebView = web ?: WebView(appContext).also { w ->
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.blockNetworkImage = true // 后台只要文字，不下图片
        w.settings.mediaPlaybackRequiresUserGesture = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        web = w
    }

    private class Job(
        val url: String,
        val js: String,
        val allowedHosts: List<String>,
        val isDone: (JSONObject) -> Boolean,
        val cont: CancellableContinuation<JSONObject>,
    ) {
        val started = System.currentTimeMillis()
        var challengeSince = 0L
        var last: JSONObject? = null
        var finished = false
        var pageLoaded = false
        var loadedPolls = 0

        var sawStart = false

        fun start() {
            val w = webView()
            current?.finished = true
            current = this
            w.stopLoading()
            w.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val host = req.url.host?.lowercase() ?: return null
                    val ok = host == "challenges.cloudflare.com" || host.endsWith("cloudflare.com") ||
                        allowedHosts.any { host == it || host.endsWith(".$it") }
                    // 广告、统计脚本一律拦掉，加载更快
                    return if (ok) null else WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                override fun onPageStarted(view: WebView, u: String?, favicon: android.graphics.Bitmap?) { sawStart = true }
                // 上一个页面迟到的 onPageFinished 不算数
                override fun onPageFinished(view: WebView, u: String?) { if (sawStart) pageLoaded = true }
            }
            cont.invokeOnCancellation { main.post { finished = true; if (current === this) w.stopLoading() } }
            w.loadUrl(url)
            main.postDelayed(::poll, 800)
        }

        fun poll() {
            if (finished || current !== this) return
            val w = webView()
            w.evaluateJavascript(js) { raw ->
                if (finished) return@evaluateJavascript
                val obj = decode(raw)
                val now = System.currentTimeMillis()
                if (obj != null) {
                    last = obj
                    if (obj.optBoolean("challenge")) {
                        if (challengeSince == 0L) challengeSince = now
                        if (now - challengeSince > CHALLENGE_GIVE_UP_MS) return@evaluateJavascript fail(ChallengeException(url))
                    } else {
                        challengeSince = 0L
                        if (isDone(obj)) return@evaluateJavascript succeed(obj)
                        if (pageLoaded && ++loadedPolls >= 4) return@evaluateJavascript succeed(obj)
                    }
                }
                if (now - started > TIMEOUT_MS) {
                    val l = last
                    if (l != null && !l.optBoolean("challenge")) succeed(l)
                    else if (l != null) fail(ChallengeException(url))
                    else fail(RuntimeException("加载超时"))
                    return@evaluateJavascript
                }
                main.postDelayed(::poll, POLL_MS)
            }
        }

        private fun release() { finished = true; if (current === this) { webView().stopLoading(); current = null } }
        fun succeed(o: JSONObject) { release(); if (cont.isActive) cont.resume(o) }
        fun fail(e: Exception) { release(); if (cont.isActive) cont.resumeWithException(e) }

        fun decode(raw: String?): JSONObject? {
            if (raw == null || raw == "null") return null
            return try {
                val s = JSONObject("{\"v\":$raw}").optString("v")
                if (s.isNullOrBlank()) null else JSONObject(s)
            } catch (e: Exception) { null }
        }
    }
}
