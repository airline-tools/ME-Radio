package org.onedroid.radiowave.data.news

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class RadioNewsItem(
    val id: String,
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: String = "",
    val summary: String = "",
    val imageUrl: String = ""
)

data class RadioMatchItem(
    val id: String,
    val home: String,
    val away: String,
    val time: String,
    val status: String,
    val competition: String,
    val url: String,
    val competitionImageUrl: String = "",
    val homeScore: String = "",
    val awayScore: String = "",
    val homeImageUrl: String = "",
    val awayImageUrl: String = ""
)

class NewsMatchesRepository(
    private val client: HttpClient
) {
    suspend fun getNews(): List<RadioNewsItem> = withTimeout(20_000L) {
        val body = client.get(NEWS_URL) {
            headers {
                append("User-Agent", BROWSER_USER_AGENT)
                append("Accept", "application/json")
                append("Accept-Language", "ar-EG,ar;q=0.9,en;q=0.8")
                append("Referer", "https://alqaheranews.net/breaking-news")
            }
        }.bodyAsText()

        withContext(Dispatchers.Default) {
            val root = Json.parseToJsonElement(body).jsonObject
            val posts = root["posts"] as? JsonArray
            if (posts == null) {
                emptyList()
            } else {
                posts.mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val title = item.string("title").trim()
                    if (title.isBlank()) return@mapNotNull null
                    val related = item["relatedPost"] as? JsonObject
                    val relatedId = related?.string("id").orEmpty()
                    val slug = related?.string("slug").orEmpty()
                    val articleUrl = if (relatedId.isNotBlank() && slug.isNotBlank()) {
                        "https://alqaheranews.net/news/$relatedId/$slug"
                    } else {
                        ""
                    }
                    RadioNewsItem(
                        id = item.string("id").ifBlank { "$title-${item.string("createdAtUTC")}" },
                        title = title,
                        source = "عاجل - القاهرة الإخبارية",
                        url = articleUrl,
                        publishedAt = item.string("createdAtUTC").ifBlank { item.string("createdAt") },
                        summary = item.firstString("summary", "excerpt", "description", "content")
                            .ifBlank { related?.firstString("summary", "excerpt", "description", "content").orEmpty() },
                        imageUrl = item.firstString("image", "imageUrl", "thumbnail", "cover")
                            .ifBlank { related?.firstString("image", "imageUrl", "thumbnail", "cover").orEmpty() }
                    )
                }.take(30)
            }
        }
    }

    suspend fun getMatches(): List<RadioMatchItem> = withTimeout(20_000L) {
        val html = client.get(MATCHES_URL) {
            headers {
                append("User-Agent", MATCHES_USER_AGENT)
                append("Accept-Language", "ar-EG,ar;q=0.9,en;q=0.8")
            }
        }.bodyAsText()

        withContext(Dispatchers.Default) {
            val cardRegex = Regex(
                """<a[^>]*href=["']([^"']*/match/[^"']*)["'][^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val timeRegex = Regex("""([01]?[0-9]|2[0-3]):[0-5][0-9]""")
            val minuteRegex = Regex("""([0-9]{1,3})[ ]*(?:′|’|'|دقيقة|دقائق)""")
            val statusOptions = listOf(
                "لم تبدأ بعد", "الشوط الأول", "الشوط الثاني", "بين الشوطين",
                "استراحة", "وقت إضافي", "ركلات الترجيح", "لم تبدأ",
                "مباشر", "انتهت", "تأجيل", "ملغاة"
            )
            val output = mutableListOf<RadioMatchItem>()

            // Parse tournament headings once, instead of rescanning the full
            // HTML prefix for every match card.
            val tourRegex = Regex(
                """<a[^>]*href=["']([^"']*/tour/[^"']*)["'][^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val ignoredCompetitions = setOf(
                "المباريات", "الأخبار", "المزيد", "نتائج المباريات", "ترتيب الفرق"
            )
            val tourAnchors = tourRegex.findAll(html).mapNotNull { anchor ->
                val label = cleanHtml(anchor.groupValues[2])
                if (label.isBlank() || label in ignoredCompetitions) {
                    null
                } else {
                    val image = Regex(
                        """<img[^>]+(?:src|data-src)=["']([^"']+)["']""",
                        RegexOption.IGNORE_CASE
                    ).find(anchor.groupValues[2])?.groupValues?.get(1).orEmpty()
                    val absoluteImage = when {
                        image.startsWith("//") -> "https:$image"
                        image.startsWith("/") -> "https://www.yallakora.com$image"
                        image.startsWith("http") -> image
                        else -> ""
                    }
                    Triple(anchor.range.first, label, absoluteImage)
                }
            }.toList()

            cardRegex.findAll(html).take(100).forEach { match ->
                val href = match.groupValues[1]
                var text = cleanHtml(match.groupValues[2])
                if (text.isBlank()) return@forEach
                text = text.replace("التفاصيل", " ").replace("توقع النتيجة", " ")
                val timeMatch = timeRegex.find(text) ?: return@forEach
                val time = timeMatch.value
                val timeIndex = timeMatch.range.first
                val statusEntry = statusOptions.mapNotNull { candidate ->
                    val index = text.indexOf(candidate)
                    if (index >= 0 && index < timeIndex) {
                        Triple(candidate, index, candidate.length)
                    } else null
                }.minByOrNull { it.second }

                var status = statusEntry?.first ?: "لم تبدأ"
                val minuteMatch = minuteRegex.find(text)
                val isInProgress = status.contains("الشوط") ||
                    status.contains("مباشر") ||
                    status.contains("وقت إضافي") ||
                    status.contains("ركلات الترجيح")
                if (minuteMatch != null && isInProgress) {
                    status += " · ${minuteMatch.groupValues[1]}′"
                }

                val homeStart = statusEntry?.let { it.second + it.third } ?: 0
                val home = text.substring(homeStart, timeIndex).trim()
                var away = text.substring(timeMatch.range.last + 1).trim()
                val stadiumIndex = away.indexOf("استاد")
                if (stadiumIndex >= 0) away = away.substring(0, stadiumIndex).trim()
                if (home.isBlank() || away.isBlank() || home.length > 80 || away.length > 80) {
                    return@forEach
                }

                val scoreMatch = Regex("""(?<![0-9])([0-9]{1,2})\s*[-:]\s*([0-9]{1,2})(?![0-9])""").find(text)
                val homeScore = scoreMatch?.groupValues?.get(1).orEmpty()
                val awayScore = scoreMatch?.groupValues?.get(2).orEmpty()
                // YallaKora uses different lazy-loading attributes across match cards.
                // Read common image URL attributes and prefer images tagged with each team.
                val imageTags = Regex(
                    """<img\b[^>]*>""",
                    RegexOption.IGNORE_CASE
                ).findAll(match.groupValues[2]).map { it.value }.toList()

                fun normalizedTeamName(value: String): String = value
                    .lowercase()
                    .replace(Regex("[\u064B-\u065F\u0670]"), "")
                    .replace("ـ", "")
                    .replace(Regex("[^\p{L}\p{N}]"), "")

                fun imageUrl(tag: String): String {
                    val raw = Regex(
                        """(?:src|data-src|data-original|data-lazy-src|data-image|srcset|data-srcset)\s*=\s*["']([^"']+)["']""",
                        RegexOption.IGNORE_CASE
                    ).find(tag)?.groupValues?.get(1)
                        ?.substringBefore(",")
                        ?.trim()
                        ?.substringBefore(" ")
                        .orEmpty()
                    return when {
                        raw.startsWith("//") -> "https:$raw"
                        raw.startsWith("/") -> "https://www.yallakora.com$raw"
                        raw.startsWith("http://") || raw.startsWith("https://") -> raw
                        else -> ""
                    }
                }

                val homeKey = normalizedTeamName(home)
                val awayKey = normalizedTeamName(away)
                val imageCandidates = imageTags.mapNotNull { tag ->
                    val url = imageUrl(tag)
                    if (url.isBlank()) return@mapNotNull null
                    val alt = Regex(
                        """(?:alt|title)\s*=\s*["']([^"']*)["']""",
                        RegexOption.IGNORE_CASE
                    ).find(tag)?.groupValues?.get(1).orEmpty()
                    val classes = Regex(
                        """class\s*=\s*["']([^"']*)["']""",
                        RegexOption.IGNORE_CASE
                    ).find(tag)?.groupValues?.get(1).orEmpty().lowercase()
                    Triple(url, normalizedTeamName(alt), classes)
                }

                val homeImageUrl = imageCandidates.firstOrNull { candidate ->
                    (candidate.second.isNotBlank() &&
                        (candidate.second.contains(homeKey) || homeKey.contains(candidate.second))) ||
                        candidate.third.contains("home") || candidate.third.contains("team-home")
                }?.first.orEmpty()
                val awayImageUrl = imageCandidates.firstOrNull { candidate ->
                    (candidate.second.isNotBlank() &&
                        (candidate.second.contains(awayKey) || awayKey.contains(candidate.second))) ||
                        candidate.third.contains("away") || candidate.third.contains("team-away")
                }?.first.orEmpty()

                // Older cards omit alt/class metadata; use team/crest/flag images first.
                val fallbackTeamImages = imageCandidates
                    .filter { candidate ->
                        candidate.third.contains("team") ||
                            candidate.third.contains("crest") ||
                            candidate.third.contains("flag")
                    }
                    .ifEmpty { imageCandidates }
                val resolvedHomeImage = homeImageUrl.ifBlank { fallbackTeamImages.getOrNull(0)?.first.orEmpty() }
                val resolvedAwayImage = awayImageUrl.ifBlank { fallbackTeamImages.getOrNull(1)?.first.orEmpty() }

                val tournament = tourAnchors.lastOrNull { it.first <= match.range.first }
                val competition = tournament?.second?.ifBlank { competitionFromUrl(href) }
                    ?: competitionFromUrl(href)
                val competitionImageUrl = tournament?.third.orEmpty()
                val absoluteUrl = if (href.startsWith("http")) {
                    href
                } else {
                    "https://www.yallakora.com" + href
                }
                output += RadioMatchItem(
                    id = "$home|$away|$time",
                    home = home,
                    away = away,
                    time = time,
                    status = status,
                    competition = competition,
                    url = absoluteUrl,
                    competitionImageUrl = competitionImageUrl,
                    homeScore = homeScore,
                    awayScore = awayScore,
                    homeImageUrl = resolvedHomeImage,
                    awayImageUrl = resolvedAwayImage
                )
            }
            output.distinctBy { it.id }.take(100)
        }
    }

    private fun competitionFromUrl(url: String): String {
        val labels = mapOf(
            "egyptian-league" to "الدوري المصري",
            "egyptian-league-cup" to "كأس رابطة الأندية",
            "uefa-champions-league" to "دوري أبطال أوروبا",
            "uefa-europa-league" to "الدوري الأوروبي",
            "uefa-conference-league" to "دوري المؤتمر الأوروبي",
            "english-premier-league" to "الدوري الإنجليزي",
            "spanish-league" to "الدوري الإسباني",
            "italian-league" to "الدوري الإيطالي",
            "german-league" to "الدوري الألماني",
            "french-league" to "الدوري الفرنسي",
            "saudi-league" to "الدوري السعودي",
            "uae-league" to "الدوري الإماراتي",
            "qatar-stars-league" to "دوري نجوم قطر",
            "moroccan-league" to "الدوري المغربي",
            "tunisian-league" to "الدوري التونسي",
            "caf-world-cup-qualifiers" to "تصفيات كأس العالم - أفريقيا",
            "afcon-qualifiers" to "تصفيات أمم أفريقيا"
        )
        val lower = url.lowercase()
        return labels.entries.firstOrNull { lower.contains(it.key) }?.value ?: "يلاكورة"
    }

    private fun cleanHtml(value: String): String = value
        .replace(Regex("""(?is)<script.*?</script>"""), " ")
        .replace(Regex("""(?is)<style.*?</style>"""), " ")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&#160;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\\\"")
        .replace("&#39;", "'")
        .replace(Regex("[ ]+"), " ")
        .trim()

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.firstString(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            string(key).takeIf { it.isNotBlank() }
        }.orEmpty()

    companion object {
        private const val NEWS_URL =
            "https://api.alqaheranews.net/api/urgent-posts/published?page=1&perPage=30"
        private const val MATCHES_URL = "https://www.yallakora.com/Match-Center/"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/154.0 Safari/537.36"
        private const val MATCHES_USER_AGENT =
            "Mozilla/5.0 (compatible; HorusTravel/1.0)"
    }
}
