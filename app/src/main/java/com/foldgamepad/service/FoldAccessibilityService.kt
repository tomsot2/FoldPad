package com.foldgamepad.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.foldgamepad.util.InputInjector

class FoldAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        InputInjector.service = this
        serviceInfo = serviceInfo.also {
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (InputInjector.service === this) InputInjector.service = null
    }
}
