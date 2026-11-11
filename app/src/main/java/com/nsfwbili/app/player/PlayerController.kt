package com.nsfwbili.app.player

import android.annotation.SuppressLint
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nsfwbili.app.data.LocalStore

/**
 * 详情页小窗和独立播放页共用的播放器控制：PlayerView 配置、手势、全屏按钮、
 * 生命周期（onStop 释放 / onStart 恢复）、保存进度、地址过期回调。
 */
@OptIn(UnstableApi::class)
class PlayerController(
    private val activity: AppCompatActivity,
    private val pv: PlayerView,
    osd: TextView,
    /** 保存进度用的键（通常是视频页网址）；null 表示不记进度 */
    private val progressKey: String?,
) : DefaultLifecycleObserver {

    data class Source(
        val url: String,
        val referer: String,
        val userAgent: String?,
        val cookies: String?,
        val cookieHosts: List<String>,
    )

    var player: ExoPlayer? = null
        private set
    private var source: Source? = null
    private var resumePos = 0L
    private var resumePlaying = true
    private lateinit var gestures: PlayerGestures

    /** 全屏按钮被点击 */
    var onFullscreen: ((Boolean) -> Unit)? = null
    /** 播放地址过期：调用方去取新地址，再调 [play] 续播 */
    var onExpired: ((positionMs: Long) -> Unit)? = null
    /** 从后台回来要重建播放器时调用；返回 false 表示调用方自己处理（比如先刷新地址） */
    var beforeRestore: ((positionMs: Long) -> Boolean)? = null

    val isStarted get() = source != null
    val position get() = player?.currentPosition ?: resumePos
    val isPlaying get() = player?.isPlaying == true

    /** 画中画时隐藏控制栏、关掉手势 */
    fun setPip(pip: Boolean) {
        pv.useController = !pip
        gestures.enabled = !pip
    }

    init {
        setupView(osd)
        activity.lifecycle.addObserver(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(osd: TextView) {
        pv.controllerAutoShow = false
        pv.controllerShowTimeoutMs = 3500
        pv.setShowNextButton(false)
        pv.setShowPreviousButton(false)
        pv.setShowSubtitleButton(false)
        pv.setFullscreenButtonClickListener { full -> onFullscreen?.invoke(full) }
        gestures = PlayerGestures(activity, pv, osd) { player }
        pv.setOnTouchListener { _, e -> gestures.onTouch(e) }
    }

    fun setFullscreenState(full: Boolean) = pv.setFullscreenButtonState(full)

    fun play(src: Source, startMs: Long, playWhenReady: Boolean = true) {
        source = src
        resumePlaying = playWhenReady
        player?.release()
        val p = PlayerCore.create(
            activity, src.url, src.referer, src.userAgent, src.cookies, startMs,
            cookieHosts = src.cookieHosts,
            onExpired = onExpired?.let { cb -> { pos: Long -> resumePlaying = true; cb(pos) } },
        )
        // 记住上次选的倍速（控制栏右下角设置里可以改）
        p.setPlaybackSpeed(LocalStore.playbackSpeed)
        p.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackParametersChanged(params: androidx.media3.common.PlaybackParameters) {
                if (!gestures.longPressing) LocalStore.playbackSpeed = params.speed
            }
        })
        p.playWhenReady = playWhenReady
        pv.player = p
        player = p
    }

    private fun save() {
        val p = player ?: return
        resumePos = p.currentPosition
        resumePlaying = p.playWhenReady
        val key = progressKey ?: return
        // 看完（剩不到 30 秒）就从头开始，否则记住进度
        val nearEnd = p.duration > 0 && p.duration - p.currentPosition < 30_000
        LocalStore.saveProgress(key, if (nearEnd) 0 else p.currentPosition, p.duration.coerceAtLeast(0))
    }

    override fun onStart(owner: LifecycleOwner) {
        val src = source ?: return
        if (player != null) return
        if (beforeRestore?.invoke(resumePos) == false) return
        play(src, resumePos, resumePlaying)
    }

    override fun onStop(owner: LifecycleOwner) {
        save()
        player?.release()
        player = null
        pv.player = null
    }
}
