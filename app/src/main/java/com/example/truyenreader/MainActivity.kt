package com.example.truyenreader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (savedInstanceState == null) show(HomeFragment())
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> show(HomeFragment())
                R.id.nav_favorites -> show(FavoritesFragment())
                R.id.nav_history -> show(HistoryFragment())
            }
            true
        }

        requestNotificationPermission()
        scheduleNewChapterChecks()
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    /** Kiem tra chap moi cho truyen yeu thich moi 3 gio (chi khi co mang). */
    private fun scheduleNewChapterChecks() {
        val request = PeriodicWorkRequestBuilder<NewChapterWorker>(3, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "new_chapter_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
