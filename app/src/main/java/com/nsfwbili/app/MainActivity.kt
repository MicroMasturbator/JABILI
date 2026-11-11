package com.nsfwbili.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.nsfwbili.app.data.LocalStore
import com.nsfwbili.app.databinding.ActivityMainBinding
import com.nsfwbili.app.site.SiteRegistry
import com.nsfwbili.app.ui.BaseActivity
import com.nsfwbili.app.ui.CategoriesFragment
import com.nsfwbili.app.ui.Nav
import com.nsfwbili.app.ui.SearchHistory
import com.nsfwbili.app.ui.VideoListFragment

/** 首页：侧边栏 + 原生列表（类似 EhViewer 的结构） */
class MainActivity : BaseActivity() {
    private lateinit var b: ActivityMainBinding
    private var currentId = 0

    private companion object {
        const val ID_CATEGORIES = 100
        const val ID_FAV = 101
        const val ID_HIST = 102
        const val ID_WEB = 103
        const val ID_LIKE = 104
        const val ID_SETTINGS = 105
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LocalStore.siteAddress == null) {
            startActivity(Intent(this, com.nsfwbili.app.ui.SetupActivity::class.java)); finish(); return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        ActionBarDrawerToggle(this, b.drawer, b.toolbar, 0, 0).also { b.drawer.addDrawerListener(it); it.syncState() }

        val site = SiteRegistry.current
        val m = b.nav.menu
        if (!SiteRegistry.unlocked) {
            // 地址不对：空页面，只留设置入口
            m.add(3, ID_SETTINGS, 71, getString(R.string.settings))
            b.nav.setNavigationItemSelectedListener {
                b.drawer.closeDrawer(GravityCompat.START)
                startActivity(Intent(this, com.nsfwbili.app.ui.SettingsActivity::class.java)); false
            }
            supportActionBar?.title = ""
            return
        }
        b.nav.getHeaderView(0).findViewById<android.widget.TextView>(R.id.navSite).text = site.name
        site.sections.forEachIndexed { i, s -> m.add(1, i, i, s.name).isCheckable = true }
        if (site.categoriesUrl != null) m.add(1, ID_CATEGORIES, 50, getString(R.string.categories)).isCheckable = true
        m.add(2, ID_LIKE, 59, "爱心").isCheckable = true
        m.add(2, ID_FAV, 60, getString(R.string.favorites)).isCheckable = true
        m.add(2, ID_HIST, 61, getString(R.string.history)).isCheckable = true
        m.add(3, ID_WEB, 70, getString(R.string.open_web))
        m.add(3, ID_SETTINGS, 71, getString(R.string.settings))
        b.nav.setNavigationItemSelectedListener { item ->
            b.drawer.closeDrawer(GravityCompat.START)
            if (item.itemId == ID_WEB) { Nav.web(this, site.homeUrl); false }
            else if (item.itemId == ID_SETTINGS) { startActivity(Intent(this, com.nsfwbili.app.ui.SettingsActivity::class.java)); false }
            else { show(item.itemId); true }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (b.drawer.isDrawerOpen(GravityCompat.START)) b.drawer.closeDrawer(GravityCompat.START)
                else if (currentId != 0) show(0)
                else finish()
            }
        })

        currentId = savedInstanceState?.getInt("cur") ?: 0
        if (savedInstanceState == null) show(0) else title(currentId)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putInt("cur", currentId) }

    private fun title(id: Int) {
        val site = SiteRegistry.current
        supportActionBar?.title = when (id) {
            ID_CATEGORIES -> getString(R.string.categories)
            ID_FAV -> getString(R.string.favorites)
            ID_LIKE -> "爱心"
            ID_HIST -> getString(R.string.history)
            else -> site.sections.getOrNull(id)?.name ?: site.name
        }
        b.nav.setCheckedItem(id)
        invalidateOptionsMenu()
    }

    private fun show(id: Int) {
        val site = SiteRegistry.current
        val f: Fragment = when (id) {
            ID_CATEGORIES -> CategoriesFragment()
            ID_FAV -> VideoListFragment.local("fav")
            ID_LIKE -> VideoListFragment.local("like")
            ID_HIST -> VideoListFragment.local("hist")
            else -> VideoListFragment.remote(site.sections[id].url)
        }
        currentId = id
        supportFragmentManager.beginTransaction().replace(R.id.content, f).commit()
        title(id)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!SiteRegistry.unlocked) return true
        menuInflater.inflate(R.menu.main, menu)
        val sv = menu.findItem(R.id.action_search).actionView as SearchView
        sv.queryHint = getString(R.string.search_hint)
        SearchHistory.attach(this, sv) { q -> Nav.list(this, "搜索：$q", SiteRegistry.current.searchUrl(q)) }
        menu.findItem(R.id.action_translate).isChecked = LocalStore.translateTitles
        if (currentId == ID_HIST) menu.add(0, 9, 9, R.string.clear_history)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_layout -> {
                LocalStore.gridMode = !LocalStore.gridMode
                (supportFragmentManager.findFragmentById(R.id.content) as? VideoListFragment)?.setGrid(LocalStore.gridMode)
            }
            R.id.action_web -> Nav.web(this, SiteRegistry.current.homeUrl)
            R.id.action_translate -> {
                LocalStore.translateTitles = !LocalStore.translateTitles
                item.isChecked = LocalStore.translateTitles
                if (LocalStore.translateTitles) android.widget.Toast.makeText(this, "首次使用会下载日译中离线模型（约 30MB）", android.widget.Toast.LENGTH_SHORT).show()
                (supportFragmentManager.findFragmentById(R.id.content) as? VideoListFragment)?.setGrid(LocalStore.gridMode)
            }
            9 -> AlertDialog.Builder(this).setMessage(R.string.clear_history)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    LocalStore.clearHistory()
                    (supportFragmentManager.findFragmentById(R.id.content) as? VideoListFragment)?.reload()
                }.setNegativeButton(android.R.string.cancel, null).show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
