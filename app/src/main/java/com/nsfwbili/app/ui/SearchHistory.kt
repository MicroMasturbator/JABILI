package com.nsfwbili.app.ui

import android.content.Context
import android.database.MatrixCursor
import android.provider.BaseColumns
import androidx.appcompat.widget.SearchView
import androidx.cursoradapter.widget.SimpleCursorAdapter
import com.nsfwbili.app.data.LocalStore

/** 搜索框下拉：最近搜索（输入时按关键字过滤），最后一项可清空 */
object SearchHistory {
    private const val CLEAR = "\u0000clear"

    fun attach(ctx: Context, sv: SearchView, onSearch: (String) -> Unit) {
        val adapter = SimpleCursorAdapter(
            ctx, android.R.layout.simple_list_item_1, null,
            arrayOf("q"), intArrayOf(android.R.id.text1), 0
        )
        sv.suggestionsAdapter = adapter
        sv.isQueryRefinementEnabled = false
        (sv.findViewById<android.widget.AutoCompleteTextView>(androidx.appcompat.R.id.search_src_text))?.threshold = 0

        fun refresh(text: String) {
            val c = MatrixCursor(arrayOf(BaseColumns._ID, "q", "raw"))
            val list = LocalStore.searchHistory().filter { text.isBlank() || it.contains(text.trim(), true) }
            list.forEachIndexed { i, q -> c.addRow(arrayOf(i, "🕘  $q", q)) }
            if (list.isNotEmpty() && text.isBlank()) c.addRow(arrayOf(list.size, "清除搜索历史", CLEAR))
            adapter.changeCursor(c)
        }

        sv.setOnQueryTextFocusChangeListener { _, has -> if (has) refresh(sv.query.toString()) }
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String): Boolean {
                if (q.isBlank()) return true
                LocalStore.addSearch(q)
                sv.clearFocus()
                onSearch(q.trim())
                return true
            }
            override fun onQueryTextChange(t: String): Boolean { refresh(t); return true }
        })
        sv.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int) = false
            override fun onSuggestionClick(position: Int): Boolean {
                val c = adapter.cursor ?: return true
                if (!c.moveToPosition(position)) return true
                val q = c.getString(2)
                if (q == CLEAR) { LocalStore.clearSearches(); refresh("") }
                else { sv.setQuery(q, true) }
                return true
            }
        })
    }
}
