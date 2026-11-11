package com.nsfwbili.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nsfwbili.app.data.ChallengeException
import com.nsfwbili.app.data.VideoItem
import com.nsfwbili.app.site.SiteRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 远程列表的数据和翻页状态。放在 ViewModel 里，旋转 / 切深色模式重建界面时不用重新加载。 */
class ListViewModel : ViewModel() {

    data class State(
        val items: List<VideoItem> = emptyList(),
        /** 首次 / 刷新加载中 */
        val refreshing: Boolean = false,
        /** 加载下一页中 */
        val loadingMore: Boolean = false,
        val end: Boolean = false,
        /** 首屏出错（整页显示错误） */
        val error: String? = null,
        /** 翻页出错（底部显示，可点重试） */
        val moreError: String? = null,
        /** 需要网站验证的网址 */
        val challengeUrl: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var firstUrl: String? = null
    private var nextUrl: String? = null
    private var job: Job? = null

    /** 第一次绑定网址时加载；之后重建界面直接复用已有数据 */
    fun bind(url: String) {
        if (firstUrl == url) return
        firstUrl = url
        refresh()
    }

    fun refresh() {
        val url = firstUrl ?: return
        job?.cancel()
        nextUrl = null
        _state.value = State(refreshing = true)
        fetch(url, replace = true)
    }

    fun loadMore() {
        val s = _state.value
        if (job?.isActive == true || s.end || s.moreError != null || s.refreshing) return
        fetch(nextUrl ?: return, replace = false)
    }

    fun retryMore() {
        _state.value = _state.value.copy(moreError = null)
        loadMore()
    }

    fun challengeHandled() { _state.value = _state.value.copy(challengeUrl = null) }

    private fun fetch(url: String, replace: Boolean) {
        if (!replace) _state.value = _state.value.copy(loadingMore = true)
        job = viewModelScope.launch {
            try {
                val page = SiteRegistry.current.list(url)
                val old = if (replace) emptyList() else _state.value.items
                // 去重（翻页时偶尔会重复上一页最后几条）
                val known = old.mapTo(HashSet()) { it.url }
                val fresh = page.items.filter { known.add(it.url) }
                nextUrl = page.next?.takeIf { it != url && fresh.isNotEmpty() }
                _state.value = State(items = old + fresh, end = nextUrl == null)
            } catch (e: ChallengeException) {
                fail(replace, "需要网站验证", url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(replace, "加载失败：${e.message}", null)
            }
        }
    }

    private fun fail(replace: Boolean, msg: String, challenge: String?) {
        val s = _state.value
        _state.value = if (replace || s.items.isEmpty())
            s.copy(refreshing = false, loadingMore = false, error = msg, challengeUrl = challenge)
        else s.copy(loadingMore = false, moreError = msg, challengeUrl = challenge)
    }
}
