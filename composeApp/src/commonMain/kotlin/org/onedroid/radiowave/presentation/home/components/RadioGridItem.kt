package org.onedroid.radiowave.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import org.onedroid.radiowave.domain.Radio

@Composable
fun RadioGridItem(
    radio: Radio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canTune = radio.playableUrl.isNotBlank() || !radio.masperoName.isNullOrBlank()
    val hasUrl = radio.playableUrl.isNotBlank()
    val gold = Color(0xFFC5A059)
    val shape = RoundedCornerShape(13.dp)

    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = canTune, onClick = onClick),
        shape = shape,
        color = Color(0xFF101D30),
        border = BorderStroke(1.dp, gold.copy(alpha = if (canTune) .28f else .10f)),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF172A44), Color(0xFF0E1A2C))
                    )
                )
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(39.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFA83A43), Color(0xFF5B1921))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color(0xFFFFE7B0), fontSize = 13.sp, fontWeight = FontWeight.Black)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    radio.name.trim(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF5F0E6)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        radio.frequency?.takeIf { it.isNotBlank() && it != "000" },
                        radio.category?.takeIf { it.isNotBlank() }
                    ).joinToString("  •  "),
                    color = Color(0xFFB7C2D2),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when {
                        hasUrl -> "رابط متاح"
                        canTune -> "تحديث عند التشغيل"
                        else -> "غير متاح"
                    },
                    color = when {
                        hasUrl -> Color(0xFF79D6A1)
                        canTune -> gold
                        else -> Color(0xFF8993A2)
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    radio.frequency?.substringBefore(" ")?.takeIf { it.isNotBlank() && it != "000" } ?: "LIVE RADIO",
                    color = gold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
