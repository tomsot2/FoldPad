package com.foldgamepad.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.foldgamepad.util.InputInjector

class FoldAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { InputInjector.service = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        if (InputInjector.service === this) InputInjector.service = null
    }
}
