package com.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerService : AccessibilityService() {
    private var last = 0L
    private var lastPause = 0L
    private var fgPkg = ""
    private var fgStart = 0L
    private var fgBase = -1L
    private var limits: Map<String, Int> = emptyMap()
    private var limitsAt = 0L

    private val guardPkgs = setOf(
        "com.android.settings", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.samsung.android.packageinstaller", "com.android.vending"
    )
    private val browsers = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/url_bar_title",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.opera.browser" to "com.opera.browser:id/url_field"
    )
    private val bad = listOf(
        "porn", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "xxx", "hentai", "onlyfans",
        "brazzers", "nsfw", "sexvideo", "sex-video", "camgirl", "erotic", "nude", "spankbang",
        "stripchat", "chaturbate", "bhabhi", "desisex", "hotsex", "adultvideo"
    )

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        val stateChange = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (stateChange && pkg != fgPkg && !ignored(pkg)) {
            fgPkg = pkg; fgStart = now; fgBase = -1L
        }

        val a = Blocking.current(this)
        if (a != null) {
            if (pkg in guardPkgs && allText(rootInActiveWindow).let { it.contains("focuslock") || it.contains("focus lock") }) {
                block(null, "Focus Lock settings are locked while focus is on.", a.until); return
            }
            if (pkg in a.apps) {
                block(pkg, "Blocked until ${clock(a.until)} (${a.label}).", a.until); return
            }
            if (SHORTS in a.apps && pkg == YT && inShorts()) {
                throttled { performGlobalAction(GLOBAL_ACTION_BACK) }; return
            }
            if (a.adult && browsers.containsKey(pkg) && adultUrl(pkg)) {
                block(null, "Adult content is blocked until ${clock(a.until)}.", a.until); return
            }
        }

        // daily limits
        if (now - limitsAt > 20_000) { limits = Store.limits(this); limitsAt = now }
        val lim = limits[pkg]
        if (lim != null && pkg == fgPkg) {
            if (fgBase < 0) fgBase = Usage.today(this, pkg)
            if (fgBase + (now - fgStart) >= lim * 60_000L) {
                block(pkg, "You hit your daily limit of $lim min. Back tomorrow.", 0L); return
            }
        }

        // mindful pause
        if (stateChange && now - lastPause > 1500 && pkg in Store.pauseApps(this) && Store.grace(this, pkg) < now) {
            lastPause = now
            startActivity(
                Intent(this, PauseActivity::class.java).putExtra("pkg", pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private fun ignored(pkg: String) =
        pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("keyboard")

    private fun inShorts(): Boolean {
        val r = rootInActiveWindow ?: return false
        return listOf("reel_recycler", "reel_player_page_container", "shorts_player_container")
            .any { r.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$it").isNotEmpty() }
    }

    private fun adultUrl(pkg: String): Boolean {
        val r = rootInActiveWindow ?: return false
        val words = bad + Store.keywords(this)
        return r.findAccessibilityNodeInfosByViewId(browsers[pkg]!!).any { n ->
            val t = n.text?.toString()?.lowercase() ?: ""
            words.any { t.contains(it) }
        }
    }

    private fun allText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val sb = StringBuilder()
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var count = 0
        while (q.isNotEmpty() && count < 250) {
            val n = q.removeFirst(); count++
            n.text?.let { sb.append(it).append(' ') }
            n.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return sb.toString().lowercase()
    }

    private fun throttled(f: () -> Unit) {
        val n = System.currentTimeMillis()
        if (n - last < 800) return
        last = n; f()
    }

    private fun block(pkg: String?, reason: String, until: Long) = throttled {
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra("name", if (pkg == null) "" else Usage.label(this, pkg))
                .putExtra("reason", reason).putExtra("until", until)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    override fun onInterrupt() {}
}
