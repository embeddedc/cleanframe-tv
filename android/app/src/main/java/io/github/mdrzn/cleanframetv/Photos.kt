package io.github.mdrzn.cleanframetv

import android.content.Context
import java.io.File

/** The downloaded photo catalogue (written by BingFeed) as served to the web interface. */
class Photos(context: Context) {
    val root = File(context.filesDir, "photos").apply { mkdirs() }
    val catalogue = File(root, "photos.json")

    fun json(): String = try { if (catalogue.isFile) catalogue.readText() else "[]" } catch (e: Exception) { "[]" }
}
