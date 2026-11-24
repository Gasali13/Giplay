package com.personal.animeplayer.ui.compose.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.ui.compose.theme.AniPlayColors
import com.personal.animeplayer.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val EpisodeCardShape = RoundedCornerShape(12.dp)
private val EpisodeThumbShape = RoundedCornerShape(10.dp)
private val EpisodeBadgeShape = RoundedCornerShape(4.dp)

private val EpisodeDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeListItem(
    episode: Episode,
    isLastWatched: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    val thumbState by rememberEpisodeThumbnail(
        uri = episode.uri,
        knownDurationMs = episode.durationMs
    )
    val imageBitmap = thumbState.first
    val effectiveDuration = thumbState.second
    val isShort = effectiveDuration in 1 until 60_000L

    val subtitleText = remember(episode.dateAdded, episode.fileSize) {
        val parts = mutableListOf<String>()
        if (episode.dateAdded > 0L) {
            val timeMs = if (episode.dateAdded > 2_500_000_000_000L) {
                episode.dateAdded / 1000L
            } else {
                episode.dateAdded
            }
            EpisodeDateFormatter.get()?.let { formatter ->
                parts.add(formatter.format(Date(timeMs)))
            }
        }
        if (episode.fileSize > 0L) {
            parts.add(FileUtils.formatFileSize(episode.fileSize))
        }
        parts.joinToString(" • ")
    }

    val selectedBorderModifier = if (isSelectionMode && isSelected) {
        Modifier
            .background(AniPlayColors.CardSelectedBg, EpisodeCardShape)
            .border(1.5.dp, AniPlayColors.Primary, EpisodeCardShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(EpisodeCardShape)
            .then(selectedBorderModifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color(0x26FFFFFF)),
                onClick = {
                    if (!isSelectionMode) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    onClick()
                },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail Container (142x80dp, 10dp rounded squircle)
            Box(
                modifier = Modifier
                    .width(142.dp)
                    .height(80.dp)
                    .clip(EpisodeThumbShape)
                    .background(AniPlayColors.ThumbBg)
                    .border(0.75.dp, Color(0x24FFFFFF), EpisodeThumbShape)
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_arrow),
                        contentDescription = null,
                        tint = AniPlayColors.OnSurfaceMuted,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                    )
                }

                // Duration Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp, bottom = 5.dp)
                        .clip(EpisodeBadgeShape)
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (effectiveDuration > 0L) {
                            FileUtils.formatBadgeDuration(effectiveDuration)
                        } else {
                            "--.--"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 13.sp
                    )
                }

                // Watch Progress Bar
                if (!isShort && episode.progressPercentage > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(Color(0x4D000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(
                                    fraction = (episode.progressPercentage / 100f).coerceIn(0f, 1f)
                                )
                                .background(AniPlayColors.Secondary)
                        )
                    }
                }
            }

            // Information Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 4.dp)
            ) {
                Text(
                    text = episode.title,
                    color = if (isLastWatched) AniPlayColors.AppleBlue else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitleText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = subtitleText,
                        color = AniPlayColors.TextSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Trailing Control (Selection Checkbox or 3-Dot More Button)
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(38.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = AniPlayColors.Primary,
                        uncheckedColor = AniPlayColors.TextSecondary,
                        checkmarkColor = Color.White
                    )
                )
            } else {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = AniPlayColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
