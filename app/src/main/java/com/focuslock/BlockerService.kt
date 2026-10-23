package com.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerService : AccessibilityService() {
    private var last = 0L

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
        "brazzers", "nsfw", "sexvideo", "sex-video", "sexy-video", "camgirl", "erotic", "nude",
        "spankbang", "stripchat", "chaturbate", "bhabhi", "desisex", "hotsex", "adultvideo"
    )

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || !Store.locked(this)) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return

        val hit = ITEMS.firstOrNull { pkg in it.pkgs }
        if (hit != null && hit.name in Store.apps(this)) {
            if (hit.name == "YouTube Shorts") {
                if (inShorts()) { throttled { performGlobalAction(GLOBAL_ACTION_BACK) } }
            } else kick()
            return
        }
        if (pkg in guardPkgs && allText(rootInActiveWindow).contains("focuslock")) { kick(); return }
        if (Store.adult(this) && pkg in browsers && adultUrl(pkg)) kick()
    }

    private fun inShorts(): Boolean {
        val r = rootInActiveWindow ?: return false
        return listOf("reel_recycler", "reel_player_page_container", "shorts_player_container")
            .any { r.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$it").isNotEmpty() }
    }

    private fun adultUrl(pkg: String): Boolean {
        val r = rootInActiveWindow ?: return false
        val nodes = r.findAccessibilityNodeInfosByViewId(browsers[pkg]!!)
        return nodes.any { n ->
            val t = n.text?.toString()?.lowercase() ?: ""
            bad.any { t.contains(it) }
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

    private fun kick() = throttled {
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    override fun onInterrupt() {}
}
