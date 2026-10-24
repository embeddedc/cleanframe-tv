package io.github.mdrzn.cleanframetv

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Native recovery/setup screen remains usable even if the launcher's WebView fails. */
class HomeSettingsActivity : Activity() {
    private val compatibility by lazy { HomeCompatibility(this) }
    private lateinit var status: TextView
    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(24), dp(40), dp(24))
            setBackgroundColor(Color.rgb(24, 24, 24))
        }
        fun text(value: String, size: Float): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, dp(6))
            content.addView(this)
        }
        fun button(label: Int, action: () -> Unit): Button = Button(this).apply {
            setText(label)
            isAllCaps = false
            setOnClickListener { action() }
            content.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        text(getString(R.string.home_settings_title), 26f)
        text(getString(R.string.home_compatibility_explanation), 17f)
        status = text("", 17f)
        toggle = button(R.string.home_compatibility_enable) {
            if (compatibility.enabled) compatibility.pause()
            else if (!compatibility.enable()) {
                compatibility.pause(getString(R.string.home_compatibility_save_failed))
            }
            refresh()
        }
        button(R.string.home_accessibility_settings) {
            showError(AndroidCatalog(this).openAccessibilitySettings())
        }
        button(R.string.home_previous_launcher) {
            showError(AndroidCatalog(this).openPreviousLauncher())
            refresh()
        }
        button(R.string.home_return) {
            startActivity(Intent(this, HomeActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        setContentView(ScrollView(this).apply { addView(content) })
        toggle.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val googleAvailable = packageManager.queryIntentActivities(homeIntent, 0)
            .any { it.activityInfo.packageName == HomeRedirectPolicy.GOOGLE_TV }
        val state = when {
            compatibility.reason.isNotEmpty() -> compatibility.reason
            !compatibility.enabled -> getString(R.string.home_compatibility_off)
            !HomeCompatibilityService.running -> getString(R.string.home_compatibility_needs_service)
            else -> getString(R.string.home_compatibility_on)
        }
        val current = resolved?.loadLabel(packageManager)?.toString() ?: getString(R.string.home_unknown)
        status.text = getString(R.string.home_status, current, state) +
            if (googleAvailable) "" else "\n" + getString(R.string.home_google_unavailable)
        // Always allow turning OFF, even if Google TV becomes unavailable during setup.
        toggle.isEnabled = googleAvailable || compatibility.enabled
        toggle.setText(if (compatibility.enabled) R.string.home_compatibility_disable else R.string.home_compatibility_enable)
    }

    private fun showError(error: String) {
        if (error.isNotEmpty()) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
