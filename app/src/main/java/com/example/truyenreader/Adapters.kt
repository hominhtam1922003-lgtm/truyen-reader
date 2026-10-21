package com.example.truyenreader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ComicGridAdapter(
    private var items: List<Comic>,
    private val onClick: (Comic) -> Unit
) : RecyclerView.Adapter<ComicGridAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val iv: ImageView = v.findViewById(R.id.ivCover)
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvLatest: TextView = v.findViewById(R.id.tvLatest)
    }

    fun submit(newItems: List<Comic>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comic_grid, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        h.tvTitle.text = c.title
        val sub = listOf(c.latestChapter, c.updatedText).filter { it.isNotEmpty() }.joinToString(" • ")
        h.tvLatest.text = sub
        ImageLoader.cover(h.iv, c.cover)
        h.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount() = items.size
}

class ComicRowAdapter(
    private var items: List<Pair<Comic, String>>,
    private val onClick: (Comic) -> Unit
) : RecyclerView.Adapter<ComicRowAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val iv: ImageView = v.findViewById(R.id.ivCoverRow)
        val tvTitle: TextView = v.findViewById(R.id.tvTitleRow)
        val tvSub: TextView = v.findViewById(R.id.tvSubRow)
    }

    fun submit(newItems: List<Pair<Comic, String>>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comic_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val (c, sub) = items[pos]
        h.tvTitle.text = c.title
        h.tvSub.text = sub
        ImageLoader.cover(h.iv, c.cover)
        h.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount() = items.size
}

class ChapterAdapter(
    private val items: List<ComicChapter>,
    private val onClick: (ComicChapter) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvChapter)
        val tvDate: TextView = v.findViewById(R.id.tvChapterDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chapter, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val ch = items[pos]
        h.tvName.text = ch.name
        h.tvDate.text = ch.date
        h.itemView.setOnClickListener { onClick(ch) }
    }

    override fun getItemCount() = items.size
}

class PageAdapter(
    private val images: List<String>
) : RecyclerView.Adapter<PageAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val iv: ImageView = v.findViewById(R.id.ivPage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_page, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        ImageLoader.page(h.iv, images[pos])
    }

    override fun getItemCount() = images.size
}
