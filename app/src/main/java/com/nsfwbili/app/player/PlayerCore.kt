package com.nsfwbili.app.player

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** 统一创建 ExoPlayer：带 Referer / Cookie / UA，识别 m3u8 */
@OptIn(UnstableApi::class)
object PlayerCore {
    /**
     * @param cookieHosts Cookie 只发给这些域名（站点自己），不发给视频 CDN 和分片主机
     * @param onExpired 播放地址过期 / 403 时调用（只回调一次），由调用方重新取地址续播；为 null 时只提示
     */
    fun create(
        ctx: Context, url: String, referer: String, userAgent: String?, cookies: String?, startMs: Long,
        cookieHosts: List<String> = emptyList(),
        onExpired: ((positionMs: Long) -> Unit)? = null,
    ): ExoPlayer {
        val headers = buildMap {
            if (referer.isNotEmpty()) {
                put("Referer", referer)
                val u = Uri.parse(referer)
                if (u.scheme != null && u.host != null) put("Origin", "${u.scheme}://${u.host}")
            }
        }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(headers)

        val withCookies = ResolvingDataSource.Factory(http) { spec ->
            val host = spec.uri.host?.lowercase()
            if (!cookies.isNullOrEmpty() && host != null && cookieHosts.any { host == it || host.endsWith(".$it") })
                spec.withAdditionalHeaders(mapOf("Cookie" to cookies))
            else spec
        }

        val p = ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(withCookies))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        val item = MediaItem.Builder().setUri(url).apply {
            val path = url.substringBefore('?').lowercase()
            when {
                path.endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                path.endsWith(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }.build()
        p.setMediaItem(item)
        if (startMs > 0) p.seekTo(startMs)
        p.addListener(object : Player.Listener {
            var retried = false
            override fun onPlayerError(error: PlaybackException) {
                val expired = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION
                if (expired && onExpired != null && !retried) {
                    retried = true
                    onExpired(p.currentPosition)
                    return
                }
                Toast.makeText(ctx, "播放失败：${error.errorCodeName}\n${error.message}", Toast.LENGTH_LONG).show()
            }
        })
        p.prepare()
        return p
    }
}
