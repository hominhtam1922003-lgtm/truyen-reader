package com.example.truyenreader

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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
    private val latestBackup = ArrayList<Comic>()
    private var page = 1
    private var loading = false

    // Goi y tim kiem theo tung phim go
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var searchGen = 0
    private var searching = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvComics)
        rv.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = ComicGridAdapter(comics) { comic -> openDetail(comic) }
        rv.adapter = adapter

        val swipe = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        swipe.setOnRefreshListener {
            // Keo lam moi = thoat che do tim kiem, tai lai danh sach moi nhat
            exitSearchMode()
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
                if (q.isNotEmpty()) doSearch(q, manual = true)
                true
            } else false
        }
        // Go toi dau tim toi do: cho 450ms sau phim go cuoi cung roi moi tim
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                pendingSearch?.let { searchHandler.removeCallbacks(it) }
                if (q.isEmpty()) {
                    exitSearchMode()
                    restoreLatest()
                    return
                }
                val r = Runnable { doSearch(q, manual = false) }
                pendingSearch = r
                searchHandler.postDelayed(r, 450)
            }
        })

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
                    latestBackup.clear()
                    latestBackup.addAll(comics)
                } else {
                    // Bat ky loi/ket qua rong nao cung thu duong WebView truoc khi bao loi
                    loadViaWebView(reset)
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

    private fun doSearch(keyword: String, manual: Boolean) {
        val gen = ++searchGen
        searching = true
        view?.findViewById<Button>(R.id.btnLoadMore)?.visibility = View.GONE
        setLoading(true)
        thread {
            val list = TruyenQQSource.search(keyword)
            activity?.runOnUiThread {
                if (gen != searchGen) return@runOnUiThread // Da co truy van moi hon
                if (list.isNotEmpty()) {
                    setLoading(false)
                    showSearchResults(list)
                } else {
                    // Duong Jsoup rong/bi chan -> thu duong WebView (qua duoc Cloudflare)
                    searchViaWebView(keyword, gen, manual)
                }
            }
        }
    }

    /** Du phong cho tim kiem: tai trang ket qua bang WebView roi parse HTML da render. */
    private fun searchViaWebView(keyword: String, gen: Int, manual: Boolean) {
        if (!isAdded) return
        WebFetcher.fetchHtml(requireContext(), TruyenQQSource.searchUrl(keyword)) { html ->
            if (gen != searchGen) return@fetchHtml
            setLoading(false)
            val list = if (html.isNullOrEmpty()) emptyList()
            else {
                val doc = TruyenQQSource.parseHtml(html, TruyenQQSource.BASE)
                TruyenQQSource.parseList(doc).ifEmpty { TruyenQQSource.parseListLoose(doc) }
            }
            if (list.isNotEmpty()) {
                showSearchResults(list)
            } else if (manual) {
                Toast.makeText(requireContext(), "Không tìm thấy truyện phù hợp", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSearchResults(list: List<Comic>) {
        comics.clear()
        comics.addAll(list)
        adapter.submit(ArrayList(comics))
    }

    private fun exitSearchMode() {
        searchGen++ // Huy moi ket qua tim kiem dang bay ve
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        searching = false
        view?.findViewById<Button>(R.id.btnLoadMore)?.visibility = View.VISIBLE
        val et = view?.findViewById<EditText>(R.id.etSearch)
        if (!et?.text.isNullOrEmpty()) et?.text?.clear()
    }

    private fun restoreLatest() {
        if (latestBackup.isNotEmpty()) {
            comics.clear()
            comics.addAll(latestBackup)
            adapter.submit(ArrayList(comics))
        } else {
            page = 1
            loadPage(reset = true)
        }
    }
}
