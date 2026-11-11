package com.nsfwbili.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.concurrent.Executors

/**
 * 本地数据：收藏 / 爱心 / 历史 / 播放进度存在 Room 数据库，开关类设置存在 SharedPreferences。
 * 启动时把数据库读进内存，界面上的查询都走内存（不卡主线程），写入异步落库。
 */
object LocalStore {
    private const val MAX_HISTORY = 500
    private const val MAX_PROGRESS = 2000
    private const val FAV = "fav"
    private const val LIKE = "like"
    private const val HIST = "hist"

    private lateinit var sp: SharedPreferences
    private lateinit var dao: LibraryDao
    private val io = Executors.newSingleThreadExecutor()
    private var site = "s1"

    /** type -> 按加入时间倒序的列表 */
    private val lists = hashMapOf<String, MutableList<VideoItem>>(FAV to ArrayList(), LIKE to ArrayList(), HIST to ArrayList())
    private val progress = HashMap<String, ProgressEntity>()

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("store", Context.MODE_PRIVATE)
        dao = LibraryDb.open(ctx).dao()
        // 首次启动把旧版存在 prefs 里的 JSON 搬进数据库；然后整体读进内存
        io.submit { migrateFromPrefs(); loadAll() }.get()
    }

    private fun loadAll() {
        dao.allEntries().forEach { r -> lists[r.type]?.add(VideoItem(r.url, r.title, r.cover, r.duration, r.sub)) }
        dao.allProgress().forEach { progress[it.url] = it }
    }

    private fun migrateFromPrefs() {
        val keys = listOf(FAV, LIKE, HIST)
        val pos = sp.all.filterKeys { it.startsWith("pos:") }
        if (keys.none { sp.contains(it) } && pos.isEmpty()) return
        val now = System.currentTimeMillis()
        for (k in keys) {
            val list = sp.getString(k, null)?.let { runCatching { JSONArray(it).videos() }.getOrNull() } ?: continue
            dao.upsertVideos(list.map { VideoEntity.of(it, site) })
            // 保持原来的顺序：越靠前 addedAt 越大
            dao.upsertEntries(list.mapIndexed { i, v -> LibraryEntry(k, v.url, now - i) })
        }
        dao.upsertProgressAll(pos.mapNotNull { (k, v) -> (v as? Long)?.let { ProgressEntity(k.removePrefix("pos:"), it, 0, now) } })
        sp.edit().apply { keys.forEach { remove(it) }; pos.keys.forEach { remove(it) } }.apply()
    }

    // ---------------- 收藏 / 爱心 / 历史 ----------------

    private fun list(type: String) = lists.getValue(type)

    private fun contains(type: String, url: String) = list(type).any { it.url == url }

    private fun add(type: String, v: VideoItem) {
        val l = list(type); l.removeAll { it.url == v.url }; l.add(0, v)
        val now = System.currentTimeMillis()
        io.execute { dao.upsertVideo(VideoEntity.of(v, site)); dao.upsertEntry(LibraryEntry(type, v.url, now)) }
    }

    private fun remove(type: String, url: String) {
        list(type).removeAll { it.url == url }
        io.execute { dao.deleteEntry(type, url) }
    }

    private fun toggle(type: String, v: VideoItem): Boolean =
        if (contains(type, v.url)) { remove(type, v.url); false } else { add(type, v); true }

    /** "收藏"（对应站点账号的稍後觀看 / fav_type=1） */
    fun favorites(): List<VideoItem> = list(FAV).toList()
    fun isFavorite(url: String) = contains(FAV, url)
    fun toggleFavorite(v: VideoItem) = toggle(FAV, v)

    /** "爱心"（对应站点账号的爱心 / fav_type=0） */
    fun likes(): List<VideoItem> = list(LIKE).toList()
    fun isLiked(url: String) = contains(LIKE, url)
    fun toggleLike(v: VideoItem) = toggle(LIKE, v)

    /** 把账号里拉下来的列表合并进本地（账号里的排前面，本地独有的保留） */
    fun mergeRemote(like: Boolean, remote: List<VideoItem>) {
        val type = if (like) LIKE else FAV
        val l = list(type)
        val urls = remote.mapTo(HashSet()) { it.url }
        val merged = remote + l.filter { it.url !in urls }
        l.clear(); l.addAll(merged)
        val now = System.currentTimeMillis()
        io.execute {
            dao.upsertVideos(merged.map { VideoEntity.of(it, site) })
            dao.upsertEntries(merged.mapIndexed { i, v -> LibraryEntry(type, v.url, now - i) })
        }
    }

    fun history(): List<VideoItem> = list(HIST).toList()
    fun addHistory(v: VideoItem) {
        add(HIST, v)
        val l = list(HIST)
        if (l.size > MAX_HISTORY) { while (l.size > MAX_HISTORY) l.removeAt(l.size - 1); io.execute { dao.trimHistory(MAX_HISTORY) } }
    }
    fun removeHistory(url: String) = remove(HIST, url)
    fun restoreHistory(v: VideoItem, index: Int) {
        val l = list(HIST); l.removeAll { it.url == v.url }; l.add(index.coerceIn(0, l.size), v)
        io.execute { dao.upsertVideo(VideoEntity.of(v, site)); dao.upsertEntry(LibraryEntry(HIST, v.url, System.currentTimeMillis() - index)) }
    }
    /** 看过：在历史里，且有进度记录（看完的会被重置为 0，但 durationMs 还在） */
    fun isWatched(url: String) = progress[url]?.durationMs?.let { it > 0 } == true

    fun clearHistory() { list(HIST).clear(); io.execute { dao.clear(HIST) } }

    // ---------------- 播放进度 ----------------

    fun progress(url: String) = progress[url]?.posMs ?: 0L
    fun duration(url: String) = progress[url]?.durationMs ?: 0L

    fun saveProgress(url: String, ms: Long, durationMs: Long = 0) {
        val e = ProgressEntity(url, ms, if (durationMs > 0) durationMs else duration(url), System.currentTimeMillis())
        progress[url] = e
        io.execute {
            dao.upsertProgress(e)
            if (progress.size > MAX_PROGRESS) dao.trimProgress(MAX_PROGRESS)
        }
    }

    // ---------------- 设置 ----------------

    var syncToAccount: Boolean
        get() = sp.getBoolean("sync", false)
        set(v) { sp.edit().putBoolean("sync", v).apply() }

    var loggedIn: Boolean
        get() = sp.getBoolean("loggedIn", false)
        set(v) { sp.edit().putBoolean("loggedIn", v).apply() }

    var lastSync: Long
        get() = sp.getLong("lastSync", 0L)
        set(v) { sp.edit().putLong("lastSync", v).apply() }

    var gridMode: Boolean
        get() = sp.getBoolean("grid", true)
        set(v) { sp.edit().putBoolean("grid", v).apply() }

    /** 0 跟随系统，1 浅色，2 深色 */
    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }

    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic", false)
        set(v) { sp.edit().putBoolean("dynamic", v).apply() }

    /** 默认播放速度 */
    var playbackSpeed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) { sp.edit().putFloat("speed", v).apply() }

    /** 最近搜索，最新的在前，最多 20 条 */
    fun searchHistory(): List<String> =
        sp.getString("searches", null)?.let { runCatching { JSONArray(it).let { a -> (0 until a.length()).map(a::getString) } }.getOrNull() } ?: emptyList()
    fun addSearch(q: String) {
        val l = (listOf(q.trim()) + searchHistory().filter { it != q.trim() }).take(20)
        sp.edit().putString("searches", JSONArray(l).toString()).apply()
    }
    fun clearSearches() = sp.edit().remove("searches").apply()

    var translateTitles: Boolean
        get() = sp.getBoolean("translate", false)
        set(v) { sp.edit().putBoolean("translate", v).apply() }

    /** 用户填的站点地址（像密码一样），null 表示还没走过引导 */
    var siteAddress: String?
        get() = sp.getString("site_addr", null)
        set(v) { sp.edit().putString("site_addr", v).apply() }
}
