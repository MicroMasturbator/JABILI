package com.nsfwbili.app.ui

import android.content.Context
import android.content.Intent
import android.widget.ImageView
import coil.load
import com.nsfwbili.app.data.PageLoader
import com.nsfwbili.app.data.VideoItem
import com.nsfwbili.app.site.SiteRegistry

fun ImageView.loadCover(url: String?) {
    if (url.isNullOrBlank()) { setImageDrawable(null); return }
    load(url) {
        crossfade(true)
        addHeader("Referer", SiteRegistry.current.refererFor(url))
        addHeader("User-Agent", PageLoader.userAgent())
    }
}

object Nav {
    fun list(ctx: Context, title: String, url: String) =
        ctx.startActivity(Intent(ctx, ListActivity::class.java).putExtra("title", title).putExtra("url", url))

    fun detail(ctx: Context, v: VideoItem) =
        ctx.startActivity(
            Intent(ctx, DetailActivity::class.java)
                .putExtra("url", v.url).putExtra("title", v.title)
                .putExtra("cover", v.cover).putExtra("duration", v.duration)
        )

    fun web(ctx: Context, url: String) = com.nsfwbili.app.browser.BrowserActivity.start(ctx, url)
}
