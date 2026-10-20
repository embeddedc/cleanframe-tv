package io.github.mdrzn.cleanframetv

import android.text.format.DateFormat
import android.webkit.JavascriptInterface

/**
 * window.CleanFrameAndroid — the only surface the web interface sees (app/device.js).
 * Methods are synchronous and run on a WebView binder thread.
 */
class Bridge(private val activity: HomeActivity, private val catalog: AndroidCatalog, private val photos: Photos, private val feed: BingFeed) {
    @Volatile var pendingConfig: String? = null

    @JavascriptInterface fun appsJson(): String = Catalog.toJson(catalog.entries())
    @JavascriptInterface fun launch(appId: String): String = catalog.launch(appId)
    @JavascriptInterface fun openSettings(): String = catalog.openSettings()
    @JavascriptInterface fun openPreviousLauncher(): String = catalog.openPreviousLauncher()
    @JavascriptInterface fun is24Hour(): Boolean = DateFormat.is24HourFormat(activity)
    @JavascriptInterface fun photosJson(): String = photos.json()
    @JavascriptInterface fun refreshPhotos() { feed.refresh(force = true) }
    @JavascriptInterface fun takeConfig(): String { val value = pendingConfig ?: ""; pendingConfig = null; return value }
}
