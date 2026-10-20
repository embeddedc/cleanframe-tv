package io.github.mdrzn.cleanframetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BingFeedTest {
    private val sample = """
        {"images":[
          {"startdate":"20261020","urlbase":"/th?id=OHR.LakeBled_IT-IT123","url":"/th?id=OHR.LakeBled_IT-IT123_1920x1080.jpg&pid=hp",
           "copyright":"Lago di Bled, Slovenia (© Someone/Getty Images)","title":"Un lago da fiaba"},
          {"startdate":"20261019","urlbase":"/th?id=OHR.Dolomiti_IT-IT456","copyright":"Dolomiti, Italia (© Other)","title":""},
          {"startdate":"bad","urlbase":"/th?id=x"},
          {"startdate":"20261018"}
        ]}
    """.trimIndent()

    @Test fun parsesNewestFirstAndSkipsBrokenEntries() {
        val items = BingFeed.parse(sample)
        assertEquals(listOf("bing-20261020", "bing-20261019"), items.map { it.id })
        assertEquals("https://www.bing.com/th?id=OHR.LakeBled_IT-IT123_1920x1080.jpg", items[0].url)
        assertEquals("Un lago da fiaba", items[0].title)
        assertEquals("Lago di Bled, Slovenia (© Someone/Getty Images)", items[0].caption)
        assertEquals("Dolomiti, Italia", items[1].title) // no title: the place from the caption
    }

    @Test fun marketFollowsDeviceLocale() {
        assertEquals("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=it-IT", BingFeed.feedUrl("it", "IT"))
        assertTrue(BingFeed.feedUrl("", "").endsWith("mkt=en-US"))
    }
}
