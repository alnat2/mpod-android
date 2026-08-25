package com.example.mpod.data.rss

import android.util.Xml
import com.example.mpod.data.local.entity.PodcastEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OpmlItem(
    val title: String,
    val xmlUrl: String,
    val htmlUrl: String = ""
)

object OpmlParser {

    fun parse(inputStream: InputStream): List<OpmlItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        parser.nextTag()
        return readOpml(parser)
    }

    fun parse(xmlString: String): List<OpmlItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlString))
        parser.nextTag()
        return readOpml(parser)
    }

    private fun readOpml(parser: XmlPullParser): List<OpmlItem> {
        val items = mutableListOf<OpmlItem>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    ?: parser.getAttributeValue(null, "url")
                val text = parser.getAttributeValue(null, "text") ?: ""
                val title = parser.getAttributeValue(null, "title") ?: text
                val htmlUrl = parser.getAttributeValue(null, "htmlUrl") ?: ""

                if (!xmlUrl.isNullOrBlank()) {
                    items.add(
                        OpmlItem(
                            title = if (title.isNotBlank()) title else xmlUrl,
                            xmlUrl = xmlUrl.trim(),
                            htmlUrl = htmlUrl.trim()
                        )
                    )
                }
            }
            eventType = parser.next()
        }
        return items
    }

    fun generateOpml(podcasts: List<PodcastEntity>): String {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        val now = dateFormat.format(Date())
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("""  <head>""")
        sb.appendLine("""    <title>mpoddy Subscriptions</title>""")
        sb.appendLine("""    <dateCreated>$now</dateCreated>""")
        sb.appendLine("""  </head>""")
        sb.appendLine("""  <body>""")
        sb.appendLine("""    <outline text="feeds">""")
        for (pod in podcasts) {
            val titleEscaped = escapeXml(pod.title)
            val urlEscaped = escapeXml(pod.feedUrl)
            val htmlEscaped = escapeXml(pod.link)
            sb.appendLine("""      <outline type="rss" text="$titleEscaped" title="$titleEscaped" xmlUrl="$urlEscaped" htmlUrl="$htmlEscaped" />""")
        }
        sb.appendLine("""    </outline>""")
        sb.appendLine("""  </body>""")
        sb.appendLine("""</opml>""")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
