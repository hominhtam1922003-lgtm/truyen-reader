package com.example.truyenreader

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORY_URL = "story_url"
        const val EXTRA_STORY_TITLE = "story_title"
        const val EXTRA_COVER = "story_cover"
        const val EXTRA_CHAPTER_URL = "chapter_url"
        const val EXTRA_CHAPTER_NAME = "chapter_name"
    }

    private lateinit var db: DbRepo
    private lateinit var storyUrl: String
    private lateinit var storyTitle: String
    private lateinit var cover: String
    private var chapterUrl: String = ""
    private var chapterName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        db = DbRepo(this)

        storyUrl = intent.getStringExtra(EXTRA_STORY_URL) ?: run { finish(); return }
        storyTitle = intent.getStringExtra(EXTRA_STORY_TITLE) ?: ""
        cover = intent.getStringExtra(EXTRA_COVER) ?: ""
        chapterUrl = intent.getStringExtra(EXTRA_CHAPTER_URL) ?: run { finish(); return }
        chapterName = intent.getStringExtra(EXTRA_CHAPTER_NAME) ?: ""

        findViewById<Button>(R.id.btnPrev).setOnClickListener { moveChapter(-1) }
        findViewById<Button>(R.id.btnNext).setOnClickListener { moveChapter(1) }

        loadChapter()
    }

    private fun currentIndex(): Int =
        ChapterListHolder.chapters.indexOfFirst { it.url == chapterUrl }

    private fun moveChapter(delta: Int) {
        val list = ChapterListHolder.chapters
        val idx = currentIndex()
        if (ChapterListHolder.storyUrl != storyUrl || idx < 0) {
            Toast.makeText(this, "Hãy chọn chương từ trang chi tiết truyện", Toast.LENGTH_SHORT).show()
            return
        }
        val target = idx + delta
        if (target !in list.indices) {
            Toast.makeText(
                this,
                if (delta < 0) "Đây là chương đầu tiên" else "Đây là chương mới nhất",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val ch = list[target]
        chapterUrl = ch.url
        chapterName = ch.name
        loadChapter()
    }

    private fun loadChapter() {
        title = "$storyTitle — $chapterName"
        findViewById<TextView>(R.id.tvReaderTitle).text = chapterName
        val progress = findViewById<ProgressBar>(R.id.readerProgress)
        progress.visibility = View.VISIBLE

        // Luu lich su doc
        db.addHistory(
            HistoryItem(storyUrl, storyTitle, cover, chapterName, chapterUrl, System.currentTimeMillis())
        )

        val url = chapterUrl
        thread {
            var pages: ChapterPages? = null
            var blocked = false
            try {
                pages = TruyenQQSource.fetchChapter(url)
            } catch (e: TruyenQQSource.BlockedException) {
                blocked = true
            } catch (e: Exception) {
                pages = null
            }
            runOnUiThread {
                if (pages != null && pages.images.isNotEmpty()) {
                    showPages(pages)
                } else if (blocked) {
                    WebFetcher.fetchHtml(this, url) { html ->
                        val parsed = if (html.isNullOrEmpty()) null else
                            TruyenQQSource.parseChapter(
                                TruyenQQSource.parseHtml(html, TruyenQQSource.BASE)
                            )
                        if (parsed != null && parsed.images.isNotEmpty()) showPages(parsed)
                        else loadFailed()
                    }
                } else {
                    loadFailed()
                }
            }
        }
    }

    private fun showPages(pages: ChapterPages) {
        findViewById<ProgressBar>(R.id.readerProgress).visibility = View.GONE
        val rv = findViewById<RecyclerView>(R.id.rvPages)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = PageAdapter(pages.images)
        rv.scrollToPosition(0)
    }

    private fun loadFailed() {
        findViewById<ProgressBar>(R.id.readerProgress).visibility = View.GONE
        Toast.makeText(
            this,
            "Không tải được ảnh chương. Kiểm tra mạng rồi mở lại chương.",
            Toast.LENGTH_LONG
        ).show()
    }
}
