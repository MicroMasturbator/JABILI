package com.nsfwbili.app.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.nsfwbili.app.databinding.ActivityPlayerBinding

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    data class Args(
        val url: String,
        val referer: String,
        val userAgent: String,
        val cookies: String?,
        val title: String,
        val startMs: Long,
        val pageUrl: String? = null,
    )

    companion object {
        fun start(ctx: Context, a: Args) {
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra("url", a.url)
                    .putExtra("referer", a.referer)
                    .putExtra("ua", a.userAgent)
                    .putExtra("cookies", a.cookies)
                    .putExtra("title", a.title)
                    .putExtra("start", a.startMs)
                    .putExtra("page", a.pageUrl)
            )
        }
    }

    private lateinit var b: ActivityPlayerBinding
    private lateinit var ctl: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        enterImmersive()

        val url = intent.getStringExtra("url") ?: return finish()
        val page = intent.getStringExtra("page")
        ctl = PlayerController(this, b.playerView, b.osd, page)
        // 控制栏显示/隐藏时同步标题栏
        b.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { vis ->
            b.title.visibility = if (vis == View.VISIBLE && b.title.text.isNotEmpty()) View.VISIBLE else View.GONE
            if (vis != View.VISIBLE) enterImmersive()
        })
        b.title.text = intent.getStringExtra("title").orEmpty()

        // 全屏按钮：横屏全屏 ⇄ 竖屏
        ctl.setFullscreenState(true)
        ctl.onFullscreen = { full ->
            requestedOrientation = if (full) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val start = savedInstanceState?.getLong("pos", -1L)?.takeIf { it >= 0 } ?: intent.getLongExtra("start", 0L)
        ctl.play(
            PlayerController.Source(
                url = url,
                referer = intent.getStringExtra("referer").orEmpty(),
                userAgent = intent.getStringExtra("ua"),
                cookies = intent.getStringExtra("cookies"),
                cookieHosts = com.nsfwbili.app.site.SiteRegistry.forUrl(page ?: url).allowedHosts,
            ),
            start,
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::ctl.isInitialized) outState.putLong("pos", ctl.position)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= 26 && ctl.isPlaying && !isInPictureInPictureMode) {
            runCatching {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        ctl.setPip(isInPip)
        if (isInPip) b.title.visibility = View.GONE
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }
}
