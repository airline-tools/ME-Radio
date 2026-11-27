package org.onedroid.radiowave.presentation.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.onedroid.radiowave.data.news.NewsMatchesRepository
import org.onedroid.radiowave.data.news.RadioMatchItem
import org.onedroid.radiowave.data.news.RadioNewsItem

class NewsMatchesViewModel(
    private val repository: NewsMatchesRepository
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

    fun refreshNews() {
        if (isLoadingNews) return
        viewModelScope.launch {
            isLoadingNews = true
            newsError = null
            runCatching { repository.getNews() }
                .onSuccess {
                    news = it
                    if (selectedNewsIndex >= news.size) selectedNewsIndex = 0
                }
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

    fun selectNews(index: Int) {
        if (news.isNotEmpty()) selectedNewsIndex = index.coerceIn(news.indices)
    }

    fun nextNews() {
        if (news.isNotEmpty()) selectedNewsIndex = (selectedNewsIndex + 1) % news.size
    }

    fun previousNews() {
        if (news.isNotEmpty()) selectedNewsIndex = (selectedNewsIndex - 1 + news.size) % news.size
    }
}
