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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.onedroid.radiowave.domain.Radio

@Composable
fun RadioGridItem(
    radio: Radio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playable = radio.url.isNotBlank()
    val gold = Color(0xFFD8B46C)
    val cardShape = RoundedCornerShape(20.dp)

    Surface(
        modifier = modifier
            .padding(5.dp)
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(enabled = playable, onClick = onClick),
        shape = cardShape,
        color = Color(0xFF122139),
        border = BorderStroke(1.dp, gold.copy(alpha = if (playable) 0.32f else 0.12f)),
        tonalElevation = 0.dp,
        shadowElevation = 5.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF182B47), Color(0xFF101D31), Color(0xFF111A2A))
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFE0C17E), Color(0xFF9B753A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            radio.frequency?.contains("AM", true) == true -> "AM"
                            radio.frequency?.contains("FM", true) == true -> "FM"
                            else -> "♫"
                        },
                        color = Color(0xFF172238),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = if (playable) "● LIVE" else "غير متاح",
                    color = if (playable) Color(0xFF78D6A0) else Color(0xFF8993A2),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = radio.name.trim(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF5F0E6)
                ),
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.size(1.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = radio.frequency?.takeIf { it.isNotBlank() && it != "000" } ?: "إذاعة مصرية",
                    style = MaterialTheme.typography.labelMedium,
                    color = gold,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = radio.category.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB7C2D2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
