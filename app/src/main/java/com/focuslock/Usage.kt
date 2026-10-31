package com.focuslock

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import java.util.Calendar

class AppUse(val pkg: String, val name: String, val ms: Long)

class InsightData(
    val today: Long,
    val series: List<Long>,      // last 30 days, oldest first, last = today
    val top: List<AppUse>,
    val baseline: Long,          // avg per day before Focus Lock
    val sinceAvg: Long,          // avg of complete days since start (max 7)
    val sinceDays: Int,
    val saved: Long,             // total time saved vs baseline
    val goalDays: Int            // days under goal in the last 7 complete days
)

object Usage {
    @Suppress("DEPRECATION")
    fun granted(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun dayStart(offset: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        return cal.timeInMillis
    }

    fun perApp(c: Context, from: Long, to: Long): Map<String, Long> {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return usm.queryAndAggregateUsageStats(from, to)
            .mapValues { it.value.totalTimeInForeground }.filter { it.value > 0 }
    }

    fun today(c: Context, pkg: String): Long =
        perApp(c, dayStart(0), System.currentTimeMillis())[pkg] ?: 0L

    fun label(c: Context, pkg: String): String {
        if (pkg == SHORTS) return "YouTube Shorts"
        return try {
            val pm = c.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
    }

    private fun homePkg(c: Context): String? =
        c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName

    private fun valid(c: Context, p: String, home: String?) =
        p != c.packageName && p != home && c.packageManager.getLaunchIntentForPackage(p) != null

    private fun totalFor(c: Context, from: Long, to: Long): Long {
        val home = homePkg(c)
        return perApp(c, from, to).filter { valid(c, it.key, home) }.values.sum()
    }

    fun todayTotal(c: Context): Long = totalFor(c, dayStart(0), System.currentTimeMillis())

    // Saves daily totals permanently and sets the "before Focus Lock" baseline on first run.
    fun sync(c: Context) {
        if (!granted(c)) return
        val now = System.currentTimeMillis()
        if (Store.startDay(c) < 0) {
            val vals = (7 downTo 1).map { d -> totalFor(c, dayStart(d), dayStart(d - 1)) }.filter { it > 0 }
            Store.setBaseline(c, if (vals.isEmpty()) 0L else vals.sum() / vals.size)
            Store.setStartDay(c, dayOf(now))
        }
        val h = Store.history(c).toMutableMap()
        for (d in 7 downTo 0) {
            val s = dayStart(d)
            val e = if (d == 0) now else dayStart(d - 1)
            h[dayOf(s)] = totalFor(c, s, e)
        }
        Store.saveHistory(c, h)
    }

    fun load(c: Context): InsightData {
        sync(c)
        val now = System.currentTimeMillis()
        val hist = Store.history(c)
        val todayE = dayOf(now)
        val start = Store.startDay(c)
        val baseline = Store.baseline(c)
        val home = homePkg(c)
        val todayMap = perApp(c, dayStart(0), now).filter { valid(c, it.key, home) }
        val series = (29 downTo 0).map { hist[todayE - it] ?: 0L }
        val complete = (1..30).map { todayE - it }.filter { it >= start && hist.containsKey(it) }
        val last7 = complete.take(7)
        val sinceAvg = if (last7.isEmpty()) 0L else last7.sumOf { hist[it] ?: 0L } / last7.size
        val saved = complete.sumOf { (baseline - (hist[it] ?: 0L)).coerceAtLeast(0L) }
        val goalMs = Store.goalMin(c) * 60_000L
        val goalDays = (1..7).count { d -> val v = hist[todayE - d]; v != null && v > 0 && v <= goalMs }
        val top = todayMap.entries.sortedByDescending { it.value }.take(6).map { AppUse(it.key, label(c, it.key), it.value) }
        return InsightData(todayMap.values.sum(), series, top, baseline, sinceAvg, last7.size, saved, goalDays)
    }
}
