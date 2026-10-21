package com.example.truyenreader

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import kotlin.concurrent.thread

/**
 * Tab duyet truyen theo loai:
 * - NEW: truyen moi cap nhat (/truyen-moi-cap-nhat/trang-N, co phan trang)
 * - HOT: truyen hot/noi bat (khong phan trang)
 * Moi lan mo tab deu tai lai tu web nen truyen/chap moi ra la thay ngay.
 */
class BrowseFragment : Fragment() {

    companion object {
        const val KIND_NEW = "new"
        const val KIND_HOT = "hot"
        fun newInstance(kind: String): BrowseFragment {
            val f = BrowseFragment()
            f.arguments = Bundle().apply { putString("kind", kind) }
            return f
        }
    }

    private lateinit var adapter: ComicGridAdapter
    private val comics = ArrayList<Comic>()
    private var page = 1
    private var loading = false
    private val kind: String get() = arguments?.getString("kind") ?: KIND_NEW

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // An o tim kiem (chi tab Trang chu moi co)
        view.findViewById<View>(R.id.etSearch)?.let { (it.parent as? View)?.visibility = View.GONE }

        adapter = ComicGridAdapter(comics) { comic ->
            val i = Intent(requireContext(), DetailActivity::class.java)
            i.putExtra(DetailActivity.EXTRA_URL, comic.url)
            i.putExtra(DetailActivity.EXTRA_TITLE, comic.title)
            i.putExtra(DetailActivity.EXTRA_COVER, comic.cover)
            startActivity(i)
        }
        val rv = view.findViewById<RecyclerView>(R.id.rvComics)
        rv.layoutManager = GridLayoutManager(requireContext(), 3)
        rv.adapter = adapter

        view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh).setOnRefreshListener {
            loadPage(reset = true)
        }
        val btnMore = view.findViewById<MaterialButton>(R.id.btnLoadMore)
        if (kind == KIND_HOT) {
            btnMore.visibility = View.GONE
        } else {
            btnMore.text = "Tải thêm truyện mới cập nhật"
            btnMore.setOnClickListener { loadPage(reset = false) }
        }
        loadPage(reset = true)
    }

    private fun urlFor(p: Int): String =
        if (kind == KIND_HOT) TruyenQQSource.BASE + "/truyen-hot"
        else TruyenQQSource.BASE + "/truyen-moi-cap-nhat/trang-$p"

    private fun loadPage(reset: Boolean) {
        if (loading) return
        loading = true
        if (reset) page = 1
        val targetPage = page
        setRefreshing(true)
        thread {
            RemoteConfig.awaitReady(3500)
            var list: List<Comic> = emptyList()
            var error: Exception? = null
            try {
                list = if (kind == KIND_HOT) TruyenQQSource.fetchHot()
                else TruyenQQSource.fetchNewPage(targetPage)
            } catch (e: Exception) {
                error = e
            }
            activity?.runOnUiThread {
                loading = false
                setRefreshing(false)
                if (list.isNotEmpty()) {
                    if (reset) comics.clear()
                    val known = comics.map { it.url }.toSet()
                    comics.addAll(list.filter { it.url !in known })
                    adapter.submit(ArrayList(comics))
                    if (!reset) page++
                    else page = 2
                } else if (error != null) {
                    loadViaWebView(reset, targetPage)
                } else if (reset && comics.isEmpty()) {
                    Toast.makeText(
                        requireContext(), "Chưa tải được danh sách, kéo xuống thử lại.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loadViaWebView(reset: Boolean, targetPage: Int) {
        WebFetcher.fetchHtml(requireContext(), urlFor(targetPage)) { html ->
            if (html.isNullOrEmpty()) {
                if (reset && comics.isEmpty()) {
                    Toast.makeText(
                        requireContext(), "Không tải được. Kiểm tra mạng rồi thử lại.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@fetchHtml
            }
            val list = TruyenQQSource.parseList(
                TruyenQQSource.parseHtml(html, TruyenQQSource.BASE)
            )
            if (list.isNotEmpty()) {
                if (reset) comics.clear()
                val known = comics.map { it.url }.toSet()
                comics.addAll(list.filter { it.url !in known })
                adapter.submit(ArrayList(comics))
                page = targetPage + 1
            }
        }
    }

    private fun setRefreshing(on: Boolean) {
        view?.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = on
        view?.findViewById<View>(R.id.progress)?.visibility =
            if (on) View.VISIBLE else View.GONE
    }
}
