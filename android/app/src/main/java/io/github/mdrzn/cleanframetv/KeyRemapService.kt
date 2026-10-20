package io.github.mdrzn.cleanframetv

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional: opens a chosen app or input when an assigned remote button is pressed (for example the
 * Mi or Netflix key, which TV vendors handle before any app sees them). Accessibility services get
 * key presses first; this one acts only on the assigned keycodes and passes every other key through.
 * It receives no accessibility events and cannot read the screen.
 */
class KeyRemapService : AccessibilityService() {
    private lateinit var catalog: AndroidCatalog
    @Volatile private var buttons: Map<Int, String> = emptyMap()
    private val prefs by lazy { getSharedPreferences("cleanframe", Context.MODE_PRIVATE) }
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AndroidCatalog.BUTTONS) buttons = catalog.buttons()
    }

    override fun onServiceConnected() {
        catalog = AndroidCatalog(this)
        buttons = catalog.buttons()
        prefs.registerOnSharedPreferenceChangeListener(changes)
        Log.i(AndroidCatalog.TAG, "Remote buttons active (${buttons.size} assigned)")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val target = buttons[event.keyCode] ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val error = catalog.launch(target)
            if (error.isNotEmpty()) Log.w(AndroidCatalog.TAG, "Button ${event.keyCode} → $target: $error")
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(changes)
        super.onDestroy()
    }
}
