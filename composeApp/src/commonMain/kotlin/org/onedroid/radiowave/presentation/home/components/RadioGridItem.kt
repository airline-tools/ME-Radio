package org.onedroid.radiowave.presentation.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import org.onedroid.radiowave.domain.Radio
import org.onedroid.radiowave.app.theme.Shapes
import org.onedroid.radiowave.app.theme.small
import org.onedroid.radiowave.app.theme.thin
import radiowave.composeapp.generated.resources.Res
import radiowave.composeapp.generated.resources.broken_image_radio

@Composable
fun RadioGridItem(
    radio: Radio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(targetValue = if (isHovered && radio.url.isNotBlank()) 1.05f else 1f)
    val playable = radio.url.isNotBlank()

    Surface(
        modifier = modifier
            .padding(thin)
            .clip(Shapes.small)
            .scale(scale)
            .clickable(
                enabled = playable,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource = interactionSource)
            .then(Modifier.fillMaxWidth()),
        tonalElevation = thin,
        shape = Shapes.small,
        color = if (playable) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(small).fillMaxWidth(),
        ) {
            radio.imgUrl?.let { imageUrl ->
                CustomAsyncImage(
                    imageUrl = imageUrl,
                    contentDescription = radio.name,
                    errorImage = painterResource(Res.drawable.broken_image_radio),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.small)
                )
            }

            Spacer(modifier = Modifier.height(small))

            Text(
                text = radio.name.trimStart(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val stationInfo = listOfNotNull(
                radio.frequency?.takeIf { it.isNotBlank() && it != "000" },
                radio.category?.takeIf { it.isNotBlank() }
            ).joinToString(" • ")

            Text(
                text = stationInfo.ifBlank { if (playable) "إذاعة مصرية" else "الرابط غير متاح حالياً" },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (playable) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
