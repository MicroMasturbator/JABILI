package com.nsfwbili.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nsfwbili.app.R
import com.nsfwbili.app.data.LocalStore
import com.nsfwbili.app.data.VideoItem

/** 长按视频卡片弹出的底部菜单 */
object VideoActions {
    private val codeRe = Regex("^[A-Za-z0-9]+-[0-9]+")

    fun show(ctx: Context, v: VideoItem, extra: List<Pair<String, () -> Unit>> = emptyList(), onChanged: () -> Unit = {}) {
        val dialog = BottomSheetDialog(ctx)
        val view = LayoutInflater.from(ctx).inflate(R.layout.sheet_video_actions, null)
        view.findViewById<TextView>(R.id.sheetTitle).text = v.title
        val box = view.findViewById<android.widget.LinearLayout>(R.id.sheetActions)
        val code = codeRe.find(v.title)?.value

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        val liked = LocalStore.isLiked(v.url)
        val faved = LocalStore.isFavorite(v.url)
        actions += (if (liked) "♥  取消爱心" else "♡  爱心") to {
            val added = LocalStore.toggleLike(v); AccountSync.push(ctx.applicationContext, v, true, added); onChanged()
        }
        actions += (if (faved) "★  取消收藏" else "☆  收藏") to {
            val added = LocalStore.toggleFavorite(v); AccountSync.push(ctx.applicationContext, v, false, added); onChanged()
        }
        if (code != null) actions += "⧉  复制番号 $code" to {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
            Toast.makeText(ctx, "已复制 $code", Toast.LENGTH_SHORT).show()
        }
        actions += "⇪  分享链接" to {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "${v.title}\n${v.url}"), "分享"))
        }
        actions += "🌐  用网页版打开" to { Nav.web(ctx, v.url) }
        actions += extra

        val pad = (16 * ctx.resources.displayMetrics.density).toInt()
        actions.forEach { (label, run) ->
            box.addView(TextView(ctx).apply {
                text = label
                textSize = 15f
                setPadding(pad, pad * 3 / 4, pad, pad * 3 / 4)
                val tv = android.util.TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                setOnClickListener { dialog.dismiss(); run() }
            })
        }
        dialog.setContentView(view)
        dialog.show()
    }
}
