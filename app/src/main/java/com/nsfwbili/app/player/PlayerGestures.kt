package com.nsfwbili.app.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 播放器手势：
 *  - 单击：显示 / 隐藏进度条和控制栏
 *  - 双击：中间暂停 / 播放，左侧快退 10 秒，右侧快进 10 秒
 *  - 左右滑：拖动进度（松手后跳转）
 *  - 左半屏上下滑：亮度；右半屏上下滑：音量
 *  - 长按：临时 2 倍速，松手恢复
 */
@OptIn(UnstableApi::class)
class PlayerGestures(
    private val activity: Activity,
    private val pv: PlayerView,
    private val osd: TextView,
    private val playerProvider: () -> Player?,
) {
    private enum class Mode { NONE, SEEK, BRIGHTNESS, VOLUME }

    /** 横向滑满一屏对应的时长 */
    private val seekFullWidthMs = 120_000L

    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var seekStartPos = 0L
    private var seekTargetPos = 0L
    private var startBrightness = 0.5f
    private var startVolume = 0
    var longPressing = false
        private set
    var enabled = true
    private var speedBefore = 1f

    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val hideOsd = Runnable { osd.visibility = View.GONE }

    private val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val p = playerProvider() ?: return true
            val w = pv.width.toFloat()
            when {
                // 左右两侧双击：快退 / 快进 10 秒（中间区域双击仍是暂停 / 播放）
                e.x < w * 0.3f -> { p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0)); flash("« 10 秒") }
                e.x > w * 0.7f -> {
                    val d = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    p.seekTo((p.currentPosition + 10_000).coerceAtMost(d)); flash("10 秒 »")
                }
                p.isPlaying -> { p.pause(); flash("❚❚ 暂停") }
                else -> { p.play(); flash("▶ 播放") }
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (mode != Mode.NONE) return
            val p = playerProvider() ?: return
            if (!p.isPlaying) return
            longPressing = true
            speedBefore = p.playbackParameters.speed
            p.setPlaybackSpeed(2f)
            show("▶▶ 2x 倍速中")
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (longPressing) return true
            val totalX = e2.x - downX
            val totalY = e2.y - downY
            if (mode == Mode.NONE) {
                val p = playerProvider() ?: return true
                mode = if (abs(totalX) > abs(totalY)) {
                    if (p.duration == C.TIME_UNSET || !p.isCurrentMediaItemSeekable) return true
                    seekStartPos = p.currentPosition
                    Mode.SEEK
                } else if (downX < pv.width / 2f) {
                    startBrightness = activity.window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
                    Mode.BRIGHTNESS
                } else {
                    startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    Mode.VOLUME
                }
            }
            when (mode) {
                Mode.SEEK -> onSeekDrag(totalX)
                Mode.BRIGHTNESS -> onBrightnessDrag(-totalY / pv.height)
                Mode.VOLUME -> onVolumeDrag(-totalY / pv.height)
                Mode.NONE -> {}
            }
            return true
        }
    })

    fun onTouch(e: MotionEvent): Boolean {
        if (!enabled) return false
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = e.x; downY = e.y; mode = Mode.NONE
        }
        detector.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            finishGesture()
        }
        return true
    }

    private fun finishGesture() {
        val p = playerProvider()
        if (mode == Mode.SEEK && p != null) {
            p.seekTo(seekTargetPos)
            osd.postDelayed(hideOsd, 400)
        } else if (mode != Mode.NONE) {
            osd.postDelayed(hideOsd, 400)
        }
        if (longPressing) {
            p?.setPlaybackSpeed(speedBefore)
            longPressing = false
            osd.postDelayed(hideOsd, 200)
        }
        mode = Mode.NONE
    }

    private fun onSeekDrag(totalX: Float) {
        val p = playerProvider() ?: return
        val duration = p.duration.coerceAtLeast(0)
        // 短视频按时长比例，长视频按 seekFullWidthMs
        val fullWidth = minOf(duration, seekFullWidthMs).coerceAtLeast(1)
        val delta = (totalX / pv.width * fullWidth).toLong()
        seekTargetPos = (seekStartPos + delta).coerceIn(0, duration)
        val sign = if (delta >= 0) "+" else "-"
        show("${fmt(seekTargetPos)} / ${fmt(duration)}\n$sign${fmt(abs(delta))}")
    }

    private fun onBrightnessDrag(fraction: Float) {
        val v = (startBrightness + fraction).coerceIn(0.01f, 1f)
        val lp = activity.window.attributes
        lp.screenBrightness = v
        activity.window.attributes = lp
        show("☀ 亮度 ${(v * 100).roundToInt()}%")
    }

    private fun onVolumeDrag(fraction: Float) {
        val v = (startVolume + fraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        show("🔊 音量 ${(v * 100f / maxVolume).roundToInt()}%")
    }

    private fun show(text: String) {
        osd.removeCallbacks(hideOsd)
        osd.text = text
        osd.visibility = View.VISIBLE
    }

    private fun flash(text: String) {
        show(text)
        osd.postDelayed(hideOsd, 700)
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
