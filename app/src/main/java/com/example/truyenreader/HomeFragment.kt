package com.example.truyenreader

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.concurrent.thread

class HomeFragment : Fragment() {

    private lateinit var adapter: ComicGridAdapter
    private val comics = ArrayList<Comic>()
    private var page = 1
    private var loading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvComics)
        rv.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = ComicGridAdapter(comics) { comic -> openDetail(comic) }
        rv.adapter = adapter

        val swipe = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        swipe.setOnRefreshListener {
            page = 1
            loadPage(reset = true) { swipe.isRefreshing = false }
        }

        view.findViewById<Button>(R.id.btnLoadMore).setOnClickListener {
            page++
            loadPage(reset = false)
        }

        val et = view.findViewById<EditText>(R.id.etSearch)
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = et.text.toString().trim()
                if (q.isNotEmpty()) doSearch(q)
                true
            } else false
        }

        loadPage(reset = true)
    }

    private fun openDetail(comic: Comic) {
        val i = Intent(requireContext(), DetailActivity::class.java)
        i.putExtra(DetailActivity.EXTRA_URL, comic.url)
        i.putExtra(DetailActivity.EXTRA_TITLE, comic.title)
        i.putExtra(DetailActivity.EXTRA_COVER, comic.cover)
        startActivity(i)
    }

    private fun setLoading(on: Boolean) {
        loading = on
        view?.findViewById<ProgressBar>(R.id.progress)?.visibility =
            if (on) View.VISIBLE else View.GONE
    }

    private fun loadPage(reset: Boolean, done: (() -> Unit)? = null) {
        if (loading) { done?.invoke(); return }
        setLoading(true)
        val p = page
        thread {
            var list: List<Comic> = emptyList()
            var error: Exception? = null
            try {
                list = TruyenQQSource.fetchLatest(p)
            } catch (e: Exception) {
                error = e
            }
            activity?.runOnUiThread {
                setLoading(false)
                done?.invoke()
                if (list.isNotEmpty()) {
                    if (reset) comics.clear()
                    // Tranh trung khi cac muc tren trang chu lap lai
                    val known = comics.map { it.url }.toSet()
                    comics.addAll(list.filter { it.url !in known })
                    adapter.submit(ArrayList(comics))
                } else if (error is TruyenQQSource.BlockedException) {
                    loadViaWebView(reset)
                } else if (reset && comics.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Không tải được danh sách. Kéo xuống để thử lại.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** Du phong Cloudflare: tai bang WebView roi parse HTML da render. */
    private fun loadViaWebView(reset: Boolean) {
        val url = if (page <= 1) TruyenQQSource.BASE + "/doc-truyen"
        else TruyenQQSource.BASE + "/truyen-moi-cap-nhat/trang-$page"
        WebFetcher.fetchHtml(requireContext(), url) { html ->
            if (html.isNullOrEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Nguồn đang chặn tải tự động (Cloudflare). Thử lại sau ít phút.",
                    Toast.LENGTH_LONG
                ).show()
                return@fetchHtml
            }
            val list = TruyenQQSource.parseList(TruyenQQSource.parseHtml(html, TruyenQQSource.BASE))
            if (reset) comics.clear()
            val known = comics.map { it.url }.toSet()
            comics.addAll(list.filter { it.url !in known })
            adapter.submit(ArrayList(comics))
        }
    }

    private fun doSearch(keyword: String) {
        setLoading(true)
        thread {
            val list = TruyenQQSource.search(keyword)
            activity?.runOnUiThread {
                setLoading(false)
                if (list.isEmpty()) {
                    Toast.makeText(requireContext(), "Không tìm thấy truyện phù hợp", Toast.LENGTH_SHORT).show()
                } else {
                    comics.clear()
                    comics.addAll(list)
                    adapter.submit(ArrayList(comics))
                    page = 1
                }
            }
        }
    }
}
