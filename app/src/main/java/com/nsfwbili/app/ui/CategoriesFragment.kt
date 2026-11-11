package com.nsfwbili.app.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nsfwbili.app.R
import com.nsfwbili.app.data.ChallengeException
import com.nsfwbili.app.data.Link
import com.nsfwbili.app.databinding.FragmentListBinding
import com.nsfwbili.app.site.SiteRegistry
import kotlinx.coroutines.launch

/** 分类页：带封面的主题分类 + 下面一大片标签 */
class CategoriesFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private var cats: List<Link> = emptyList()
    private var tagGroups: List<Pair<String, List<Link>>> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(inflater, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        b.refresh.setOnRefreshListener { load() }
        b.retryBtn.setOnClickListener {
            if (b.errorText.text == getString(R.string.challenge_needed))
                (activity as? BaseActivity)?.requestChallenge(SiteRegistry.current.categoriesUrl!!) { if (it && _b != null) load() }
            else load()
        }
        b.webBtn.setOnClickListener { Nav.web(requireContext(), SiteRegistry.current.categoriesUrl!!) }
        b.recycler.adapter = Adapter()
        layout()
        load()
    }

    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); layout() }

    private fun layout() {
        val span = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        b.recycler.layoutManager = GridLayoutManager(requireContext(), span).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(p: Int) = if (p >= cats.size) span else 1
            }
        }
    }

    fun load() {
        b.errorBox.visibility = View.GONE
        b.refresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val p = SiteRegistry.current.list(SiteRegistry.current.categoriesUrl!!)
                cats = p.categories
                tagGroups = p.tags.groupBy { it.group }.toList()
                b.recycler.adapter?.notifyDataSetChanged()
                if (cats.isEmpty() && tagGroups.isEmpty()) { b.errorText.text = getString(R.string.empty); b.errorBox.visibility = View.VISIBLE }
            } catch (e: ChallengeException) {
                b.errorText.text = getString(R.string.challenge_needed); b.errorBox.visibility = View.VISIBLE
                (activity as? BaseActivity)?.requestChallenge(SiteRegistry.current.categoriesUrl!!) { if (it && _b != null) load() }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                b.errorText.text = "加载失败：${e.message}"; b.errorBox.visibility = View.VISIBLE
            } finally { _b?.refresh?.isRefreshing = false }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = cats.size + tagGroups.size
        override fun getItemViewType(p: Int) = if (p < cats.size) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, t: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            val v = inf.inflate(if (t == 0) R.layout.item_category else R.layout.item_tags, parent, false)
            return object : RecyclerView.ViewHolder(v) {}
        }
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            val v = h.itemView
            if (p < cats.size) {
                val c = cats[p]
                v.findViewById<RatioImageView>(R.id.cover).loadCover(c.cover)
                v.findViewById<TextView>(R.id.title).text = if (c.group.isNotBlank()) "${c.name}  ${c.group}" else c.name
                v.setOnClickListener { Nav.list(requireContext(), c.name, c.url) }
            } else {
                val (name, tags) = tagGroups[p - cats.size]
                v.findViewById<TextView>(R.id.groupTitle).text = name.ifBlank { getString(R.string.tags) }
                val g = v.findViewById<ChipGroup>(R.id.chips)
                g.removeAllViews()
                tags.forEach { t ->
                    g.addView(Chip(requireContext()).apply {
                        text = t.name
                        setOnClickListener { Nav.list(requireContext(), "#${t.name}", t.url) }
                    })
                }
            }
        }
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}
