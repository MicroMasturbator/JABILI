package com.nsfwbili.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.nsfwbili.app.R
import com.nsfwbili.app.data.LocalStore
import com.nsfwbili.app.databinding.ActivityListBinding

/** 标签 / 女优 / 分类 / 搜索结果 */
class ListActivity : BaseActivity() {
    private lateinit var b: ActivityListBinding
    private val frag get() = supportFragmentManager.findFragmentById(R.id.content) as? VideoListFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra("title")
        val url = intent.getStringExtra("url")!!
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.content, VideoListFragment.remote(url)).commit()
        }
        setupSort(url, savedInstanceState?.getInt("sort") ?: 0)
    }

    private var sortIndex = 0

    /** 分类 / 标签 / 女优 / 搜索页顶部的排序 Chip */
    private fun setupSort(url: String, selected: Int) {
        val opts = com.nsfwbili.app.site.SiteRegistry.current.sortOptions(url)
        if (opts.isEmpty()) return
        b.sortBar.visibility = android.view.View.VISIBLE
        sortIndex = selected
        opts.forEachIndexed { i, (label, u) ->
            val chip = com.google.android.material.chip.Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                id = android.view.View.generateViewId()
                text = label
                isCheckable = true
                isChecked = i == selected
                setOnClickListener {
                    if (sortIndex == i) return@setOnClickListener
                    sortIndex = i
                    supportFragmentManager.beginTransaction().replace(R.id.content, VideoListFragment.remote(u)).commit()
                }
            }
            b.sortChips.addView(chip)
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("sort", sortIndex)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.toggle_layout)
        menu.add(0, 2, 1, R.string.open_web)
        menu.add(0, 3, 2, R.string.translate_titles).apply { isCheckable = true; isChecked = LocalStore.translateTitles }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            1 -> { LocalStore.gridMode = !LocalStore.gridMode; frag?.setGrid(LocalStore.gridMode) }
            2 -> Nav.web(this, intent.getStringExtra("url")!!)
            3 -> { LocalStore.translateTitles = !LocalStore.translateTitles; item.isChecked = LocalStore.translateTitles; frag?.setGrid(LocalStore.gridMode) }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
