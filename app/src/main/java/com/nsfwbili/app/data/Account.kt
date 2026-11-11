package com.nsfwbili.app.data

import android.webkit.CookieManager
import com.nsfwbili.app.site.SiteRegistry
import org.json.JSONObject

/** 网站账号：登录状态、把账号里的爱心 / 收藏拉到本地、把本地操作写回账号 */
object Account {

    /** 打开账号的爱心列表来判断是否登录（没登录会被跳到登录页） */
    suspend fun checkLogin(): Boolean {
        val url = SiteRegistry.current.accountListUrl(true) ?: return false
        val ok = runCatching { SiteRegistry.current.listWithLogin(url).second }.getOrDefault(false)
        LocalStore.loggedIn = ok
        return ok
    }

    /** 拉取账号里的列表（最多 30 页），合并到本地。返回 (爱心数, 收藏数) */
    suspend fun pull(): Pair<Int, Int> {
        val site = SiteRegistry.current
        val counts = IntArray(2)
        for ((i, like) in listOf(true, false).withIndex()) {
            var url: String? = site.accountListUrl(like) ?: continue
            val all = ArrayList<VideoItem>()
            var pages = 0
            while (url != null && pages++ < 30) {
                val (page, logged) = site.listWithLogin(url)
                if (!logged) { LocalStore.loggedIn = false; throw IllegalStateException("没有登录") }
                val known = all.mapTo(HashSet()) { it.url }
                val fresh = page.items.filter { known.add(it.url) }
                if (fresh.isEmpty()) break
                all.addAll(fresh)
                url = page.next?.takeIf { it != url }
            }
            LocalStore.mergeRemote(like, all)
            counts[i] = all.size
        }
        LocalStore.lastSync = System.currentTimeMillis()
        return counts[0] to counts[1]
    }

    /**
     * 同步开关打开且已登录时，把一次 加 / 删 写回账号。
     * 返回 null 表示不需要同步；否则返回是否成功。
     */
    suspend fun push(item: VideoItem, like: Boolean, add: Boolean, knownVideoId: String? = null): Boolean? {
        if (!LocalStore.syncToAccount || !LocalStore.loggedIn) return null
        val site = SiteRegistry.current
        val id = knownVideoId ?: site.detail(item.url).videoId ?: return false
        val url = site.favActionUrl(id, like, add) ?: return null
        val o = site.raw(url)
        val body = o.optString("body").trim()
        // 接口返回 {"status":"success"} 或带 errors；已存在 / 已删除也算成功
        if (body.contains("not_logged_in")) { LocalStore.loggedIn = false; return false }
        return runCatching {
            val j = JSONObject(body)
            j.optString("status") == "success" ||
                j.optJSONArray("errors")?.toString()?.contains("already", true) == true
        }.getOrDefault(body.contains("success"))
    }

    /** 走网站自己的退出地址，保留 Cloudflare 的 cookie */
    suspend fun logout() {
        SiteRegistry.current.logoutUrl?.let { u -> runCatching { SiteRegistry.current.raw(u) } }
        CookieManager.getInstance().flush()
        LocalStore.loggedIn = false
    }
}
