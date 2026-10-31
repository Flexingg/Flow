package com.flow.todo.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.flow.todo.domain.model.AppUsageSnapshot
import com.flow.todo.domain.model.AppUsageStat
import com.flow.todo.domain.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

val InstalledAppInfo.label: String
    get() = appName

val AppUsageStat.appLabel: String
    get() = appName

class AppUsageManager(private val context: Context) {

    private val _snapshot = MutableStateFlow(AppUsageSnapshot())
    val snapshot: StateFlow<AppUsageSnapshot> = _snapshot.asStateFlow()

    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun tryAutoGrantViaShizuku(): Boolean {
        if (hasUsageAccessPermission()) return true
        val canUseShizuku = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        if (canUseShizuku) {
            runCatching {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                val process = method.invoke(
                    null,
                    arrayOf("sh", "-c", "appops set ${context.packageName} GET_USAGE_STATS allow"),
                    null,
                    null
                ) as java.lang.Process
                process.waitFor()
            }
        }
        return hasUsageAccessPermission()
    }

    private fun queryInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val installed = resolvedList
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .map {
                InstalledAppInfo(
                    packageName = it.packageName,
                    appName = it.loadLabel(pm).toString()
                )
            }

        val installedPackages = installed.map { it.packageName }.toSet()
        val presetsToAdd = PRESET_APPS.filter { it.packageName !in installedPackages }
        val combined = (installed + presetsToAdd)
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase(Locale.getDefault()) }

        return combined
    }

    suspend fun refreshUsageSnapshot(): AppUsageSnapshot = withContext(Dispatchers.IO) {
        var hasPerm = hasUsageAccessPermission()
        if (!hasPerm) {
            tryAutoGrantViaShizuku()
            hasPerm = hasUsageAccessPermission()
        }

        val installedApps = queryInstalledApps()
        val labelMap = installedApps.associate { it.packageName to it.appName }
        val nowMillis = System.currentTimeMillis()

        if (!hasPerm) {
            val snapshot = AppUsageSnapshot(
                hasUsagePermission = false,
                statsByPackage = emptyMap(),
                installedApps = installedApps,
                lastSyncedMillis = nowMillis
            )
            _snapshot.value = snapshot
            return@withContext snapshot
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            val snapshot = AppUsageSnapshot(
                hasUsagePermission = false,
                statsByPackage = emptyMap(),
                installedApps = installedApps,
                lastSyncedMillis = nowMillis
            )
            _snapshot.value = snapshot
            return@withContext snapshot
        }

        val startOfDayMillis = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val lastResumeTimestampByPkg = mutableMapOf<String, Long>()
        val accumulatedForegroundMsByPkg = mutableMapOf<String, Long>()
        val launchCountByPkg = mutableMapOf<String, Int>()
        var currentForegroundPkg: String? = null

        runCatching {
            val events = usageStatsManager.queryEvents(startOfDayMillis, nowMillis)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (currentForegroundPkg != pkg) {
                            launchCountByPkg[pkg] = (launchCountByPkg[pkg] ?: 0) + 1
                            currentForegroundPkg = pkg
                        }
                        lastResumeTimestampByPkg[pkg] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val resumeTs = lastResumeTimestampByPkg.remove(pkg)
                        if (resumeTs != null) {
                            val duration = (event.timeStamp - resumeTs).coerceAtLeast(0L)
                            accumulatedForegroundMsByPkg[pkg] = (accumulatedForegroundMsByPkg[pkg] ?: 0L) + duration
                        }
                    }
                }
            }

            for ((pkg, resumeTs) in lastResumeTimestampByPkg) {
                val duration = (nowMillis - resumeTs).coerceAtLeast(0L)
                accumulatedForegroundMsByPkg[pkg] = (accumulatedForegroundMsByPkg[pkg] ?: 0L) + duration
            }
        }

        val aggregatedStats = runCatching {
            usageStatsManager.queryAndAggregateUsageStats(startOfDayMillis, nowMillis)
        }.getOrDefault(emptyMap())

        val pm = context.packageManager
        val allPackages = (installedApps.map { it.packageName } + accumulatedForegroundMsByPkg.keys + aggregatedStats.keys).toSet()

        val statsByPackage = allPackages.associateWith { pkg ->
            val eventMinutes = (accumulatedForegroundMsByPkg[pkg] ?: 0L) / 60_000.0
            val aggregateMinutes = (aggregatedStats[pkg]?.totalTimeInForeground ?: 0L) / 60_000.0
            val activeMinutesToday = maxOf(eventMinutes, aggregateMinutes)
            val launchCountToday = launchCountByPkg[pkg] ?: 0

            val appName = labelMap[pkg] ?: runCatching {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
                pm.getApplicationLabel(appInfo).toString()
            }.getOrNull() ?: pkg

            AppUsageStat(
                packageName = pkg,
                appName = appName,
                activeMinutesToday = activeMinutesToday,
                launchCountToday = launchCountToday
            )
        }

        val snapshot = AppUsageSnapshot(
            hasUsagePermission = true,
            statsByPackage = statsByPackage,
            installedApps = installedApps,
            lastSyncedMillis = nowMillis
        )
        _snapshot.value = snapshot
        snapshot
    }

    companion object {
        val PRESET_APPS = listOf(
            InstalledAppInfo("com.instagram.android", "Instagram"),
            InstalledAppInfo("com.zhiliaoapp.musically", "TikTok"),
            InstalledAppInfo("com.google.android.youtube", "YouTube"),
            InstalledAppInfo("com.twitter.android", "X / Twitter"),
            InstalledAppInfo("com.reddit.frontpage", "Reddit"),
            InstalledAppInfo("com.android.chrome", "Chrome"),
            InstalledAppInfo("com.whatsapp", "WhatsApp"),
            InstalledAppInfo("com.discord", "Discord"),
            InstalledAppInfo("com.spotify.music", "Spotify"),
            InstalledAppInfo("com.duolingo", "Duolingo"),
            InstalledAppInfo("com.google.android.apps.books", "Play Books")
        )
    }
}
