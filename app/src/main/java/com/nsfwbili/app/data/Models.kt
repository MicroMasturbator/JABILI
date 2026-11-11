package com.nsfwbili.app.data

import org.json.JSONArray
import org.json.JSONObject

data class VideoItem(
    val url: String,
    val title: String,
    val cover: String,
    val duration: String = "",
    val sub: String = "",
) {
    fun toJson() = JSONObject().put("url", url).put("title", title).put("cover", cover)
        .put("duration", duration).put("sub", sub)

    companion object {
        fun from(o: JSONObject) = VideoItem(
            url = o.optString("url"), title = o.optString("title"), cover = o.optString("cover"),
            duration = o.optString("duration"), sub = o.optString("sub"),
        )
    }
}

/** group：标签的分组名；分类卡片上是影片数量；详情页标签里 "cat" 表示是分类 */
data class Link(val name: String, val url: String, val cover: String = "", val group: String = "") {
    companion object {
        fun from(o: JSONObject) = Link(o.optString("name"), o.optString("url"), o.optString("cover"), o.optString("group"))
    }
}

data class ListPage(
    val items: List<VideoItem>,
    val next: String?,
    val categories: List<Link>,
    val tags: List<Link>,
)

data class VideoDetail(
    val url: String,
    val title: String,
    val cover: String,
    val info: String,
    val hls: String?,
    val models: List<Link>,
    val tags: List<Link>,
    val related: List<VideoItem>,
    val videoId: String? = null,
)

/** 碰到 Cloudflare 验证，需要用户在可见的网页里过一下 */
class ChallengeException(val url: String) : Exception("需要通过网站验证")

internal fun JSONArray?.videos() = this?.let { a -> (0 until a.length()).map { VideoItem.from(a.getJSONObject(it)) } } ?: emptyList()
internal fun JSONArray?.links() = this?.let { a -> (0 until a.length()).map { Link.from(a.getJSONObject(it)) } } ?: emptyList()
internal fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
