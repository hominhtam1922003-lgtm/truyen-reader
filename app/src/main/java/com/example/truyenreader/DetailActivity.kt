package com.example.truyenreader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlin.concurrent.thread

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "story_url"
        const val EXTRA_TITLE = "story_title"
        const val EXTRA_COVER = "story_cover"
    }

    private lateinit var db: DbRepo
    private var detail: ComicDetail? = null
    private lateinit var storyUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        db = DbRepo(this)

        storyUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val preTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val preCover = intent.getStringExtra(EXTRA_COVER) ?: ""

        title = preTitle
        findViewById<TextView>(R.id.tvDetailTitle).text = preTitle
        if (preCover.isNotEmpty()) {
            Glide.with(this).load(preCover).centerCrop()
                .into(findViewById<ImageView>(R.id.ivDetailCover))
        }
        updateFavoriteButton()

        findViewById<Button>(R.id.btnFavorite).setOnClickListener { toggleFavorite() }
        findViewById<Button>(R.id.btnReadFirst).setOnClickListener {
            val first = detail?.chapters?.firstOrNull()
            if (first != null) openReader(first) else toastLoading()
        }
        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            val d = detail
            if (d == null || d.chapters.isEmpty()) {
                toastLoading()
                return@setOnClickListener
            }
            val last = db.lastRead(storyUrl)
            val target = if (last != null) {
                d.chapters.firstOrNull { it.url == last.chapterUrl }
            } else null
            openReader(target ?: d.chapters.first())
        }

        loadDetail()
    }

    private fun toastLoading() =
        Toast.makeText(this, "Đang tải danh sách chương...", Toast.LENGTH_SHORT).show()

    private fun loadDetail() {
        thread {
            var d: ComicDetail? = null
            var blocked = false
            try {
                d = TruyenQQSource.fetchDetail(storyUrl)
            } catch (e: TruyenQQSource.BlockedException) {
                blocked = true
            } catch (e: Exception) {
                blocked = false
            }
            runOnUiThread {
                if (d != null && d.chapters.isNotEmpty()) {
                    showDetail(d)
                } else if (blocked) {
                    // Du phong Cloudflare bang WebView
                    WebFetcher.fetchHtml(this, storyUrl) { html ->
                        val parsed = if (html.isNullOrEmpty()) null else
                            TruyenQQSource.parseDetail(
                                TruyenQQSource.parseHtml(html, TruyenQQSource.BASE), storyUrl
                            )
                        if (parsed != null && parsed.chapters.isNotEmpty()) {
                            showDetail(parsed)
                        } else {
                            loadFailed()
                        }
                    }
                } else {
                    loadFailed()
                }
            }
        }
    }

    private fun loadFailed() {
        findViewById<ProgressBar>(R.id.detailProgress).visibility = View.GONE
        Toast.makeText(
            this,
            "Không tải được truyện (nguồn có thể đang chặn hoặc mất mạng). Quay lại thử sau.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showDetail(d: ComicDetail) {
        detail = d
        findViewById<ProgressBar>(R.id.detailProgress).visibility = View.GONE
        title = d.comic.title
        findViewById<TextView>(R.id.tvDetailTitle).text = d.comic.title
        val meta = listOf(
            if (d.author.isNotEmpty()) "Tác giả: ${d.author}" else "",
            if (d.status.isNotEmpty()) "Tình trạng: ${d.status}" else ""
        ).filter { it.isNotEmpty() }.joinToString("\n")
        findViewById<TextView>(R.id.tvDetailMeta).text = meta
        findViewById<TextView>(R.id.tvDetailGenres).text =
            if (d.genres.isNotEmpty()) "Thể loại: ${d.genres}" else ""
        findViewById<TextView>(R.id.tvDetailDesc).text = d.description
        if (d.comic.cover.isNotEmpty()) {
            Glide.with(this).load(d.comic.cover).centerCrop()
                .into(findViewById<ImageView>(R.id.ivDetailCover))
        }
        findViewById<TextView>(R.id.tvChapterCount).text =
            "Danh sách chương (${d.chapters.size} chap — đã tải toàn bộ)"

        val rv = findViewById<RecyclerView>(R.id.rvChapters)
        rv.layoutManager = LinearLayoutManager(this)
        // Hien thi moi nhat truoc cho de bam, du lieu van giu thu tu tang dan
        rv.adapter = ChapterAdapter(d.chapters.reversed()) { ch -> openReader(ch) }

        // Neu da yeu thich: cap nhat moc chap moi nhat da biet (khong bao lai chap cu)
        if (db.isFavorite(storyUrl)) {
            val latest = d.chapters.maxOfOrNull { it.number } ?: 0.0
            db.updateLatestChapter(storyUrl, latest)
        }
        updateFavoriteButton()
    }

    private fun updateFavoriteButton() {
        val btn = findViewById<Button>(R.id.btnFavorite)
        btn.text = if (db.isFavorite(storyUrl)) "★ Đã yêu thích" else "☆ Yêu thích"
    }

    private fun toggleFavorite() {
        if (db.isFavorite(storyUrl)) {
            db.removeFavorite(storyUrl)
            Toast.makeText(this, "Đã bỏ yêu thích", Toast.LENGTH_SHORT).show()
        } else {
            val d = detail
            val comic = d?.comic ?: Comic(
                intent.getStringExtra(EXTRA_TITLE) ?: "",
                storyUrl,
                intent.getStringExtra(EXTRA_COVER) ?: ""
            )
            val latest = d?.chapters?.maxOfOrNull { it.number } ?: 0.0
            db.addFavorite(comic, latest)
            Toast.makeText(
                this,
                "Đã lưu yêu thích — app sẽ thông báo khi có chap mới",
                Toast.LENGTH_SHORT
            ).show()
        }
        updateFavoriteButton()
    }

    private fun openReader(chapter: ComicChapter) {
        val d = detail ?: return
        ChapterListHolder.storyUrl = storyUrl
        ChapterListHolder.chapters = d.chapters
        val i = Intent(this, ReaderActivity::class.java)
        i.putExtra(ReaderActivity.EXTRA_STORY_URL, storyUrl)
        i.putExtra(ReaderActivity.EXTRA_STORY_TITLE, d.comic.title)
        i.putExtra(ReaderActivity.EXTRA_COVER, d.comic.cover)
        i.putExtra(ReaderActivity.EXTRA_CHAPTER_URL, chapter.url)
        i.putExtra(ReaderActivity.EXTRA_CHAPTER_NAME, chapter.name)
        startActivity(i)
    }
}
