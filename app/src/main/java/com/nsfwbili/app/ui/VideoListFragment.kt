package com.nsfwbili.app.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nsfwbili.app.R
import com.nsfwbili.app.data.LocalStore
import com.nsfwbili.app.databinding.FragmentListBinding
import com.nsfwbili.app.site.SiteRegistry
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * 视频列表（网格 / 列表两种模式，下拉刷新，滑到底自动加载下一页）。
 * mode = remote：从网站抓（数据在 ListViewModel）；fav / like / hist：本地收藏 / 爱心 / 历史。
 */
class VideoListFragment : Fragment() {

    companion object {
        fun remote(url: String) = VideoListFragment().apply { arguments = Bundle().apply { putString("mode", "remote"); putString("url", url) } }
        fun local(mode: String) = VideoListFragment().apply { arguments = Bundle().apply { putString("mode", mode) } }
    }

    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: VideoAdapter
    private val vm: ListViewModel by viewModels()
    private val mode get() = requireArguments().getString("mode")!!
    private val firstUrl get() = requireArguments().getString("url")
    private var challengeAsked = false

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = VideoAdapter(LocalStore.gridMode, { Nav.detail(requireContext(), it) }, { v ->
            val extra = if (mode == "hist") listOf("🗑  从历史里删除" to { deleteHistory(v) }) else emptyList()
            VideoActions.show(requireContext(), v, extra) {
                if (mode == "remote") adapter.notifyDataSetChanged() else reload()
            }
        })
        adapter.onFooterClick = { vm.retryMore() }
        b.recycler.adapter = adapter
        applyLayout()
        b.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && nearEnd()) vm.loadMore()
            }
        })
        b.refresh.setOnRefreshListener { reload() }
        if (mode == "hist") enableSwipeDelete()
        b.retryBtn.setOnClickListener {
            val u = vm.state.value.challengeUrl
            if (u != null) challenge(u) else reload()
        }
        b.webBtn.setOnClickListener { Nav.web(requireContext(), firstUrl ?: SiteRegistry.current.homeUrl) }
        b.webBtn.visibility = if (mode == "remote") View.VISIBLE else View.GONE

        if (mode == "remote") {
            vm.bind(firstUrl!!)
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect { render(it) } }
            }
        }
    }

    private fun render(s: ListViewModel.State) {
        b.refresh.isRefreshing = s.refreshing
        if (adapter.items != s.items) {
            val grew = s.items.size > adapter.items.size && s.items.subList(0, adapter.items.size) == adapter.items
            val start = adapter.items.size
            adapter.items.clear(); adapter.items.addAll(s.items)
            if (grew) adapter.notifyItemRangeInserted(start, s.items.size - start) else adapter.notifyDataSetChanged()
        }
        adapter.footer = when {
            s.refreshing || s.error != null -> null
            s.moreError != null -> "${s.moreError}（点这里重试）"
            s.loadingMore -> getString(R.string.loading_more)
            s.items.isEmpty() -> getString(R.string.empty)
            s.end -> getString(R.string.no_more)
            else -> null
        }
        b.errorBox.visibility = if (s.error != null) View.VISIBLE else View.GONE
        b.errorText.text = if (s.challengeUrl != null) getString(R.string.challenge_needed) else s.error
        if (s.challengeUrl != null && !challengeAsked) { challengeAsked = true; challenge(s.challengeUrl) }
        // 第一页没占满屏幕时，滚动事件不会触发，主动接着翻
        if (!s.refreshing && !s.loadingMore && !s.end && s.items.isNotEmpty())
            b.recycler.post { if (_b != null && nearEnd()) vm.loadMore() }
    }

    override fun onResume() {
        super.onResume()
        if (mode != "remote") reload()
        else if (adapter.items.isNotEmpty()) adapter.notifyItemRangeChanged(0, adapter.items.size) // 刷新 ♥/★/进度
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    fun setGrid(grid: Boolean) {
        val pos = (b.recycler.layoutManager as? GridLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        adapter.grid = grid
        applyLayout()
        adapter.notifyDataSetChanged()
        b.recycler.scrollToPosition(maxOf(pos, 0))
    }

    /** 按屏幕宽度自动分列：网格每列约 180dp，列表每列约 360dp */
    private fun applyLayout() {
        val widthDp = resources.configuration.screenWidthDp
        val span = if (adapter.grid) maxOf(2, widthDp / 180) else maxOf(1, widthDp / 360)
        val lm = GridLayoutManager(requireContext(), span)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(p: Int) = if (adapter.isFooter(p)) span else 1
        }
        b.recycler.layoutManager = lm
    }

    fun reload() {
        when (mode) {
            "fav", "hist", "like" -> {
                val list = when (mode) { "fav" -> LocalStore.favorites(); "like" -> LocalStore.likes(); else -> LocalStore.history() }
                adapter.items.clear(); adapter.items.addAll(list)
                adapter.footer = if (list.isEmpty()) getString(R.string.empty) else null
                adapter.notifyDataSetChanged()
                b.refresh.isRefreshing = false
            }
            else -> { challengeAsked = false; vm.refresh() }
        }
    }

    /** 历史页左右滑删除，可撤销 */
    private fun enableSwipeDelete() {
        androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, a: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                if (adapter.isFooter(vh.bindingAdapterPosition)) 0 else super.getSwipeDirs(rv, vh)
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                adapter.items.getOrNull(pos)?.let { deleteHistory(it) }
            }
        }).attachToRecyclerView(b.recycler)
    }

    private fun deleteHistory(v: com.nsfwbili.app.data.VideoItem) {
        val index = adapter.items.indexOfFirst { it.url == v.url }
        LocalStore.removeHistory(v.url)
        reload()
        com.google.android.material.snackbar.Snackbar.make(b.root, "已从历史删除", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .setAction("撤销") { LocalStore.restoreHistory(v, index); reload() }.show()
    }

    private fun nearEnd(): Boolean {
        val lm = b.recycler.layoutManager as? GridLayoutManager ?: return false
        return lm.findLastVisibleItemPosition() >= adapter.itemCount - 6
    }

    private fun challenge(url: String) =
        (activity as? BaseActivity)?.requestChallenge(url) { ok ->
            vm.challengeHandled()
            if (ok && _b != null) reload()
        }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}
