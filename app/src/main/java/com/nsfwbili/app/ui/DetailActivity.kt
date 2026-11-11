package com.nsfwbili.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ImageRequest
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nsfwbili.app.R
import com.nsfwbili.app.data.ChallengeException
import com.nsfwbili.app.data.Link
import com.nsfwbili.app.data.LocalStore
import com.nsfwbili.app.data.PageLoader
import com.nsfwbili.app.data.VideoDetail
import com.nsfwbili.app.data.VideoItem
import com.nsfwbili.app.databinding.ActivityDetailBinding
import com.nsfwbili.app.player.PlayerController
import com.nsfwbili.app.site.SiteRegistry
import kotlinx.coroutines.launch

/** 详情页：顶部竖屏小窗播放器（可全屏），下面是标题、女优、标签、相关推荐 */
@OptIn(UnstableApi::class)
class DetailActivity : BaseActivity() {
    private lateinit var b: ActivityDetailBinding
    private lateinit var item: VideoItem
    private var detail: VideoDetail? = null
    private var loadedAt = 0L
    private lateinit var related: VideoAdapter
    private lateinit var playerCtl: PlayerController
    private var fullscreen = false

    private companion object { const val STREAM_TTL_MS = 10 * 60_000L }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        item = VideoItem(
            url = intent.getStringExtra("url")!!,
            title = intent.getStringExtra("title").orEmpty(),
            cover = intent.getStringExtra("cover").orEmpty(),
            duration = intent.getStringExtra("duration").orEmpty(),
        )
        supportActionBar?.title = item.title.substringBefore(' ').ifBlank { item.title }
        b.title.text = item.title
        b.cover.loadCover(item.cover)

        related = VideoAdapter(true, { Nav.detail(this, it) })
        b.related.adapter = related
        layoutRelated()

        b.playBtn.isEnabled = false
        b.playBtn.setOnClickListener { play() }
        b.playIcon.setOnClickListener { play() }
        b.cover.setOnClickListener { play() }
        setupPlayerView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fullscreen) setFullscreen(false) else finish() }
        })
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        b.webBtn.setOnClickListener { Nav.web(this, item.url) }
        b.favBtn.setOnClickListener {
            val added = LocalStore.toggleFavorite(currentItem())
            updateFav()
            Toast.makeText(this, if (added) "已加入收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
            AccountSync.push(applicationContext, currentItem(), false, added, detail?.videoId)
        }
        b.likeBtn.setOnClickListener {
            val added = LocalStore.toggleLike(currentItem())
            updateFav()
            Toast.makeText(this, if (added) "已点爱心" else "已取消爱心", Toast.LENGTH_SHORT).show()
            AccountSync.push(applicationContext, currentItem(), true, added, detail?.videoId)
        }
        b.error.setOnClickListener { load() }
        updateFav()
        load()
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        layoutRelated()
        // 全屏时系统栏偶尔会被弹回来，重新隐藏
        if (fullscreen) WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun layoutRelated() {
        val span = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        b.related.layoutManager = GridLayoutManager(this, span)
    }

    private fun currentItem(): VideoItem {
        val d = detail ?: return item
        return item.copy(
            title = d.title.ifBlank { item.title },
            cover = item.cover.ifBlank { d.cover },
        )
    }

    private fun updateFav() {
        styleToggle(b.favBtn, LocalStore.isFavorite(item.url), "☆ 收藏", "★ 已收藏")
        styleToggle(b.likeBtn, LocalStore.isLiked(item.url), "♡ 爱心", "♥ 已爱心")
    }

    /** 已选中：实心主色按钮；未选中：浅色 tonal 按钮 */
    private fun styleToggle(btn: com.google.android.material.button.MaterialButton, on: Boolean, off: String, onText: String) {
        btn.text = if (on) onText else off
        val tv = android.util.TypedValue()
        val bg = if (on) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorSecondaryContainer
        val fg = if (on) com.google.android.material.R.attr.colorOnPrimary else com.google.android.material.R.attr.colorOnSecondaryContainer
        theme.resolveAttribute(bg, tv, true); btn.backgroundTintList = android.content.res.ColorStateList.valueOf(tv.data)
        theme.resolveAttribute(fg, tv, true); btn.setTextColor(tv.data)
    }

    private fun load() {
        b.progress.visibility = View.VISIBLE
        b.error.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val d = SiteRegistry.current.detail(item.url)
                detail = d
                loadedAt = System.currentTimeMillis()
                show(d)
                LocalStore.addHistory(currentItem())
            } catch (e: ChallengeException) {
                requestChallenge(item.url) { ok ->
                    if (ok) load() else { b.error.text = getString(R.string.challenge_needed); b.error.visibility = View.VISIBLE }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                b.error.text = "加载失败：${e.message}\n点这里重试"
                b.error.visibility = View.VISIBLE
            } finally {
                b.progress.visibility = View.GONE
            }
        }
    }

    private fun show(d: VideoDetail) {
        if (d.title.isNotBlank()) b.title.text = d.title
        if (item.cover.isBlank() && d.cover.isNotBlank()) b.cover.loadCover(d.cover)
        b.info.text = d.info
        b.info.visibility = if (d.info.isBlank()) View.GONE else View.VISIBLE
        // 翻译放在 info 写完之后：命中缓存时回调是同步的，不然"机翻"会被覆盖
        if (LocalStore.translateTitles && com.nsfwbili.app.data.TitleTranslator.isJapanese(b.title.text.toString())) {
            val orig = b.title.text.toString()
            com.nsfwbili.app.data.TitleTranslator.translate(orig) { t ->
                b.title.text = t
                b.info.text = if (d.info.isBlank()) "机翻" else "${d.info}  ·  机翻"
                b.info.visibility = View.VISIBLE
                b.title.setOnLongClickListener { b.title.text = orig; true } // 长按看原文
            }
        }
        b.playBtn.isEnabled = d.hls != null
        val pos = LocalStore.progress(item.url)
        b.playBtn.text = when {
            d.hls == null -> "未找到视频地址"
            pos > 5_000 -> "继续 " + fmtTime(pos)
            else -> getString(R.string.play)
        }
        chips(b.models, b.modelsLabel, d.models, "")
        chips(b.tags, b.tagsLabel, d.tags.sortedByDescending { it.group == "cat" }, "#")
        related.items.clear(); related.items.addAll(d.related); related.notifyDataSetChanged()
        b.relatedLabel.visibility = if (d.related.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun chips(g: ChipGroup, label: View, links: List<Link>, prefix: String) {
        g.removeAllViews()
        label.visibility = if (links.isEmpty()) View.GONE else View.VISIBLE
        links.forEach { l ->
            val chip = Chip(this).apply {
                text = l.name
                setOnClickListener { Nav.list(this@DetailActivity, prefix + l.name, l.url) }
            }
            if (l.cover.isNotBlank()) {
                imageLoader.enqueue(ImageRequest.Builder(this).data(l.cover)
                    .addHeader("Referer", SiteRegistry.current.refererFor(l.cover))
                    .addHeader("User-Agent", PageLoader.userAgent())
                    .target { d -> chip.chipIcon = d; chip.isChipIconVisible = true }
                    .build())
            }
            g.addView(chip)
        }
    }

    private fun fmtTime(ms: Long): String {
        val t = ms / 1000; val h = t / 3600; val m = t % 3600 / 60; val sec = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    private fun play() {
        // 视频地址带有效期，详情页放太久就重新取一次
        if (detail != null && stale()) refreshStream { launchPlayer() } else launchPlayer()
    }

    private fun stale() = System.currentTimeMillis() - loadedAt > STREAM_TTL_MS

    /** 重新抓详情页拿新的播放地址；失败就提示，不再用过期的旧地址 */
    private fun refreshStream(then: () -> Unit) {
        lifecycleScope.launch {
            b.progress.visibility = View.VISIBLE
            val r = runCatching { SiteRegistry.current.detail(item.url) }
            b.progress.visibility = View.GONE
            r.onSuccess { detail = it; loadedAt = System.currentTimeMillis(); then() }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) return@onFailure
                    Toast.makeText(this@DetailActivity, "获取播放地址失败：${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun launchPlayer() {
        val d = detail ?: return
        if (d.hls == null) { Toast.makeText(this, "未找到视频地址，可以试试网页版", Toast.LENGTH_SHORT).show(); return }
        startInline(LocalStore.progress(item.url))
    }

    private fun setupPlayerView() {
        playerCtl = PlayerController(this, b.playerView, b.osd, item.url).apply {
            onFullscreen = { full -> setFullscreen(full) }
            // 地址过期：换新地址，从刚才的位置继续
            onExpired = { pos -> refreshStream { startInline(pos) } }
            // 从后台回来时地址可能已过期，先刷新
            beforeRestore = { pos -> if (stale()) { refreshStream { startInline(pos) }; false } else true }
        }
    }

    /** 在顶部小窗里开始播放 */
    private fun startInline(startMs: Long) {
        val hls = detail?.hls ?: return
        val site = SiteRegistry.current
        playerCtl.play(
            PlayerController.Source(
                url = hls,
                referer = site.refererFor(item.url),
                userAgent = PageLoader.userAgent(),
                cookies = android.webkit.CookieManager.getInstance().getCookie(item.url),
                cookieHosts = site.allowedHosts,
            ),
            startMs,
        )
        b.playerView.visibility = View.VISIBLE
        b.cover.visibility = View.GONE
        b.playIcon.visibility = View.GONE
    }

    /** 小窗 ⇄ 横屏全屏 */
    private fun setFullscreen(full: Boolean) {
        fullscreen = full
        playerCtl.setFullscreenState(full)
        val bars = WindowInsetsControllerCompat(window, window.decorView)
        if (full) {
            b.toolbar.visibility = View.GONE
            b.contentBox.visibility = View.GONE
            b.playerBox.fullscreen = true
            b.playerBox.layoutParams = b.playerBox.layoutParams.apply { height = android.view.ViewGroup.LayoutParams.MATCH_PARENT }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            b.root.fitsSystemWindows = false
            b.root.setPadding(0, 0, 0, 0)
            bars.hide(WindowInsetsCompat.Type.systemBars())
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            b.toolbar.visibility = View.VISIBLE
            b.contentBox.visibility = View.VISIBLE
            b.playerBox.fullscreen = false
            b.playerBox.layoutParams = b.playerBox.layoutParams.apply { height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT }
            bars.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            b.root.fitsSystemWindows = true
            b.root.requestApplyInsets()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // ---------------- 画中画 ----------------

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 正在播放时按 Home / 切走，自动进入小窗
        if (android.os.Build.VERSION.SDK_INT >= 26 && playerCtl.isPlaying && !isInPictureInPictureMode) {
            runCatching {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        playerCtl.setPip(isInPip)
        val v = if (isInPip) View.GONE else View.VISIBLE
        if (!fullscreen) {
            b.toolbar.visibility = v
            b.contentBox.visibility = v
            b.playerBox.fullscreen = isInPip
            b.playerBox.layoutParams = b.playerBox.layoutParams.apply {
                height = if (isInPip) android.view.ViewGroup.LayoutParams.MATCH_PARENT else android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { onBackPressedDispatcher.onBackPressed(); return true }
        return super.onOptionsItemSelected(item)
    }
}
