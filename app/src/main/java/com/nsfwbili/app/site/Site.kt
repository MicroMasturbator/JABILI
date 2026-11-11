package com.nsfwbili.app.site

import com.nsfwbili.app.data.Link
import com.nsfwbili.app.data.ListPage
import com.nsfwbili.app.data.PageLoader
import com.nsfwbili.app.data.VideoDetail
import com.nsfwbili.app.data.links
import com.nsfwbili.app.data.str
import com.nsfwbili.app.data.videos

/**
 * 一个站点 = 网页接管能力（[SiteExtractor]）+ 原生界面 + 可选的账号能力。
 * 解析脚本放在 assets/<assetDir>/ 下：common.js、list.js、detail.js、raw.js。
 * 新增站点：写一个 object 继承 Site，然后加到 [SiteRegistry.sites]。
 */
abstract class Site(private val assetDir: String) : SiteExtractor {
    /** 侧边栏里的固定栏目 */
    abstract val sections: List<Link>
    /** 分类页地址，null 表示没有 */
    abstract val categoriesUrl: String?
    abstract fun searchUrl(keyword: String): String

    /** 某个列表页能用的排序：(显示名, 网址)。第一项是默认排序。空表示不支持 */
    open fun sortOptions(listUrl: String): List<Pair<String, String>> = emptyList()

    // ---------- 账号（不支持的站点返回 null） ----------
    open val loginUrl: String? = null
    open val logoutUrl: String? = null
    val displayName: String get() = name
    /** 账号里的列表：like=true 爱心，false 收藏（稍后观看） */
    open fun accountListUrl(like: Boolean): String? = null
    /** 加 / 删 的接口地址 */
    open fun favActionUrl(videoId: String, like: Boolean, add: Boolean): String? = null

    /** 跑一个任意页面，返回 {loggedIn, path, body} */
    suspend fun raw(url: String) = PageLoader.load(url, "$assetDir/common.js", "$assetDir/raw.js", allowedHosts) { true }

    /** 列表页 + 登录状态 */
    suspend fun listWithLogin(url: String): Pair<ListPage, Boolean> {
        val o = PageLoader.load(url, "$assetDir/common.js", "$assetDir/list.js", allowedHosts) {
            (it.optJSONArray("items")?.length() ?: 0) > 0 || it.optBoolean("empty") || it.optBoolean("loggedIn")
        }
        val page = ListPage(o.optJSONArray("items").videos(), o.str("next"), emptyList(), emptyList())
        return page to (o.optBoolean("loggedIn") && !o.optString("path").startsWith("/login"))
    }

    suspend fun list(url: String): ListPage {
        val o = PageLoader.load(url, "$assetDir/common.js", "$assetDir/list.js", allowedHosts) {
            (it.optJSONArray("items")?.length() ?: 0) > 0 || (it.optJSONArray("categories")?.length() ?: 0) > 0 || it.optBoolean("empty")
        }
        return ListPage(
            items = o.optJSONArray("items").videos(),
            next = o.str("next"),
            categories = o.optJSONArray("categories").links(),
            tags = o.optJSONArray("tags").links(),
        )
    }

    suspend fun detail(url: String): VideoDetail {
        val o = PageLoader.load(url, "$assetDir/common.js", "$assetDir/detail.js", allowedHosts) {
            it.str("hls") != null
        }
        return VideoDetail(
            url = url,
            title = o.optString("title"),
            cover = o.optString("cover"),
            info = o.optString("info"),
            hls = o.str("hls"),
            models = o.optJSONArray("models").links(),
            tags = o.optJSONArray("tags").links(),
            related = o.optJSONArray("related").videos(),
            videoId = o.str("videoId"),
        )
    }
}

/**
 * 当前唯一的站点实现。地址不写死在代码里，来自用户在引导页 / 设置里填的网址（见 [SiteLock]）。
 */
class MainSite(host: String) : Site("s1") {
    private val base = "https://$host/"
    override val name = host.substringBefore('.').replaceFirstChar { it.uppercase() }
    override val homeUrl = base
    override val allowedHosts = listOf(host)

    // 视频页里有 `var hlsUrl = 'https://....m3u8'`
    override val streamUrlJs = "(typeof hlsUrl !== 'undefined' && hlsUrl) ? String(hlsUrl) : null"

    // CDN 校验 Referer，用站点根地址最稳
    override fun refererFor(pageUrl: String) = base
    override val sections = listOf(
        Link("最近更新", "${base}latest-updates/"),
        Link("热门影片", "${base}hot/"),
        Link("全新上市", "${base}new-release/"),
    )
    override val categoriesUrl = "${base}categories/"
    override fun sortOptions(listUrl: String): List<Pair<String, String>> {
        val b = listUrl.substringBefore('?')
        val path = android.net.Uri.parse(b).path ?: return emptyList()
        if (!Regex("^/(categories|tags|models|search)/[^/]+/?$").matches(path)) return emptyList()
        val search = path.startsWith("/search/")
        val opts = if (search) listOf("最相关" to "", "最近更新" to "post_date", "最多观看" to "video_viewed", "最高收藏" to "most_favourited")
        else listOf("近期最佳" to "", "最近更新" to "post_date", "最多观看" to "video_viewed", "最高收藏" to "most_favourited")
        return opts.map { (label, v) -> label to if (v.isEmpty()) b else "$b?sort_by=$v" }
    }

    override val loginUrl = "${base}login/"
    override val logoutUrl = "${base}logout/"
    // 爱心 = /my/favourites/videos/；稍後觀看 = fav_type=1
    override fun accountListUrl(like: Boolean) =
        if (like) "${base}my/favourites/videos/" else "${base}my/favourites/videos-watch-later/"
    override fun favActionUrl(videoId: String, like: Boolean, add: Boolean) =
        "$base?mode=async&format=json&action=" + (if (add) "add_to_favourites" else "delete_from_favourites") +
            "&video_id=$videoId&video_ids%5B%5D=$videoId&fav_type=" + (if (like) 0 else 1) + "&playlist_id=0"

    override fun searchUrl(keyword: String) =
        "${base}search/" + java.net.URLEncoder.encode(keyword.trim(), "UTF-8").replace("+", "%20") + "/"
}
