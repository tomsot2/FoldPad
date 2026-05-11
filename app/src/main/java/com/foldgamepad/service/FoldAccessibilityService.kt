package com.foldgamepad.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.foldgamepad.util.InputInjector

class FoldAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        InputInjector.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only use this service for gesture dispatch, not event monitoring.
    }

    override fun onInterrupt() {
        InputInjector.service = null
    }

    override fun onDestroy() {
        super.onDestroy()
        InputInjector.service = null
    }
}
