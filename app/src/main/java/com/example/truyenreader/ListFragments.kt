package com.example.truyenreader

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FavoritesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_simple, container, false)

    override fun onResume() {
        super.onResume()
        val view = view ?: return
        val db = DbRepo(requireContext())
        val favs = db.favorites()
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val rv = view.findViewById<RecyclerView>(R.id.rvList)
        rv.layoutManager = LinearLayoutManager(requireContext())
        if (favs.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "Chưa có truyện yêu thích.\nMở một truyện và bấm ☆ Yêu thích để lưu vào đây."
        } else {
            tvEmpty.visibility = View.GONE
        }
        rv.adapter = ComicRowAdapter(
            favs.map { (comic, latest) ->
                comic to if (latest > 0) "Chap mới nhất đã biết: ${formatNum(latest)}" else ""
            }
        ) { comic -> openDetail(comic) }
    }

    private fun openDetail(comic: Comic) {
        val i = Intent(requireContext(), DetailActivity::class.java)
        i.putExtra(DetailActivity.EXTRA_URL, comic.url)
        i.putExtra(DetailActivity.EXTRA_TITLE, comic.title)
        i.putExtra(DetailActivity.EXTRA_COVER, comic.cover)
        startActivity(i)
    }
}

class HistoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_simple, container, false)

    override fun onResume() {
        super.onResume()
        val view = view ?: return
        val db = DbRepo(requireContext())
        val items = db.history()
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val rv = view.findViewById<RecyclerView>(R.id.rvList)
        rv.layoutManager = LinearLayoutManager(requireContext())
        if (items.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "Chưa có lịch sử đọc.\nCác chương bạn đã đọc sẽ hiện ở đây."
        } else {
            tvEmpty.visibility = View.GONE
        }
        val fmt = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
        rv.adapter = ComicRowAdapter(
            items.map { h ->
                val progress = if (h.chaptersRead > 1) " • Đã đọc ${h.chaptersRead} chương" else ""
                Comic(h.title, h.storyUrl, h.cover) to
                    "Đang đọc: ${h.chapterName}$progress • ${fmt.format(Date(h.readAt))}"
            }
        ) { comic ->
            val i = Intent(requireContext(), DetailActivity::class.java)
            i.putExtra(DetailActivity.EXTRA_URL, comic.url)
            i.putExtra(DetailActivity.EXTRA_TITLE, comic.title)
            i.putExtra(DetailActivity.EXTRA_COVER, comic.cover)
            startActivity(i)
        }
    }
}

fun formatNum(n: Double): String =
    if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
