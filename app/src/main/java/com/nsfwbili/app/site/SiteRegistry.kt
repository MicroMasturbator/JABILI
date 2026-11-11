package com.nsfwbili.app.site

import android.net.Uri
import com.nsfwbili.app.data.LocalStore

object SiteRegistry {
    /** 填的地址是否解锁了站点 */
    var unlocked = false
        private set

    /** 原生界面当前显示的站点。未解锁时是一个连不上的占位站点，界面上不会用到 */
    var current: Site = MainSite("invalid.invalid")
        private set

    /** 网页接管能识别的站点 */
    val sites: List<Site> get() = if (unlocked) listOf(current) else emptyList()

    /** 按设置里填的地址重新决定当前站点 */
    fun reload() {
        val addr = LocalStore.siteAddress
        unlocked = SiteLock.unlocks(addr)
        current = MainSite(if (unlocked) SiteLock.normalize(addr!!) else "invalid.invalid")
    }

    /** 网页接管时按网址找站点；不认识的网址用通用嗅探 */
    fun forUrl(url: String): SiteExtractor {
        val host = Uri.parse(url).host?.lowercase() ?: return GenericExtractor
        return sites.firstOrNull { it.matches(host) } ?: GenericExtractor
    }
}
