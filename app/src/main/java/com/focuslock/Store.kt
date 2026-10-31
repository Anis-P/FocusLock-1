package com.focuslock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

const val SHORTS = "yt.shorts"
const val YT = "com.google.android.youtube"

class Preset(val name: String, val pkgs: List<String>)

val PRESETS = listOf(
    Preset("Instagram", listOf("com.instagram.android", "com.instagram.lite")),
    Preset("YouTube Shorts", listOf(SHORTS)),
    Preset("Snapchat", listOf("com.snapchat.android")),
    Preset("Facebook", listOf("com.facebook.katana", "com.facebook.lite")),
    Preset("TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
    Preset("X", listOf("com.twitter.android")),
    Preset("Reddit", listOf("com.reddit.frontpage"))
)
val DEFAULT_APPS: Set<String> = PRESETS.take(5).flatMap { it.pkgs }.toSet()

data class Schedule(
    val id: Long, val name: String, val days: Int, val start: Int, val end: Int,
    val apps: Set<String>, val adult: Boolean, val on: Boolean
)

data class Active(val apps: Set<String>, val adult: Boolean, val until: Long, val label: String)

fun dayOf(t: Long): Long = (t + TimeZone.getDefault().getOffset(t)) / 86_400_000L

fun Schedule.activeNow(): Boolean {
    val cal = Calendar.getInstance()
    val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val today = ((days shr dow) and 1) == 1
    val yesterday = ((days shr ((dow + 6) % 7)) and 1) == 1
    return when {
        start < end -> today && mins >= start && mins < end
        start > end -> (today && mins >= start) || (yesterday && mins < end)
        else -> false
    }
}

fun Schedule.untilMillis(): Long {
    val cal = Calendar.getInstance()
    val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.HOUR_OF_DAY, end / 60); cal.set(Calendar.MINUTE, end % 60)
    if (end <= mins) cal.add(Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis
}

object Store {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("fl", Context.MODE_PRIVATE)
    private fun ss(c: Context, k: String, d: Set<String>): Set<String> = p(c).getStringSet(k, d)?.toSet() ?: d
    private fun putSS(c: Context, k: String, v: Set<String>) = p(c).edit().putStringSet(k, HashSet(v)).apply()

    // manual session
    fun end(c: Context) = p(c).getLong("end", 0L)
    fun total(c: Context) = p(c).getLong("total", 1L)
    fun apps(c: Context) = ss(c, "apps", DEFAULT_APPS)
    fun adult(c: Context) = p(c).getBoolean("adult", true)
    fun onboarded(c: Context) = p(c).getBoolean("onb", false)
    fun setOnboarded(c: Context) = p(c).edit().putBoolean("onb", true).apply()
    fun goalMin(c: Context) = p(c).getInt("goal", 180)
    fun setGoalMin(c: Context, v: Int) = p(c).edit().putInt("goal", v).apply()
    fun baseline(c: Context) = p(c).getLong("baseline", 0L)
    fun setBaseline(c: Context, v: Long) = p(c).edit().putLong("baseline", v).apply()
    fun startDay(c: Context) = p(c).getLong("startDay", -1L)
    fun setStartDay(c: Context, v: Long) = p(c).edit().putLong("startDay", v).apply()
    fun best(c: Context) = p(c).getInt("best", 0)

    fun history(c: Context): Map<Long, Long> = try {
        val o = JSONObject(p(c).getString("hist", "{}"))
        o.keys().asSequence().associate { it.toLong() to o.getLong(it) }
    } catch (e: Exception) { emptyMap() }

    fun saveHistory(c: Context, m: Map<Long, Long>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k.toString(), v) }
        p(c).edit().putString("hist", o.toString()).apply()
    }

    fun startSession(c: Context, ms: Long, apps: Set<String>, adult: Boolean) {
        putSS(c, "apps", apps)
        p(c).edit().putLong("end", System.currentTimeMillis() + ms).putLong("total", ms)
            .putBoolean("adult", adult).apply()
    }

    fun complete(c: Context) {
        val e = end(c)
        if (e == 0L) return
        val d = dayOf(e)
        val last = p(c).getLong("lastDay", -1L)
        var st = p(c).getInt("streak", 0)
        st = if (last == d) st else if (last == d - 1) st + 1 else 1
        p(c).edit().putInt("sessions", sessions(c) + 1).putLong("focusMs", focusMs(c) + total(c))
            .putInt("streak", st).putInt("best", maxOf(best(c), st)).putLong("lastDay", d).putLong("end", 0L).apply()
    }

    fun sessions(c: Context) = p(c).getInt("sessions", 0)
    fun focusMs(c: Context) = p(c).getLong("focusMs", 0L)
    fun streak(c: Context): Int {
        val last = p(c).getLong("lastDay", -1L)
        return if (last >= dayOf(System.currentTimeMillis()) - 1) p(c).getInt("streak", 0) else 0
    }

    // schedules
    fun schedules(c: Context): List<Schedule> = try {
        val arr = JSONArray(p(c).getString("sched", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val a = o.getJSONArray("apps")
            Schedule(
                o.getLong("id"), o.getString("name"), o.getInt("days"), o.getInt("start"), o.getInt("end"),
                (0 until a.length()).map { a.getString(it) }.toSet(), o.getBoolean("adult"), o.getBoolean("on")
            )
        }
    } catch (e: Exception) { emptyList() }

    fun saveSchedules(c: Context, l: List<Schedule>) {
        val arr = JSONArray()
        l.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("days", it.days)
                .put("start", it.start).put("end", it.end).put("apps", JSONArray(it.apps.toList()))
                .put("adult", it.adult).put("on", it.on))
        }
        p(c).edit().putString("sched", arr.toString()).apply()
    }

    // daily limits (package -> minutes)
    fun limits(c: Context): Map<String, Int> = try {
        val o = JSONObject(p(c).getString("limits", "{}"))
        o.keys().asSequence().associateWith { o.getInt(it) }
    } catch (e: Exception) { emptyMap() }

    fun saveLimits(c: Context, m: Map<String, Int>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        p(c).edit().putString("limits", o.toString()).apply()
    }

    // mindful pause
    fun pauseApps(c: Context) = ss(c, "pause", emptySet())
    fun savePauseApps(c: Context, s: Set<String>) = putSS(c, "pause", s)
    fun pauseSecs(c: Context) = p(c).getInt("pauseSecs", 10)
    fun setPauseSecs(c: Context, v: Int) = p(c).edit().putInt("pauseSecs", v).apply()
    fun grace(c: Context, pkg: String) = p(c).getLong("g_$pkg", 0L)
    fun setGrace(c: Context, pkg: String, t: Long) = p(c).edit().putLong("g_$pkg", t).apply()

    // custom keywords
    fun keywords(c: Context) = ss(c, "kw", emptySet())
    fun saveKeywords(c: Context, s: Set<String>) = putSS(c, "kw", s)
}

object Blocking {
    fun current(c: Context): Active? {
        val now = System.currentTimeMillis()
        val apps = HashSet<String>()
        var adult = false
        var until = 0L
        var label = ""
        val e = Store.end(c)
        if (now < e) {
            apps.addAll(Store.apps(c)); adult = Store.adult(c); until = e; label = "Focus session"
        }
        for (s in Store.schedules(c)) {
            if (s.on && s.activeNow()) {
                apps.addAll(s.apps); adult = adult || s.adult
                val u = s.untilMillis()
                if (u > until) { until = u; label = s.name }
            }
        }
        return if (until == 0L) null else Active(apps, adult, until, label)
    }
}
