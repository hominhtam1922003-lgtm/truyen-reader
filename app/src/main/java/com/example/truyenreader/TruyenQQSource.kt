package com.example.truyenreader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Nguon du lieu: truyenqqko.com (TruyenQQ).
 *
 * KET QUA KHAO SAT TRUC TIEP (21/10/2026):
 * - Trang chi tiet: /truyen-tranh/{slug}-{storyId}  (so cuoi slug la ID truyen)
 * - Trang chuong:    {URL chi tiet}-chap-{so}      (so co the la thap phan: 12.5)
 * - Danh sach moi:   /truyen-moi-cap-nhat/trang-{n}
 * - TOAN BO danh sach chuong da nam san trong HTML trang chi tiet ngay khi tai
 *   (kiem chung One Piece: 1.241 link chuong). Nut "Xem Them" tren web chi mo
 *   rong phan MO TA, khong tai them chuong. Vi vay app chi can tai trang chi
 *   tiet 1 lan la co FULL CHAP - khong can bam "xem them", khong co AJAX.
 * - Anh chuong o CDN i*.truyenvua.com/{storyId}/{chap}/{index}.png
 * - Web co Cloudflare chong bot: neu Jsoup bi chan (trang "Just a moment"),
 *   app se thu lai bang WebView (xem WebFetcher) o cac man hinh chinh.
 */
object TruyenQQSource {

    const val BASE = "https://truyenqqko.com"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    class BlockedException : Exception("Cloudflare blocked")

    private val detailPathRegex = Regex("""/truyen-tranh/[a-z0-9\-]+-\d+$""")
    private val chapRegex = Regex("""-chap-([\d]+(?:\.[\d]+)?)$""")
    private val timeRegex = Regex("""(\d+\s*(Phút|Giờ|Ngày|Tuần|Tháng|Năm)\s*Trước)""", RegexOption.IGNORE_CASE)

    fun storyIdOf(detailUrl: String): String {
        val m = Regex("""-(\d+)$""").find(detailUrl.trimEnd('/'))
        return m?.groupValues?.get(1) ?: ""
    }

    fun fetchDoc(url: String): Document {
        val doc = Jsoup.connect(url)
            .userAgent(UA)
            .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
            .header("Referer", "$BASE/")
            .timeout(30000)
            .followRedirects(true)
            .get()
        val text = doc.text()
        if (doc.title().contains("Just a moment", true) ||
            text.contains("xác minh bạn là con người", true) ||
            text.contains("Thực hiện xác minh bảo mật", true)
        ) {
            throw BlockedException()
        }
        return doc
    }

    fun parseHtml(html: String, baseUrl: String): Document = Jsoup.parse(html, baseUrl)

    // ---------------- Danh sach truyen ----------------

    fun fetchLatest(page: Int): List<Comic> {
        val url = if (page <= 1) "$BASE/doc-truyen" else "$BASE/truyen-moi-cap-nhat/trang-$page"
        return parseList(fetchDoc(url))
    }

    /** Lay URL anh that tu the img: uu tien cac thuoc tinh lazy-load, cuoi cung moi la src. */
    private fun imgUrl(img: org.jsoup.nodes.Element): String {
        for (attr in listOf("data-src", "data-original", "data-lazy-src", "data-url", "src")) {
            val v = img.absUrl(attr)
            if (v.isNotEmpty() && !v.contains("placeholder", true) && !v.endsWith("loading.gif")) {
                return v
            }
        }
        return img.absUrl("src")
    }

    fun search(keyword: String): List<Comic> {
        val q = java.net.URLEncoder.encode(keyword, "UTF-8")
        // 1) Do form tim kiem that tren trang chu cua web -> dung dung action + ten tham so
        try {
            val home = fetchDoc("$BASE/")
            for (form in home.select("form")) {
                val action = form.absUrl("action")
                val lower = action.lowercase()
                if (!lower.contains("tim") && !lower.contains("search")) continue
                val input = form.select("input[name]").firstOrNull {
                    val t = it.attr("type")
                    t.isEmpty() || t == "text" || t == "search"
                } ?: continue
                val param = input.attr("name").ifEmpty { "q" }
                val sep = if (action.contains("?")) "&" else "?"
                val list = parseList(fetchDoc("$action$sep$param=$q"))
                if (list.isNotEmpty()) return list
            }
        } catch (e: Exception) {
            // Bo qua, thu cac mau URL du phong ben duoi
        }
        // 2) Cac mau URL tim kiem pho bien cua ho web nay
        for (pattern in listOf(
            "$BASE/tim-kiem?q=$q",
            "$BASE/tim-kiem.html?q=$q",
            "$BASE/tim-truyen?q=$q",
            "$BASE/search?q=$q",
            "$BASE/tim-kiem?keyword=$q"
        )) {
            try {
                val list = parseList(fetchDoc(pattern))
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                // Thu mau tiep theo
            }
        }
        return emptyList()
    }

    fun parseList(doc: Document): List<Comic> {
        val result = LinkedHashMap<String, Comic>()
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href").substringBefore("?").trimEnd('/')
            if (!detailPathRegex.containsMatchIn(href) || chapRegex.containsMatchIn(href)) continue
            val img = a.selectFirst("img") ?: continue
            val title = img.attr("alt").ifBlank { a.attr("title") }.ifBlank { a.text() }.trim()
            if (title.isEmpty()) continue
            val cover = imgUrl(img)
            // Tim chuong moi nhat + thoi gian trong khoi chua item nay
            var latest = ""
            var updated = ""
            var node: org.jsoup.nodes.Element? = a
            var depth = 0
            while (node != null && depth < 5 && (latest.isEmpty() || updated.isEmpty())) {
                if (latest.isEmpty()) {
                    val chapLink = node.select("a[href]").firstOrNull {
                        chapRegex.containsMatchIn(it.absUrl("href"))
                    }
                    if (chapLink != null) latest = chapLink.text().trim()
                }
                if (updated.isEmpty()) {
                    val m = timeRegex.find(node.text())
                    if (m != null) updated = m.value
                }
                node = node.parent()
                depth++
            }
            val old = result[href]
            if (old == null) {
                result[href] = Comic(title, href, cover, latest, updated)
            } else if (old.latestChapter.isEmpty() && latest.isNotEmpty()) {
                result[href] = old.copy(latestChapter = latest, updatedText = updated)
            }
        }
        return result.values.toList()
    }

    // ---------------- Chi tiet truyen + FULL CHAP ----------------

    fun fetchDetail(detailUrl: String): ComicDetail = parseDetail(fetchDoc(detailUrl), detailUrl)

    fun parseDetail(doc: Document, detailUrl: String): ComicDetail {
        val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        var cover = ""
        val coverImg = doc.select("img").firstOrNull { it.attr("alt").trim() == title && title.isNotEmpty() }
            ?: doc.select("img").firstOrNull { it.absUrl("src").contains("ebook") }
        if (coverImg != null) {
            cover = imgUrl(coverImg)
        }
        val genres = doc.select("a[href*=/the-loai/]").map { it.text().trim() }
            .filter { it.isNotEmpty() }.distinct().joinToString(", ")

        var author = ""
        var status = ""
        var totalChapters = 0
        for (row in doc.select("tr, li")) {
            val t = row.text()
            if (author.isEmpty() && t.contains("Tác giả", true)) {
                author = row.selectFirst("a")?.text()?.trim()
                    ?: t.substringAfter(":").trim()
            }
            if (status.isEmpty() && t.contains("Tình trạng", true)) {
                status = t.substringAfter(":").trim()
            }
            if (totalChapters == 0 && t.contains("Tổng số chap", true)) {
                totalChapters = Regex("""[\d.,]+""").find(t.substringAfter(":", ""))
                    ?.value?.replace(".", "")?.replace(",", "")?.toIntOrNull() ?: 0
            }
        }

        var description = ""
        val introHead = doc.select("h2").firstOrNull { it.text().contains("Giới Thiệu", true) }
        if (introHead != null) {
            var sib = introHead.nextElementSibling()
            val sb = StringBuilder()
            while (sib != null && !sib.tagName().equals("h2", true) && sb.length < 2000) {
                sb.append(sib.text()).append("\n")
                sib = sib.nextElementSibling()
            }
            description = sb.toString().trim()
        }
        if (description.isEmpty()) {
            description = doc.selectFirst("meta[name=description]")?.attr("content") ?: ""
        }

        // FULL CHAP: quet toan bo link chuong da render san trong trang
        val byUrl = LinkedHashMap<String, ComicChapter>()
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href").substringBefore("?").trimEnd('/')
            val m = chapRegex.find(href) ?: continue
            if (!href.startsWith(detailUrl.trimEnd('/'))) continue
            val num = m.groupValues[1].toDoubleOrNull() ?: continue
            val name = a.text().trim().ifEmpty { "Chương ${m.groupValues[1]}" }
            val parentText = a.parent()?.text() ?: ""
            val date = Regex("""\d{2}/\d{2}/\d{4}""").find(parentText)?.value ?: ""
            byUrl[href] = ComicChapter(name, num, href, date)
        }
        val chapters = byUrl.values.sortedBy { it.number }

        return ComicDetail(
            comic = Comic(title.ifEmpty { detailUrl }, detailUrl, cover),
            author = author,
            status = status,
            genres = genres,
            description = description,
            chapters = chapters,
            totalChapters = totalChapters
        )
    }

    // ---------------- Trang doc chuong (anh) ----------------

    fun fetchChapter(chapterUrl: String): ChapterPages = parseChapter(fetchDoc(chapterUrl))

    fun parseChapter(doc: Document): ChapterPages {
        val images = ArrayList<String>()
        val pagePattern = Regex("""/\d+/[\d.]+/\d+\.(png|jpg|jpeg|webp)""", RegexOption.IGNORE_CASE)
        for (img in doc.select("img")) {
            val src = imgUrl(img)
            if (src.isEmpty()) continue
            if (src.contains("truyenvua.com") || pagePattern.containsMatchIn(src)) {
                if (!images.contains(src)) images.add(src)
            }
        }
        var prev: String? = null
        var next: String? = null
        for (a in doc.select("a[href]")) {
            val t = a.text()
            if (prev == null && t.contains("Chap trước", true)) prev = a.absUrl("href")
            if (next == null && t.contains("Chap sau", true)) next = a.absUrl("href")
        }
        return ChapterPages(images, prev, next)
    }
}
