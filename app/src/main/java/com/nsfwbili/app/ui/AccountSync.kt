package com.nsfwbili.app.ui

import android.content.Context
import android.widget.Toast
import com.nsfwbili.app.data.Account
import com.nsfwbili.app.data.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 后台把一次爱心 / 收藏操作写回账号（同步开关关闭或未登录时什么都不做） */
object AccountSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun push(ctx: Context, v: VideoItem, like: Boolean, add: Boolean, videoId: String? = null) {
        scope.launch {
            val r = runCatching { Account.push(v, like, add, videoId) }.getOrElse { false }
            if (r == false) Toast.makeText(ctx, "同步到账号失败，已保存在本地", Toast.LENGTH_SHORT).show()
        }
    }
}
