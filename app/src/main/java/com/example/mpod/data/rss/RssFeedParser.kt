package com.example.mpod.data.rss

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedPodcastFeed(
    val title: String,
    val description: String,
    val author: String,
    val artworkUrl: String,
    val link: String,
    val lastBuildDate: String,
    val episodes: List<ParsedEpisodeItem>
)

data class ParsedEpisodeItem(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val durationSeconds: Long,
    val publishedAt: Long,
    val publishedAtString: String
)

object RssFeedParser {

    private val parserFactory: XmlPullParserFactory by lazy {
        XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
    }

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    )

    fun parse(inputStream: InputStream): ParsedPodcastFeed {
        val parser = parserFactory.newPullParser()
        parser.setInput(inputStream, null)
        parser.nextTag()
        return readRss(parser)
    }

    fun parse(xmlString: String): ParsedPodcastFeed {
        val parser = parserFactory.newPullParser()
        parser.setInput(StringReader(xmlString))
        parser.nextTag()
        return readRss(parser)
    }

    private fun readRss(parser: XmlPullParser): ParsedPodcastFeed {
        parser.require(XmlPullParser.START_TAG, null, "rss")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name.equals("channel", ignoreCase = true)) {
                return readChannel(parser)
            } else {
                skip(parser)
            }
        }
        return ParsedPodcastFeed("", "", "", "", "", "", emptyList())
    }

    private fun readChannel(parser: XmlPullParser): ParsedPodcastFeed {
        parser.require(XmlPullParser.START_TAG, null, "channel")
        var title = ""
        var description = ""
        var author = ""
        var artworkUrl = ""
        var link = ""
        var lastBuildDate = ""
        val episodes = mutableListOf<ParsedEpisodeItem>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val name = parser.name.lowercase(Locale.US)
            when {
                name == "title" && title.isEmpty() -> title = readText(parser)
                (name == "description" || name == "itunes:summary") && description.isEmpty() -> description = readText(parser)
                (name == "itunes:author" || name == "author" || name == "dc:creator") && author.isEmpty() -> author = readText(parser)
                name == "link" && link.isEmpty() -> link = readText(parser)
                name == "lastbuilddate" || name == "pubdate" -> lastBuildDate = readText(parser)
                name == "itunes:image" -> {
                    val href = parser.getAttributeValue(null, "href")
                    if (!href.isNullOrBlank() && artworkUrl.isEmpty()) {
                        artworkUrl = href
                    }
                    skip(parser)
                }
                name == "image" && artworkUrl.isEmpty() -> {
                    artworkUrl = readImageUrl(parser)
                }
                name == "item" -> {
                    episodes.add(readItem(parser))
                }
                else -> skip(parser)
            }
        }
        return ParsedPodcastFeed(
            title = title.trim(),
            description = description.trim(),
            author = author.trim(),
            artworkUrl = artworkUrl.trim(),
            link = link.trim(),
            lastBuildDate = lastBuildDate.trim(),
            episodes = episodes
        )
    }

    private fun readImageUrl(parser: XmlPullParser): String {
        var url = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name.equals("url", ignoreCase = true)) {
                url = readText(parser)
            } else {
                skip(parser)
            }
        }
        return url
    }

    private fun readItem(parser: XmlPullParser): ParsedEpisodeItem {
        var guid = ""
        var title = ""
        var description = ""
        var audioUrl = ""
        var durationSeconds = 0L
        var publishedAt = 0L
        var publishedAtString = ""

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val name = parser.name.lowercase(Locale.US)
            when {
                name == "guid" -> guid = readText(parser)
                name == "title" -> title = readText(parser)
                (name == "description" || name == "content:encoded" || name == "itunes:summary") && description.isEmpty() -> {
                    description = readText(parser)
                }
                name == "enclosure" -> {
                    val url = parser.getAttributeValue(null, "url")
                    if (!url.isNullOrBlank()) {
                        audioUrl = url
                    }
                    skip(parser)
                }
                name == "itunes:duration" -> {
                    val durStr = readText(parser)
                    durationSeconds = parseDuration(durStr)
                }
                name == "pubdate" -> {
                    publishedAtString = readText(parser)
                    publishedAt = parseDate(publishedAtString)
                }
                else -> skip(parser)
            }
        }

        if (guid.isBlank()) {
            guid = if (audioUrl.isNotBlank()) audioUrl else title
        }

        return ParsedEpisodeItem(
            guid = guid.trim(),
            title = title.trim(),
            description = description.trim(),
            audioUrl = audioUrl.trim(),
            durationSeconds = durationSeconds,
            publishedAt = publishedAt,
            publishedAtString = publishedAtString.trim()
        )
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    fun parseDuration(duration: String): Long {
        val trimmed = duration.trim()
        if (trimmed.isEmpty()) return 0L
        if (trimmed.all { it.isDigit() }) {
            return trimmed.toLongOrNull() ?: 0L
        }
        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].substringBefore(".").toLong()
                2 -> parts[0].toLong() * 60 + parts[1].substringBefore(".").toLong()
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun parseDate(dateStr: String): Long {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return System.currentTimeMillis()
        for (format in dateFormats) {
            try {
                val parsed = format.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}
