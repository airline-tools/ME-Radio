package org.onedroid.radiowave.presentation.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.onedroid.radiowave.data.news.NewsMatchesRepository
import org.onedroid.radiowave.data.news.RadioMatchItem
import org.onedroid.radiowave.data.news.RadioNewsItem

class NewsMatchesViewModel(
    private val repository: NewsMatchesRepository
) : ViewModel() {
    var news: List<RadioNewsItem> = emptyList()
        private set
    var matches: List<RadioMatchItem> = emptyList()
        private set
    var isLoadingNews = false
        private set
    var isLoadingMatches = false
        private set
    var newsError: String? = null
        private set
    var matchesError: String? = null
        private set
    var selectedNewsIndex = 0
        private set
    var isReading = false
        private set

    private var newsJob: Job? = null
    private var matchesJob: Job? = null
    private var readingJob: Job? = null

    fun refreshNews() {
        if (isLoadingNews) return
        viewModelScope.launch {
            isLoadingNews = true
            newsError = null
            runCatching { repository.getNews() }
                .onSuccess { news = it }
                .onFailure { newsError = it.message ?: "تعذر تحميل الأخبار" }
            isLoadingNews = false
        }
    }

    fun refreshMatches() {
        if (isLoadingMatches) return
        viewModelScope.launch {
            isLoadingMatches = true
            matchesError = null
            runCatching { repository.getMatches() }
                .onSuccess { matches = it }
                .onFailure { matchesError = it.message ?: "تعذر تحميل المباريات" }
            isLoadingMatches = false
        }
    }

    fun setReadingState(reading: Boolean) {
        isReading = reading
    }

    fun selectNews(index: Int) {
        if (news.isEmpty()) return
        selectedNewsIndex = index.coerceIn(news.indices)
    }

    fun nextNews() {
        if (news.isNotEmpty()) selectedNewsIndex = (selectedNewsIndex + 1) % news.size
    }

    fun previousNews() {
        if (news.isNotEmpty()) selectedNewsIndex = (selectedNewsIndex - 1 + news.size) % news.size
    }

    fun stopReading() {
        readingJob?.cancel()
        readingJob = null
        isReading = false
    }

    override fun onCleared() {
        stopReading()
        super.onCleared()
    }
}
