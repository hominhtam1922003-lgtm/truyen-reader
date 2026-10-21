package com.example.truyenreader

data class Comic(
    val title: String,
    val url: String,          // URL trang chi tiet tren TruyenQQ
    val cover: String,
    val latestChapter: String = "",
    val updatedText: String = ""
)

data class ComicChapter(
    val name: String,         // "Chuong 12.5"
    val number: Double,       // 12.5
    val url: String,
    val date: String = ""
)

data class ComicDetail(
    val comic: Comic,
    val author: String,
    val status: String,
    val genres: String,
    val description: String,
    val chapters: List<ComicChapter>   // da sap xep tang dan theo so chuong
)

data class ChapterPages(
    val images: List<String>,
    val prevUrl: String?,
    val nextUrl: String?
)

data class HistoryItem(
    val storyUrl: String,
    val title: String,
    val cover: String,
    val chapterName: String,
    val chapterUrl: String,
    val readAt: Long
)

/** Giu danh sach chuong tam thoi khi chuyen tu Detail sang Reader (tranh Intent qua lon voi truyen >1000 chap). */
object ChapterListHolder {
    var storyUrl: String = ""
    var chapters: List<ComicChapter> = emptyList()
}
