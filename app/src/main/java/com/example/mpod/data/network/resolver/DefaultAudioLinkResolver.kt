package com.example.mpod.data.network.resolver

import com.example.mpod.data.network.NetworkModule
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.rss.RssFeedParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAudioLinkResolver @Inject constructor(
    private val proxyHttpClientFactory: ProxyHttpClientFactory
) : AudioLinkResolver {

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus", "weba"
        )

        private val APPLE_PODCASTS_EPISODE_ID_REGEX = Regex("""[?&]i=(\d+)""")
        private val APPLE_PODCASTS_PODCAST_ID_REGEX = Regex("""(?:id|/podcast/[^/]+/id)(\d+)""")
    }

    override suspend fun resolve(rawUrl: String): Result<ResolvedAudioMetadata> = withContext(Dispatchers.IO) {
        val trimmedUrl = rawUrl.trim()
        if (trimmedUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URL cannot be empty"))
        }

        // Direct local/content URIs
        if (trimmedUrl.startsWith("content://") || trimmedUrl.startsWith("file://")) {
            return@withContext Result.success(
                ResolvedAudioMetadata(
                    directAudioUrl = trimmedUrl,
                    title = deriveTitleFromUrl(trimmedUrl)
                )
            )
        }

        val uri = runCatching { URI.create(trimmedUrl) }.getOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid URL format: $trimmedUrl"))

        val host = uri.host?.lowercase(Locale.ROOT) ?: ""

        // Strategy 2: Apple Podcasts URL
        if (host.contains("podcasts.apple.com") || host.contains("itunes.apple.com")) {
            val appleResult = resolveApplePodcasts(trimmedUrl)
            if (appleResult.isSuccess) {
                return@withContext appleResult
            }
        }

        // Strategy 1: Direct file extension check
        val extension = getUrlExtension(trimmedUrl)
        if (extension != null && AUDIO_EXTENSIONS.contains(extension)) {
            return@withContext Result.success(
                ResolvedAudioMetadata(
                    directAudioUrl = trimmedUrl,
                    title = deriveTitleFromUrl(trimmedUrl)
                )
            )
        }

        // Network inspection: HEAD or GET request
        val client = proxyHttpClientFactory.createClient()
        val headResult = inspectViaHttpHead(client, trimmedUrl)
        if (headResult != null) {
            return@withContext Result.success(headResult)
        }

        // Strategy 3: Web Page / OpenGraph / HTML scraping
        resolveFromWebPage(client, trimmedUrl)
    }

    private fun getUrlExtension(url: String): String? {
        val path = try {
            URI.create(url).path ?: ""
        } catch (_: Exception) {
            url.substringBefore('?').substringBefore('#')
        }
        val filename = path.substringAfterLast('/')
        val ext = filename.substringAfterLast('.', "")
        return if (ext.isNotBlank() && ext.length <= 5) ext.lowercase(Locale.ROOT) else null
    }

    private fun deriveTitleFromUrl(url: String): String {
        return try {
            val uri = URI.create(url)
            val path = uri.path ?: ""
            val filename = path.substringAfterLast('/')
            val nameWithoutExt = filename.substringBeforeLast('.')
            if (nameWithoutExt.isNotBlank()) {
                nameWithoutExt.replace('-', ' ').replace('_', ' ').replace("%20", " ").trim()
            } else {
                uri.host ?: "Audio Track"
            }
        } catch (_: Exception) {
            "Audio Track"
        }
    }

    private fun inspectViaHttpHead(client: OkHttpClient, url: String): ResolvedAudioMetadata? {
        return runCatching {
            val headRequest = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT) ?: ""
                    if (contentType.startsWith("audio/") || contentType == "application/ogg") {
                        val finalUrl = response.request.url.toString()
                        return@runCatching ResolvedAudioMetadata(
                            directAudioUrl = finalUrl,
                            title = deriveTitleFromUrl(finalUrl)
                        )
                    }
                }
            }
            null
        }.getOrNull()
    }

    private suspend fun resolveApplePodcasts(url: String): Result<ResolvedAudioMetadata> {
        val podcastId = APPLE_PODCASTS_PODCAST_ID_REGEX.find(url)?.groupValues?.get(1)
        val episodeId = APPLE_PODCASTS_EPISODE_ID_REGEX.find(url)?.groupValues?.get(1)

        val client = proxyHttpClientFactory.createClient()

        // 1. Try iTunes Lookup API if we have podcastId
        if (podcastId != null) {
            val itunesResult = queryItunesLookup(client, podcastId, episodeId, url)
            if (itunesResult != null) {
                return Result.success(itunesResult)
            }
        }

        // 2. Fallback: Parse episode page HTML
        return parseApplePodcastsHtml(client, url)
    }

    private fun queryItunesLookup(
        client: OkHttpClient,
        podcastId: String,
        episodeId: String?,
        appleUrl: String
    ): ResolvedAudioMetadata? {
        return runCatching {
            val limit = if (episodeId != null) 100 else 2
            val lookupUrl = "https://itunes.apple.com/lookup?id=$podcastId&entity=podcastEpisode&limit=$limit"
            val request = Request.Builder()
                .url(lookupUrl)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null

                val directMatch = parseItunesLookupResponse(body, episodeId)
                if (directMatch != null) return@use directMatch

                // Episode was not in the iTunes lookup limit window (e.g. older episode).
                // Try resolving via the podcast's official RSS feedUrl provided in the iTunes response!
                val feedUrl = extractFeedUrlFromItunesResponse(body)
                if (!feedUrl.isNullOrBlank()) {
                    val rssMatch = resolveFromPodcastRssFeed(client, feedUrl, episodeId, appleUrl)
                    if (rssMatch != null) return@use rssMatch
                }

                null
            }
        }.getOrNull()
    }

    internal fun extractFeedUrlFromItunesResponse(json: String): String? {
        return Regex(""""feedUrl"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)?.let {
            cleanDirectUrl(unescapeJson(it))
        }
    }

    internal fun resolveFromPodcastRssFeed(
        client: OkHttpClient,
        feedUrl: String,
        targetEpisodeId: String?,
        appleUrl: String
    ): ResolvedAudioMetadata? {
        return runCatching {
            val request = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val inputStream = response.body?.byteStream() ?: return@use null
                val parsedFeed = RssFeedParser.parse(inputStream)
                if (parsedFeed.episodes.isEmpty()) return@use null

                val targetSlugTitle = extractSlugTitleFromAppleUrl(appleUrl)

                // 1. Try finding by episodeId matching guid/link/audioUrl
                var matched = if (!targetEpisodeId.isNullOrBlank()) {
                    parsedFeed.episodes.find { ep ->
                        ep.guid == targetEpisodeId ||
                            ep.guid.contains(targetEpisodeId) ||
                            ep.audioUrl.contains(targetEpisodeId)
                    }
                } else null

                // 2. Try finding by matching title from URL slug
                if (matched == null && !targetSlugTitle.isNullOrBlank()) {
                    val normalizedSlug = normalizeForComparison(targetSlugTitle)
                    matched = parsedFeed.episodes.find { ep ->
                        val normalizedTitle = normalizeForComparison(ep.title)
                        normalizedTitle == normalizedSlug ||
                            normalizedTitle.contains(normalizedSlug) ||
                            normalizedSlug.contains(normalizedTitle)
                    }
                }

                // 3. Fallback: If no match and targetEpisodeId is null, pick first episode
                if (matched == null && targetEpisodeId.isNullOrBlank()) {
                    matched = parsedFeed.episodes.firstOrNull()
                }

                val ep = matched ?: return@use null
                if (ep.audioUrl.isBlank()) return@use null

                ResolvedAudioMetadata(
                    directAudioUrl = cleanDirectUrl(ep.audioUrl),
                    title = ep.title.ifBlank { targetSlugTitle ?: "Podcast Episode" },
                    author = parsedFeed.author.ifBlank { parsedFeed.title },
                    podcastTitle = parsedFeed.title.ifBlank { parsedFeed.author },
                    artworkUrl = parsedFeed.artworkUrl.ifBlank { null },
                    durationSeconds = if (ep.durationSeconds > 0) ep.durationSeconds.toInt() else null,
                    description = ep.description.ifBlank { null }
                )
            }
        }.getOrNull()
    }

    internal fun extractSlugTitleFromAppleUrl(url: String): String? {
        val slug = Regex("""/podcast/([^/?#]+)/id\d+""").find(url)?.groupValues?.get(1) ?: return null
        return runCatching {
            URLDecoder.decode(slug, "UTF-8")
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
        }.getOrNull()
    }

    internal fun normalizeForComparison(str: String): String {
        return str.lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
    }

    internal fun parseItunesLookupResponse(json: String, targetEpisodeId: String?): ResolvedAudioMetadata? {
        return runCatching {
            // Find episode items in JSON results
            val episodeBlocks = extractJsonObjectsFromArray(json)
            for (block in episodeBlocks) {
                if (!block.contains("\"episodeUrl\"") && !block.contains("\"previewUrl\"")) continue

                val trackId = Regex(""""trackId"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1)
                if (targetEpisodeId != null && trackId != targetEpisodeId) {
                    // Check trackViewUrl for ?i=
                    val hasEpisodeMatch = block.contains("i=$targetEpisodeId")
                    if (!hasEpisodeMatch) continue
                }

                val directAudioUrl = Regex(""""episodeUrl"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)
                    ?: Regex(""""previewUrl"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)
                    ?: continue

                val title = unescapeJson(Regex(""""trackName"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: "")
                val collectionName = unescapeJson(Regex(""""collectionName"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: "")
                val artistName = unescapeJson(Regex(""""artistName"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: "")
                val artworkUrl = Regex(""""artworkUrl600"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)
                    ?: Regex(""""artworkUrl160"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)
                val durationMs = Regex(""""trackTimeMillis"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1)?.toLongOrNull()
                val description = unescapeJson(Regex(""""(?:shortDescription|description)"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: "")

                return ResolvedAudioMetadata(
                    directAudioUrl = cleanDirectUrl(directAudioUrl),
                    title = title.ifBlank { "Podcast Episode" },
                    author = artistName.ifBlank { collectionName },
                    podcastTitle = collectionName.ifBlank { artistName },
                    artworkUrl = artworkUrl,
                    durationSeconds = durationMs?.let { (it / 1000).toInt() },
                    description = description.ifBlank { null }
                )
            }
            null
        }.getOrNull()
    }

    private fun parseApplePodcastsHtml(client: OkHttpClient, url: String): Result<ResolvedAudioMetadata> {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Failed to load Apple Podcasts web page: HTTP ${response.code}")
                }
                val html = response.body?.string().orEmpty()
                parseApplePodcastsHtmlContent(html)
                    ?: error("Could not find playable audio stream in Apple Podcasts page")
            }
        }
    }

    internal fun parseApplePodcastsHtmlContent(html: String): ResolvedAudioMetadata? {
        val streamUrl = Regex(""""streamUrl"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex(""""actionUrl"\s*:\s*"(http[^"]+\.mp3[^"]*)"""").find(html)?.groupValues?.get(1)
            ?: return null

        val rawTitle = Regex(""""pageDetails"\s*:\s*\{[^}]*"title"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)?.substringBefore(" - ")
            ?: Regex(""""title"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: "Episode"
        val title = unescapeJson(rawTitle)

        val showTitle = unescapeJson(
            Regex(""""showOffer"[^}]*"title"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1) ?: ""
        ).ifBlank {
            Regex("""<meta\s+property="og:site_name"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)
        }

        val artworkUrl = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)
        val duration = Regex(""""duration"\s*:\s*(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull()

        return ResolvedAudioMetadata(
            directAudioUrl = cleanDirectUrl(streamUrl),
            title = unescapeHtml(title),
            author = showTitle?.let { unescapeHtml(it) },
            podcastTitle = showTitle?.let { unescapeHtml(it) },
            artworkUrl = artworkUrl,
            durationSeconds = duration
        )
    }

    private suspend fun resolveFromWebPage(client: OkHttpClient, url: String): Result<ResolvedAudioMetadata> {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} loading $url")
                }

                val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT) ?: ""
                val finalUrl = response.request.url.toString()

                // If redirected to audio direct stream
                if (contentType.startsWith("audio/") || contentType == "application/ogg") {
                    return@use ResolvedAudioMetadata(
                        directAudioUrl = finalUrl,
                        title = deriveTitleFromUrl(finalUrl)
                    )
                }

                // If redirected to Apple Podcasts
                if (finalUrl.contains("podcasts.apple.com") || finalUrl.contains("itunes.apple.com")) {
                    val appleResult = resolveApplePodcasts(finalUrl)
                    if (appleResult.isSuccess) {
                        return@use appleResult.getOrThrow()
                    }
                }

                val html = response.body?.string().orEmpty()
                parseGenericWebHtml(html, finalUrl)
                    ?: error("Could not extract audio from this link. Make sure it's a valid podcast episode or direct audio link.")
            }
        }
    }

    internal fun parseGenericWebHtml(html: String, baseUrl: String): ResolvedAudioMetadata? {
        val audioUrl = findAudioUrlInHtml(html) ?: return null
        val directUrl = resolveUrl(baseUrl, audioUrl)

        val title = findMetaContent(html, "og:title")
            ?: findTagContent(html, "title")
            ?: deriveTitleFromUrl(directUrl)

        val siteName = findMetaContent(html, "og:site_name")
            ?: findMetaContent(html, "author")

        val artworkUrl = findMetaContent(html, "og:image")?.let { resolveUrl(baseUrl, it) }

        return ResolvedAudioMetadata(
            directAudioUrl = cleanDirectUrl(directUrl),
            title = unescapeHtml(title).trim(),
            author = siteName?.let { unescapeHtml(it).trim() },
            podcastTitle = siteName?.let { unescapeHtml(it).trim() },
            artworkUrl = artworkUrl
        )
    }

    private fun findAudioUrlInHtml(html: String): String? {
        // og:audio or og:audio:secure_url
        val ogAudio = findMetaContent(html, "og:audio:secure_url")
            ?: findMetaContent(html, "og:audio")
            ?: findMetaContent(html, "twitter:player:stream")
        if (!ogAudio.isNullOrBlank()) return ogAudio

        // <audio src="...">
        val audioSrc = Regex("""<audio[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        if (!audioSrc.isNullOrBlank()) return audioSrc

        // <source src="..." type="audio/...">
        val sourceSrc = Regex("""<source[^>]+src=["']([^"']+)["'][^>]+type=["']audio/[^"']+["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?: Regex("""<source[^>]+type=["']audio/[^"']+["'][^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)
        if (!sourceSrc.isNullOrBlank()) return sourceSrc

        return null
    }

    private fun findMetaContent(html: String, propertyOrName: String): String? {
        val p = Pattern.quote(propertyOrName)
        val pattern1 = Regex("""<meta[^>]+(?:property|name)=["']$p["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val match1 = pattern1.find(html)?.groupValues?.get(1)
        if (!match1.isNullOrBlank()) return match1

        val pattern2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$p["']""", RegexOption.IGNORE_CASE)
        val match2 = pattern2.find(html)?.groupValues?.get(1)
        if (!match2.isNullOrBlank()) return match2

        return null
    }

    private fun findTagContent(html: String, tagName: String): String? {
        val p = Pattern.quote(tagName)
        val pattern = Regex("""<$p[^>]*>(.*?)</$p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return pattern.find(html)?.groupValues?.get(1)?.trim()
    }

    private fun resolveUrl(baseUrl: String, candidateUrl: String): String {
        return try {
            val baseUri = URI.create(baseUrl)
            baseUri.resolve(candidateUrl).toString()
        } catch (_: Exception) {
            candidateUrl
        }
    }

    private fun cleanDirectUrl(url: String): String {
        return url.replace("\\/", "/").trim()
    }

    private fun unescapeJson(str: String): String {
        return str
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun unescapeHtml(str: String): String {
        return str
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#x27;", "'")
            .replace("&#8217;", "’")
            .replace("&#8216;", "‘")
            .replace("&#8220;", "“")
            .replace("&#8221;", "”")
    }

    internal fun extractJsonObjectsFromArray(resultsJson: String): List<String> {
        val resultsStart = resultsJson.indexOf("\"results\"")
        if (resultsStart == -1) return emptyList()
        val arrayStart = resultsJson.indexOf('[', resultsStart)
        if (arrayStart == -1) return emptyList()

        val objects = mutableListOf<String>()
        var depth = 0
        var objStart = -1
        var inString = false
        var escape = false

        for (i in arrayStart until resultsJson.length) {
            val c = resultsJson[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue

            if (c == '{') {
                if (depth == 0) objStart = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && objStart != -1) {
                    objects.add(resultsJson.substring(objStart, i + 1))
                    objStart = -1
                }
            } else if (c == ']' && depth == 0) {
                break
            }
        }
        return objects
    }
}
