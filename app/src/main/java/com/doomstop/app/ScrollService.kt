package com.doomstop.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class ScrollService : AccessibilityService() {
    private var lastEvent = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in Store.selectedApps(this)) return
        if (Store.prefs(this).getBoolean("reels_only", false)) {
            val cls = event.className?.toString() ?: ""
            if (!cls.contains("ViewPager")) return
        }
        val now = System.currentTimeMillis()
        val newSwipe = now - lastEvent > 600
        lastEvent = now
        if (newSwipe) Store.addScroll(this, pkg)
    }

    override fun onInterrupt() {}
}
