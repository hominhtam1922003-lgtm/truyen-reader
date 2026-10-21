package com.example.truyenreader

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Luu local: truyen yeu thich + lich su doc (SQLite, khong can dang nhap). */
class DbRepo(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "truyen_reader.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE favorites(" +
                "url TEXT PRIMARY KEY, title TEXT, cover TEXT, " +
                "latest_chapter REAL DEFAULT 0, added_at INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE history(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, story_url TEXT, title TEXT, cover TEXT, " +
                "chapter_name TEXT, chapter_url TEXT, read_at INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS favorites")
        db.execSQL("DROP TABLE IF EXISTS history")
        onCreate(db)
    }

    // ---------- Yeu thich ----------

    fun isFavorite(url: String): Boolean {
        val c = readableDatabase.rawQuery("SELECT 1 FROM favorites WHERE url=?", arrayOf(url))
        val r = c.moveToFirst()
        c.close()
        return r
    }

    fun addFavorite(comic: Comic, latestChapter: Double) {
        val v = ContentValues().apply {
            put("url", comic.url)
            put("title", comic.title)
            put("cover", comic.cover)
            put("latest_chapter", latestChapter)
            put("added_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeFavorite(url: String) {
        writableDatabase.delete("favorites", "url=?", arrayOf(url))
    }

    fun favorites(): List<Pair<Comic, Double>> {
        val list = ArrayList<Pair<Comic, Double>>()
        val c = readableDatabase.rawQuery(
            "SELECT url,title,cover,latest_chapter FROM favorites ORDER BY added_at DESC", null
        )
        while (c.moveToNext()) {
            list.add(
                Comic(c.getString(1) ?: "", c.getString(0) ?: "", c.getString(2) ?: "") to
                    c.getDouble(3)
            )
        }
        c.close()
        return list
    }

    fun updateLatestChapter(url: String, number: Double) {
        val v = ContentValues().apply { put("latest_chapter", number) }
        writableDatabase.update("favorites", v, "url=?", arrayOf(url))
    }

    // ---------- Lich su doc ----------

    fun addHistory(item: HistoryItem) {
        // Chi giu 1 dong moi nhat cho moi cap truyen+chuong
        writableDatabase.delete(
            "history", "story_url=? AND chapter_url=?",
            arrayOf(item.storyUrl, item.chapterUrl)
        )
        val v = ContentValues().apply {
            put("story_url", item.storyUrl)
            put("title", item.title)
            put("cover", item.cover)
            put("chapter_name", item.chapterName)
            put("chapter_url", item.chapterUrl)
            put("read_at", item.readAt)
        }
        writableDatabase.insert("history", null, v)
    }

    fun history(limit: Int = 100): List<HistoryItem> {
        val list = ArrayList<HistoryItem>()
        val c = readableDatabase.rawQuery(
            "SELECT story_url,title,cover,chapter_name,chapter_url,read_at " +
                "FROM history ORDER BY read_at DESC LIMIT ?", arrayOf(limit.toString())
        )
        while (c.moveToNext()) {
            list.add(
                HistoryItem(
                    c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: "",
                    c.getString(3) ?: "", c.getString(4) ?: "", c.getLong(5)
                )
            )
        }
        c.close()
        return list
    }

    fun lastRead(storyUrl: String): HistoryItem? {
        val c = readableDatabase.rawQuery(
            "SELECT story_url,title,cover,chapter_name,chapter_url,read_at " +
                "FROM history WHERE story_url=? ORDER BY read_at DESC LIMIT 1",
            arrayOf(storyUrl)
        )
        var item: HistoryItem? = null
        if (c.moveToFirst()) {
            item = HistoryItem(
                c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: "",
                c.getString(3) ?: "", c.getString(4) ?: "", c.getLong(5)
            )
        }
        c.close()
        return item
    }
}
