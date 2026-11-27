package org.onedroid.radiowave.data.news

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    val url: String
)

class NewsMatchesRepository(
    private val client: HttpClient
) {
    suspend fun getNews(): List<RadioNewsItem> {
        val response = client.get(NEWS_URL) {
            headers {
                append("User-Agent", BROWSER_USER_AGENT)
                append("Accept", "application/json")
                append("Accept-Language", "ar-EG,ar;q=0.9,en;q=0.8")
                append("Referer", "https://alqaheranews.net/breaking-news")
            }
        }
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val posts = root["posts"] as? JsonArray ?: return emptyList()
        return posts.mapNotNull { element ->
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
                summary = item.firstString("summary", "excerpt", "description", "content"),
                imageUrl = item.firstString("image", "imageUrl", "thumbnail", "cover")
            )
        }.take(30)
    }

    suspend fun getMatches(): List<RadioMatchItem> {
        val html = client.get(MATCHES_URL) {
            headers {
                append("User-Agent", MATCHES_USER_AGENT)
                append("Accept-Language", "ar-EG,ar;q=0.9,en;q=0.8")
            }
        }.bodyAsText()

        val cardRegex = Regex(
            """<a\\b[^>]*href=["']([^"']*/match/[^"']*)["'][^>]*>([\\s\\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        val timeRegex = Regex("""\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b""")
        val statusOptions = listOf(
            "لم تبدأ بعد", "الشوط الأول", "الشوط الثاني", "بين الشوطين",
            "استراحة", "وقت إضافي", "ركلات الترجيح", "لم تبدأ",
            "مباشر", "انتهت", "تأجيل", "ملغاة"
        )
        val output = mutableListOf<RadioMatchItem>()

        cardRegex.findAll(html).take(100).forEach { match ->
            val href = match.groupValues[1]
            var text = cleanHtml(match.groupValues[2])
            if (text.isBlank()) return@forEach
            text = text.replace("التفاصيل", " ").replace("توقع النتيجة", " ")
            val timeMatch = timeRegex.find(text) ?: return@forEach
            val time = timeMatch.value
            val timeIndex = timeMatch.range.first
            val statusEntry = statusOptions
                .mapNotNull { candidate ->
                    val index = text.indexOf(candidate)
                    if (index >= 0 && index < timeIndex) Triple(candidate, index, candidate.length) else null
                }
                .minByOrNull { it.second }
            val status = statusEntry?.first ?: "لم تبدأ"
            val homeStart = statusEntry?.let { it.second + it.third } ?: 0
            var home = text.substring(homeStart, timeIndex).trim()
            var away = text.substring(timeMatch.range.last + 1).trim()
            home = home.replace(Regex("""\\s+[-–—]\\s+[-–—]\\s*$"""), "").trim()
            away = away.replace(Regex("""\\s+[-–—]\\s+[-–—].*$"""), "").trim()
                .replace(Regex("""\\s+استاد.*$""", RegexOption.IGNORE_CASE), "").trim()
            if (home.isBlank() || away.isBlank() || home.length > 80 || away.length > 80) return@forEach

            val competition = competitionBefore(html, match.range.first)
                .ifBlank { competitionFromUrl(href) }
            val absoluteUrl = if (href.startsWith("http")) href else "https://www.yallakora.com" + href
            output += RadioMatchItem(
                id = "$home|$away|$time",
                home = home,
                away = away,
                time = time,
                status = status,
                competition = competition,
                url = absoluteUrl
            )
        }
        return output.distinctBy { it.id }.take(100)
    }

    private fun competitionBefore(html: String, position: Int): String {
        val before = html.take(position)
        val tourRegex = Regex(
            """<a\\b[^>]*href=["']([^"']*/tour/[^"']*)["'][^>]*>([\\s\\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        val ignored = setOf("المباريات", "الأخبار", "المزيد", "نتائج المباريات", "ترتيب الفرق")
        return tourRegex.findAll(before)
            .map { cleanHtml(it.groupValues[2]) }
            .lastOrNull { it.isNotBlank() && it !in ignored }
            .orEmpty()
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
        .replace(Regex("""<script[\\s\\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<style[\\s\\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&#160;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("""\\s+"""), " ")
        .trim()

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.firstString(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> string(key).takeIf { it.isNotBlank() } }.orEmpty()

    companion object {
        private const val NEWS_URL =
            "https://api.alqaheranews.net/api/urgent-posts/published?page=1&perPage=30"
        private const val MATCHES_URL = "https://www.yallakora.com/Match-Center/"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/154.0 Safari/537.36"
        private const val MATCHES_USER_AGENT = "Mozilla/5.0 (compatible; HorusTravel/1.0)"
    }
}
