package com.focuslock

import android.content.Context

class Item(val name: String, val pkgs: List<String>)

val ITEMS = listOf(
    Item("Instagram", listOf("com.instagram.android", "com.instagram.lite")),
    Item("YouTube Shorts", listOf("com.google.android.youtube")),
    Item("Snapchat", listOf("com.snapchat.android")),
    Item("Facebook", listOf("com.facebook.katana", "com.facebook.lite")),
    Item("TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"))
)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("fl", Context.MODE_PRIVATE)
    fun end(c: Context) = p(c).getLong("end", 0L)
    fun total(c: Context) = p(c).getLong("total", 1L)
    fun locked(c: Context) = System.currentTimeMillis() < end(c)
    fun apps(c: Context): Set<String> = p(c).getStringSet("apps", ITEMS.map { it.name }.toSet())!!
    fun adult(c: Context) = p(c).getBoolean("adult", true)
    fun override(c: Context) = p(c).getLong("ov", 0L)
    fun setOverride(c: Context, t: Long) = p(c).edit().putLong("ov", t).apply()
    fun start(c: Context, ms: Long, apps: Set<String>, adult: Boolean) {
        p(c).edit().putLong("end", System.currentTimeMillis() + ms).putLong("total", ms)
            .putStringSet("apps", apps).putBoolean("adult", adult).putLong("ov", 0L).apply()
    }
    fun clear(c: Context) = p(c).edit().putLong("end", 0L).putLong("ov", 0L).apply()
}
