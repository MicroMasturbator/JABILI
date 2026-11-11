package com.nsfwbili.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nsfwbili.app.R
import com.nsfwbili.app.data.LocalStore
import com.nsfwbili.app.data.TitleTranslator
import com.nsfwbili.app.data.VideoItem

class VideoAdapter(
    var grid: Boolean,
    private val onClick: (VideoItem) -> Unit,
    private val onLongClick: ((VideoItem) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val items = ArrayList<VideoItem>()
    var onFooterClick: (() -> Unit)? = null
    /** null = 不显示底部；否则显示这段文字 */
    var footer: String? = null
        set(v) {
            val had = field != null
            field = v
            if (had && v == null) notifyItemRemoved(items.size)
            else if (!had && v != null) notifyItemInserted(items.size)
            else if (v != null) notifyItemChanged(items.size)
        }

    fun isFooter(pos: Int) = footer != null && pos == items.size

    override fun getItemCount() = items.size + if (footer != null) 1 else 0
    override fun getItemViewType(pos: Int) = when {
        isFooter(pos) -> 2
        grid -> 0
        else -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (type) {
            2 -> object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_footer, parent, false)) {}
            0 -> VH(inf.inflate(R.layout.item_video_grid, parent, false))
            else -> VH(inf.inflate(R.layout.item_video_list, parent, false))
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        if (h is VH) h.bind(items[pos]) else {
            (h.itemView as TextView).text = footer
            h.itemView.setOnClickListener { onFooterClick?.invoke() }
        }
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val cover = v.findViewById<RatioImageView>(R.id.cover)
        private val title = v.findViewById<TextView>(R.id.title)
        private val duration = v.findViewById<TextView>(R.id.duration)
        private val sub = v.findViewById<TextView>(R.id.sub)
        private val badge = v.findViewById<TextView>(R.id.badge)
        private val watched = v.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.watched)

        fun bind(item: VideoItem) {
            cover.loadCover(item.cover)
            duration.text = item.duration
            duration.visibility = if (item.duration.isBlank()) View.GONE else View.VISIBLE
            itemView.tag = item.url
            val needTranslate = LocalStore.translateTitles && TitleTranslator.isJapanese(item.title)
            showTitle(item, if (needTranslate) TitleTranslator.cached(item.title) else null)
            if (needTranslate && TitleTranslator.cached(item.title) == null) {
                TitleTranslator.translate(item.title) { t ->
                    if (itemView.tag == item.url) showTitle(item, t) // 防止复用后串行
                }
            }
            // 右上角：♥ 爱心 / ★ 收藏 / 已看；封面底部：观看进度
            val marks = buildString {
                if (LocalStore.isLiked(item.url)) append("♥")
                if (LocalStore.isFavorite(item.url)) append("★")
            }
            val pos = LocalStore.progress(item.url)
            val dur = LocalStore.duration(item.url)
            val seen = dur > 0 && pos == 0L // 看完后进度会重置为 0
            badge.text = listOfNotNull(marks.ifEmpty { null }, if (seen) "已看" else null).joinToString(" ")
            badge.visibility = if (badge.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            if (pos > 0 && dur > 0) {
                watched.max = 1000
                watched.progress = (pos * 1000 / dur).toInt().coerceIn(10, 1000)
                watched.visibility = View.VISIBLE
            } else watched.visibility = View.GONE
            itemView.setOnClickListener { onClick(item) }
        }

        /** translated != null 时显示译文，并在观看/爱心后面标注"机翻" */
        private fun showTitle(item: VideoItem, translated: String?) {
            title.text = translated ?: item.title
            val s = if (translated != null) (if (item.sub.isBlank()) "机翻" else "${item.sub}   · 机翻") else item.sub
            sub.text = s
            sub.visibility = if (s.isBlank()) View.GONE else View.VISIBLE
            itemView.setOnLongClickListener { onLongClick?.invoke(item); onLongClick != null }
        }
    }
}
