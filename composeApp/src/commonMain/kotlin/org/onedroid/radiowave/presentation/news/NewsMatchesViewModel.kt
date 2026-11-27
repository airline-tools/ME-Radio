package org.onedroid.radiowave.presentation.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
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
        }
        isReading = true
        isSpeechPaused = false
        speakSelected()
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
        selectedNewsIndex = (selectedNewsIndex + 1) % news.size
        if (isReading && !isSpeechPaused) speakSelected()
    }

    fun previousNews() {
        if (news.isEmpty()) return
        selectedNewsIndex = (selectedNewsIndex - 1 + news.size) % news.size
        if (isReading && !isSpeechPaused) speakSelected()
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
                if (selectedNewsIndex < news.lastIndex) {
                    selectedNewsIndex += 1
                    speakSelected()
                } else {
                    stopReading()
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
