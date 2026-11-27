package org.onedroid.radiowave.presentation.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.onedroid.radiowave.app.player.PlayerRepository
import org.onedroid.radiowave.app.speech.NewsSpeechController
import org.onedroid.radiowave.data.news.NewsMatchesRepository
import org.onedroid.radiowave.data.news.RadioMatchItem
import org.onedroid.radiowave.data.news.RadioNewsItem

class NewsMatchesViewModel(
    private val repository: NewsMatchesRepository,
    private val playerRepository: PlayerRepository,
    private val speechController: NewsSpeechController
) : ViewModel() {
    var news by mutableStateOf<List<RadioNewsItem>>(emptyList())
        private set
    var matches by mutableStateOf<List<RadioMatchItem>>(emptyList())
        private set
    var isLoadingNews by mutableStateOf(false)
        private set
    var isLoadingMatches by mutableStateOf(false)
        private set
    var newsError by mutableStateOf<String?>(null)
        private set
    var matchesError by mutableStateOf<String?>(null)
        private set
    var selectedNewsIndex by mutableStateOf(0)
        private set
    var isReading by mutableStateOf(false)
        private set
    var isSpeechPaused by mutableStateOf(false)
        private set

    private var radioWasPlaying = false

    fun refreshNews() {
        if (isLoadingNews) return
        viewModelScope.launch {
            isLoadingNews = true
            newsError = null
            try {
                val latest = repository.getNews()
                // Keep the current list stable while new stories are appended.
                news = (news + latest).distinctBy { it.id }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                newsError = error.message ?: "تعذر تحميل الأخبار"
            } finally {
                isLoadingNews = false
            }
        }
    }

    fun refreshMatches() {
        if (isLoadingMatches) return
        viewModelScope.launch {
            isLoadingMatches = true
            matchesError = null
            try {
                matches = repository.getMatches()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                matchesError = error.message ?: "تعذر تحميل المباريات"
            } finally {
                isLoadingMatches = false
            }
        }
    }

    fun selectNews(index: Int) {
        if (news.isNotEmpty()) selectedNewsIndex = index.coerceIn(news.indices)
    }

    fun startReading() {
        if (news.isEmpty()) return
        if (!isReading) {
            radioWasPlaying = playerRepository.pauseForAnnouncement()
            selectedNewsIndex = news.lastIndex
        }
        isReading = true
        isSpeechPaused = false
        speakBulletinIntro()
    }

    fun pauseReading() {
        if (!isReading || isSpeechPaused) return
        speechController.stop()
        isSpeechPaused = true
    }

    fun resumeReading() {
        if (!isReading || !isSpeechPaused) return
        isSpeechPaused = false
        speakSelected()
    }

    fun stopReading() {
        speechController.stop()
        isReading = false
        isSpeechPaused = false
        playerRepository.resumeAfterAnnouncement(radioWasPlaying)
        radioWasPlaying = false
    }

    fun nextNews() {
        if (news.isEmpty()) return
        selectedNewsIndex = (selectedNewsIndex - 1 + news.size) % news.size
        if (isReading && !isSpeechPaused) speakSelected()
    }

    fun previousNews() {
        if (news.isEmpty()) return
        selectedNewsIndex = (selectedNewsIndex + 1) % news.size
        if (isReading && !isSpeechPaused) speakSelected()
    }

    private fun speakBulletinIntro() {
        if (!isReading || isSpeechPaused || news.isEmpty()) return
        val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val weekday = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")[date.dayOfWeek.ordinal]
        val gregorianMonths = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        val hijri = toTabularHijri(date.toEpochDays() + 2_440_588)
        val intro = "إليكم آخر أخبار اليوم، $weekday، الموافق ${date.dayOfMonth} من شهر ${gregorianMonths[date.monthNumber - 1]} لعام ${date.year} ميلادياً، والموافق ${hijri.third} من شهر ${hijri.second} لعام ${hijri.first} هجرياً. نبدأ الآن نشرتنا الإخبارية."
        speechController.speak(intro) {
            viewModelScope.launch { if (isReading && !isSpeechPaused) speakSelected() }
        }
    }

    private fun toTabularHijri(julianDay: Int): Triple<Int, String, Int> {
        val l0 = julianDay - 1_948_440 + 10_632
        val n = l0 / 10_631
        val l1 = l0 - 10_631 * n + 354
        val j = ((10_985 - l1) / 5_316) * ((50 * l1) / 17_719) + (l1 / 5_670) * ((43 * l1) / 15_238)
        val l2 = l1 - ((30 - j) / 15) * ((17_719 * j) / 50) - (j / 16) * ((15_238 * j) / 43) + 29
        val month = (24 * l2) / 709
        val day = l2 - (709 * month) / 24
        val year = 30 * n + j - 30
        val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
        return Triple(year, months[(month - 1).coerceIn(0, 11)], day)
    }

    private fun speakBulletinOutro() {
        if (!isReading || isSpeechPaused) return
        speechController.speak("كانت هذه نشرتنا الإخبارية. شكرًا لحسن استماعكم، وإلى اللقاء في أخبار قادمة.") {
            viewModelScope.launch { if (isReading && !isSpeechPaused) stopReading() }
        }
    }
    private fun speakSelected() {
        if (!isReading || isSpeechPaused || news.isEmpty()) return
        val item = news[selectedNewsIndex]
        val spokenText = buildString {
            append(item.title)
            if (item.summary.isNotBlank() && item.summary != item.title) {
                append(". ")
                append(item.summary)
            }
        }
        speechController.speak(spokenText) {
            viewModelScope.launch {
                if (!isReading || isSpeechPaused) return@launch
                if (selectedNewsIndex > 0) {
                    selectedNewsIndex -= 1
                    speakSelected()
                } else {
                    speakBulletinOutro()
                }
            }
        }
    }

    override fun onCleared() {
        speechController.stop()
        playerRepository.resumeAfterAnnouncement(radioWasPlaying)
        super.onCleared()
    }
}
