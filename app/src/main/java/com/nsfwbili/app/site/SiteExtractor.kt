package com.nsfwbili.app.site

/**
 * 每个站点一个实现。新增站点：写一个 SiteExtractor，然后在 [SiteRegistry] 里注册即可。
 */
interface SiteExtractor {
    /** 显示在首页的名字 */
    val name: String

    /** 首页地址 */
    val homeUrl: String

    /**
     * 允许 WebView 跳转的域名（后缀匹配）。不在列表里的跳转（多半是广告弹窗）会被拦截。
     * 返回空列表表示不限制。
     */
    val allowedHosts: List<String>

    /** 这个站点是否负责处理该网址 */
    fun matches(host: String): Boolean = allowedHosts.any { host == it || host.endsWith(".$it") }

    /**
     * 一段 JS 表达式，在页面里求值后返回视频流地址（字符串）或 null。
     * 用于站点把地址写在页面变量里的情况（比如页面里的 hlsUrl）。
     */
    val streamUrlJs: String? get() = null

    /** 网络嗅探：判断 WebView 发出的某个请求是不是视频流 */
    fun isStreamRequest(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd")
    }

    /** 播放器请求视频时带的 Referer */
    fun refererFor(pageUrl: String): String = pageUrl
}
