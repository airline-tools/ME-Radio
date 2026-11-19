package org.onedroid.radiowave.presentation.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.onedroid.radiowave.domain.Radio

private val RadioNavy = Color(0xFF0B1527)
private val RadioNavyLight = Color(0xFF142641)
private val RadioGold = Color(0xFFC5A059)
private val RadioGoldLight = Color(0xFFE8CC8B)
private val RadioRed = Color(0xFFFF303B)

@Composable
fun BookingwepRadioConsole(
    radio: Radio,
    stations: List<Radio>,
    savedStations: List<Radio>,
    isSaved: Boolean,
    isPlaying: Boolean,
    isTuning: Boolean,
    playerMessage: String?,
    isAutoSearch: Boolean,
    categories: List<String>,
    selectedCategory: String,
    onCategoryClick: (String) -> Unit,
    onFavoritesClick: () -> Unit,
    onCloseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onAutoSearchClick: () -> Unit,
    onSaveClick: () -> Unit,
    onStationFavoriteClick: (Radio) -> Unit,
    onStationClick: (Radio) -> Unit,
    onShareClick: () -> Unit,
    onWebpageClick: () -> Unit,
    statusContent: @Composable () -> Unit
) {
    var showFavoritesOnly by remember { mutableStateOf(false) }
    val visibleStations = if (showFavoritesOnly) {
        stations.filter { station -> savedStations.any { it.id == station.id } }
    } else stations
    val frequency = radio.frequency.orEmpty().substringBefore(" ").takeIf { it.isNotBlank() && it != "000" } ?: "---"
    val band = if (radio.frequency.orEmpty().contains("AM", true)) "AM" else "FM"
    val playable = radio.playableUrl.isNotBlank() || !radio.masperoName.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadioNavy)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(RadioNavyLight, Color(0xFF091222)))
                )
                .padding(horizontal = 17.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("◉", color = RadioGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(9.dp))
                    Text("الراديو", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                modifier = Modifier.size(36.dp)
                    .tvFocusOutline(CircleShape)
                    .clickable(onClick = onCloseClick),
                shape = CircleShape,
                color = Color(0xFF1A2A40),
                border = androidx.compose.foundation.BorderStroke(1.dp, RadioGold.copy(alpha = .35f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", color = RadioGoldLight, fontSize = 23.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF1B2A3F), RadioNavy, RadioNavy)
                    )
                )
                .padding(horizontal = 17.dp, vertical = 15.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF090D12), Color(0xFF030609))
                        )
                    )
                    .padding(horizontal = 11.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier
                            .tvFocusOutline(RoundedCornerShape(5.dp))
                            .clickable(onClick = {
                            showFavoritesOnly = !showFavoritesOnly
                            onFavoritesClick()
                        }),
                        shape = RoundedCornerShape(5.dp),
                        color = if (showFavoritesOnly) RadioGold else RadioGold.copy(alpha = .09f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RadioGold.copy(alpha = .5f))
                    ) {
                        Text(
                            "★ المفضلة",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = if (showFavoritesOnly) RadioNavy else RadioGoldLight,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(if (isPlaying) RadioRed else Color(0xFF687386))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            when {
                                isTuning -> "TUNING"
                                isPlaying -> "LIVE"
                                playable -> "READY"
                                else -> "NO LINK"
                            },
                            color = if (isPlaying) RadioRed else Color(0xFF9BA5B2),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 23.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        radio.name,
                        color = RadioRed,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            shadow = Shadow(RadioRed.copy(alpha = .8f), Offset.Zero, 12f)
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (frequency.all { it.isDigit() || it == '.' || it == '-' }) {
                            SevenSegmentFrequency(frequency)
                        } else {
                            Text(
                                frequency,
                                color = RadioRed,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    shadow = Shadow(RadioRed.copy(alpha = .9f), Offset.Zero, 16f)
                                )
                            )
                        }
                        Spacer(Modifier.width(9.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = RadioGold.copy(alpha = .09f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RadioGold.copy(alpha = .45f))
                        ) {
                            Text(
                                band,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                color = RadioGoldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Text(
                        playerMessage ?: if (isTuning) "جارٍ الاتصال بالبث المباشر" else if (isPlaying) "ON AIR • بث مباشر" else "اختر محطة للاستماع",
                        color = if (playerMessage != null) RadioGoldLight else Color(0xFF8D98A8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 112.dp)
                ) {
                    Spacer(
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(RadioGold.copy(alpha = .2f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioControl("|◀", onPreviousClick)
                        RadioControl(if (isPlaying) "Ⅱ" else "▶", onPlayPauseClick, main = true)
                        RadioControl("▶|", onNextClick)
                        RadioControl(if (isAutoSearch) "■" else "AUTO", onAutoSearchClick, active = isAutoSearch, compact = true)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
                statusContent()
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSaveClick) {
                    Text(if (isSaved) "★ محفوظة" else "☆ أضف للمفضلة", color = RadioGoldLight, fontSize = 11.sp)
                }
                Row {
                    TextButton(onClick = onShareClick) {
                        Text("مشاركة", color = Color(0xFFB7C2D2), fontSize = 11.sp)
                    }
                    TextButton(onClick = onWebpageClick) {
                        Text("الموقع", color = Color(0xFFB7C2D2), fontSize = 11.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF101D30))
                .padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("المحطات", color = RadioGoldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                if (showFavoritesOnly) "المفضلة • ${visibleStations.size}" else "جميع الإذاعات • ${visibleStations.size}",
                color = Color(0xFF8C98A9),
                fontSize = 9.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { category ->
                val selected = selectedCategory == category
                Surface(
                    modifier = Modifier
                        .tvFocusOutline(RoundedCornerShape(7.dp))
                        .clickable { onCategoryClick(category) },
                    shape = RoundedCornerShape(7.dp),
                    color = if (selected) RadioGold else Color(0xFF17263B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RadioGold.copy(alpha = if (selected) 1f else .18f))
                ) {
                    Text(
                        category,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (selected) RadioNavy else Color(0xFFD8E0EA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)
        ) {
            items(visibleStations, key = { it.id }) { station ->
                val isStationSaved = savedStations.any { it.id == station.id }
                val canTune = station.playableUrl.isNotBlank() || !station.masperoName.isNullOrBlank()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .tvFocusOutline(RoundedCornerShape(10.dp))
                        .clickable(enabled = canTune) { onStationClick(station) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (station.id == radio.id) Color(0xFF1C304B) else Color(0xFF111F33),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (station.id == radio.id) RadioGold.copy(alpha = .6f) else Color.White.copy(alpha = .04f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (canTune) RadioGold else Color(0xFF687386))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                station.name,
                                color = Color(0xFFF5F0E6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOfNotNull(
                                    station.frequency?.takeIf { it.isNotBlank() && it != "000" },
                                    station.category?.takeIf { it.isNotBlank() }
                                ).joinToString("  •  "),
                                color = RadioGoldLight.copy(alpha = .8f),
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                        TextButton(onClick = { onStationFavoriteClick(station) }) {
                            Text(if (isStationSaved) "★" else "☆", color = if (isStationSaved) RadioGold else Color(0xFF8B95A4), fontSize = 17.sp)
                        }
                        Text(
                            if (station.id == radio.id) "ON AIR" else if (station.url.isNotBlank()) "READY" else if (canTune) "TUNE" else "—",
                            color = if (station.id == radio.id && isPlaying) RadioRed else Color(0xFF8C98A9),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioControl(
    label: String,
    onClick: () -> Unit,
    main: Boolean = false,
    active: Boolean = false,
    compact: Boolean = false
) {
    val background = when {
        main -> Brush.verticalGradient(listOf(Color(0xFFA83A43), Color(0xFF5B1921)))
        active -> Brush.verticalGradient(listOf(Color(0xFFB93642), Color(0xFF761D27)))
        else -> Brush.verticalGradient(listOf(Color(0xFF343B46), Color(0xFF1A2028)))
    }
    Surface(
        modifier = Modifier
            .size(width = if (compact) 43.dp else if (main) 43.dp else 37.dp, height = if (main) 43.dp else 37.dp)
            .tvFocusOutline(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (main || active) Color(0xFFBD5158) else Color(0xFF4B515B)),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.background(background).padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (main || active) Color(0xFFFFE7B0) else RadioGoldLight,
                fontSize = if (compact) 9.sp else if (main) 17.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}


@Composable
private fun SevenSegmentFrequency(value: String) {
    val patterns = mapOf(
        '0' to setOf("a", "b", "c", "d", "e", "f"),
        '1' to setOf("b", "c"),
        '2' to setOf("a", "b", "g", "e", "d"),
        '3' to setOf("a", "b", "g", "c", "d"),
        '4' to setOf("f", "g", "b", "c"),
        '5' to setOf("a", "f", "g", "c", "d"),
        '6' to setOf("a", "f", "g", "e", "c", "d"),
        '7' to setOf("a", "b", "c"),
        '8' to setOf("a", "b", "c", "d", "e", "f", "g"),
        '9' to setOf("a", "b", "c", "d", "f", "g"),
        '-' to setOf("g")
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        value.forEach { character ->
            if (character == '.') {
                Box(
                    modifier = Modifier
                        .padding(start = 1.dp, end = 2.dp, bottom = 5.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(RadioRed)
                )
            } else {
                val activeSegments = patterns[character] ?: emptySet()
                Canvas(modifier = Modifier.size(width = 25.dp, height = 48.dp)) {
                    val w = size.width
                    val h = size.height
                    val t = w * .17f
                    val horizontalHeight = t * .78f
                    val verticalWidth = t * .78f
                    val verticalHeight = (h - 3 * t) / 2
                    val segments = listOf(
                        Triple("a", Offset(t, 0f), Size(w - 2 * t, horizontalHeight)),
                        Triple("g", Offset(t, (h - horizontalHeight) / 2), Size(w - 2 * t, horizontalHeight)),
                        Triple("d", Offset(t, h - horizontalHeight), Size(w - 2 * t, horizontalHeight)),
                        Triple("f", Offset(0f, t * .55f), Size(verticalWidth, verticalHeight)),
                        Triple("b", Offset(w - verticalWidth, t * .55f), Size(verticalWidth, verticalHeight)),
                        Triple("e", Offset(0f, h / 2 + t * .05f), Size(verticalWidth, verticalHeight)),
                        Triple("c", Offset(w - verticalWidth, h / 2 + t * .05f), Size(verticalWidth, verticalHeight))
                    )
                    segments.forEach { (name, offset, segmentSize) ->
                        val enabled = name in activeSegments
                        val segmentColor = if (enabled) RadioRed else RadioRed.copy(alpha = .09f)
                        if (enabled) {
                            drawRoundRect(
                                color = segmentColor.copy(alpha = .22f),
                                topLeft = Offset(offset.x - 1f, offset.y - 1f),
                                size = Size(segmentSize.width + 2f, segmentSize.height + 2f),
                                cornerRadius = CornerRadius(t / 2)
                            )
                        }
                        drawRoundRect(
                            color = segmentColor,
                            topLeft = offset,
                            size = segmentSize,
                            cornerRadius = CornerRadius(t / 2)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Visible gold focus ring for Android TV remotes and hardware-key navigation.
 * Clickable supplies the actual focus target; this modifier only highlights it.
 */
@Composable
private fun Modifier.tvFocusOutline(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(
            if (focused) BorderStroke(2.dp, RadioGold)
            else BorderStroke(0.dp, Color.Transparent),
            shape
        )
}
