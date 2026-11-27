package org.onedroid.radiowave.presentation.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.onedroid.radiowave.data.news.RadioMatchItem
import org.onedroid.radiowave.data.news.RadioNewsItem

private val NewsNavy = Color(0xFF071426)
private val NewsPanel = Color(0xFF10243A)
private val NewsPanelAlt = Color(0xFF162D43)
private val NewsLime = Color(0xFFC8F169)
private val NewsMuted = Color(0xFF9AAABD)
private val NewsWhite = Color(0xFFF3F6F8)

private enum class NewsTab { NEWS, MATCHES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsMatchesScreen(
    rootNavController: NavController,
    viewModel: NewsMatchesViewModel = koinViewModel()
) {
    var selectedTab by remember { mutableStateOf(NewsTab.NEWS) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.refreshNews()
        viewModel.refreshMatches()
        var refreshCount = 0
        while (true) {
            delay(60_000)
            viewModel.refreshMatches()
            refreshCount++
            if (refreshCount % 2 == 0) viewModel.refreshNews()
        }
    }

    Scaffold(
        containerColor = NewsNavy,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ME RADIO", color = NewsLime, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text("الأخبار والمباريات", color = NewsMuted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { rootNavController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = NewsWhite)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (selectedTab == NewsTab.NEWS) viewModel.refreshNews() else viewModel.refreshMatches()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = NewsLime)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NewsNavy)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabButton("الأخبار العاجلة", selectedTab == NewsTab.NEWS, Modifier.weight(1f)) {
                    selectedTab = NewsTab.NEWS
                }
                TabButton("المباريات", selectedTab == NewsTab.MATCHES, Modifier.weight(1f)) {
                    selectedTab = NewsTab.MATCHES
                }
            }
            when (selectedTab) {
                NewsTab.NEWS -> NewsFeed(viewModel, onOpen = { url ->
                    if (url.isNotBlank()) runCatching { uriHandler.openUri(url) }
                })
                NewsTab.MATCHES -> MatchesFeed(viewModel, onOpen = { url ->
                    if (url.isNotBlank()) runCatching { uriHandler.openUri(url) }
                })
            }
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) NewsLime else NewsPanel,
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 13.dp, horizontal = 10.dp),
            color = if (selected) NewsNavy else NewsWhite,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NewsFeed(viewModel: NewsMatchesViewModel, onOpen: (String) -> Unit) {
    when {
        viewModel.isLoadingNews && viewModel.news.isEmpty() -> LoadingState()
        viewModel.newsError != null && viewModel.news.isEmpty() -> ErrorState(viewModel.newsError!!) {
            viewModel.refreshNews()
        }
        viewModel.news.isEmpty() -> EmptyState("لا توجد أخبار عاجلة متاحة حاليًا")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NewsPanelAlt),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("النشرة الصوتية", color = NewsLime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            viewModel.news.getOrNull(viewModel.selectedNewsIndex)?.title.orEmpty(),
                            color = NewsWhite,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = viewModel::previousNews,
                                colors = ButtonDefaults.buttonColors(containerColor = NewsPanel, contentColor = NewsWhite)
                            ) { Text("السابق", fontSize = 11.sp) }
                            Button(
                                onClick = {
                                    when {
                                        !viewModel.isReading -> viewModel.startReading()
                                        viewModel.isSpeechPaused -> viewModel.resumeReading()
                                        else -> viewModel.pauseReading()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NewsLime, contentColor = NewsNavy)
                            ) {
                                Text(
                                    when {
                                        !viewModel.isReading -> "▶ قراءة"
                                        viewModel.isSpeechPaused -> "▶ استكمال"
                                        else -> "Ⅱ إيقاف مؤقت"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = viewModel::nextNews,
                                colors = ButtonDefaults.buttonColors(containerColor = NewsPanel, contentColor = NewsWhite)
                            ) { Text("التالي", fontSize = 11.sp) }
                            if (viewModel.isReading) {
                                Button(
                                    onClick = viewModel::stopReading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A2027), contentColor = Color.White)
                                ) { Text("إنهاء", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFF5A2027), shape = RoundedCornerShape(6.dp)) {
                        Text("عاجل", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("القاهرة الإخبارية", color = NewsMuted, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${viewModel.news.size} خبر", color = NewsMuted, fontSize = 12.sp)
                }
            }
            items(viewModel.news, key = { it.id }) { item ->
                NewsCard(item) { onOpen(item.url) }
            }
        }
    }
}

@Composable
private fun NewsCard(item: RadioNewsItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = item.url.isNotBlank(), onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = NewsPanel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(NewsLime, RoundedCornerShape(50)))
                Spacer(Modifier.width(7.dp))
                Text(item.source, color = NewsLime, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (item.publishedAt.isNotBlank()) {
                    Text(item.publishedAt.take(16).replace("T", " "), color = NewsMuted, fontSize = 10.sp)
                }
            }
            Text(item.title, color = NewsWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 26.sp)
            if (item.summary.isNotBlank()) {
                Text(item.summary, color = NewsMuted, fontSize = 13.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (item.url.isNotBlank()) Text("اقرأ التفاصيل  ←", color = NewsLime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MatchesFeed(viewModel: NewsMatchesViewModel, onOpen: (String) -> Unit) {
    when {
        viewModel.isLoadingMatches && viewModel.matches.isEmpty() -> LoadingState()
        viewModel.matchesError != null && viewModel.matches.isEmpty() -> ErrorState(viewModel.matchesError!!) {
            viewModel.refreshMatches()
        }
        viewModel.matches.isEmpty() -> EmptyState("لا توجد مباريات ظاهرة في مركز يلاكورة حاليًا")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("مركز المباريات", color = NewsWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${viewModel.matches.size} مباراة", color = NewsMuted, fontSize = 12.sp)
                }
            }
            items(viewModel.matches, key = { it.id }) { match ->
                MatchCard(match) { onOpen(match.url) }
            }
        }
    }
}

@Composable
private fun MatchCard(match: RadioMatchItem, onClick: () -> Unit) {
    val live = match.status.contains("الشوط") || match.status.contains("مباشر") ||
        match.status.contains("إضافي") || match.status.contains("ترجيح")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = NewsPanel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(match.competition, color = NewsMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Surface(color = if (live) Color(0xFF5A2027) else NewsPanelAlt, shape = RoundedCornerShape(6.dp)) {
                    Text(match.status, color = if (live) Color.White else NewsLime, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(match.home, color = NewsWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 2)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
                    Text("VS", color = NewsLime, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(match.time, color = NewsMuted, fontSize = 12.sp)
                }
                Text(match.away, color = NewsWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 2)
            }
            Text("تفاصيل المباراة  ←", color = NewsLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NewsLime)
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("تعذر تحميل البيانات", color = NewsWhite, fontWeight = FontWeight.Bold)
        Text(message, color = NewsMuted, textAlign = TextAlign.Center, fontSize = 12.sp)
        Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = NewsLime, contentColor = NewsNavy)) {
            Text("إعادة المحاولة")
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
        Text(message, color = NewsMuted, textAlign = TextAlign.Center)
    }
}
