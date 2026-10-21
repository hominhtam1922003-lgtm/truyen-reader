package com.example.truyenreader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Dinh ky kiem tra cac truyen YEU THICH tren TruyenQQ:
 * neu so chuong moi nhat lon hon moc da luu -> gui thong bao chap moi
 * va cap nhat moc. Chay nen bang WorkManager (xem MainActivity).
 */
class NewChapterWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        RemoteConfig.resolveBlocking(applicationContext)
        val db = DbRepo(applicationContext)
        val favorites = db.favorites()
        for ((comic, knownLatest) in favorites) {
            try {
                val detail = TruyenQQSource.fetchDetail(comic.url)
                val latest = detail.chapters.maxOfOrNull { it.number } ?: continue
                if (knownLatest > 0 && latest > knownLatest) {
                    notifyNewChapter(comic, latest)
                }
                if (latest != knownLatest) {
                    db.updateLatestChapter(comic.url, latest)
                }
            } catch (e: Exception) {
                // Truyen loi / bi chan tam thoi: bo qua, lan sau kiem tra lai
                continue
            }
        }
        return Result.success()
    }

    private fun notifyNewChapter(comic: Comic, latest: Double) {
        val ctx = applicationContext
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Chap mới", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Thông báo khi truyện yêu thích có chap mới" }
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(ctx, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_URL, comic.url)
            putExtra(DetailActivity.EXTRA_TITLE, comic.title)
            putExtra(DetailActivity.EXTRA_COVER, comic.cover)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx, comic.url.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Chap mới: ${comic.title}")
            .setContentText("Đã có chương ${formatNum(latest)}. Chạm để đọc ngay.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(comic.url.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "chap_moi"
    }
}
