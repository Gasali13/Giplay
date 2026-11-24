package com.personal.animeplayer.ui.compose.components

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.animeplayer.R
import com.personal.animeplayer.ui.compose.theme.IosMotion
import com.personal.animeplayer.ui.library.LibraryTab

private val TabBarShape = RoundedCornerShape(22.dp)
private val TabPillShape = RoundedCornerShape(19.dp)

@Composable
fun SegmentedTabSwitcher(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    onTabReselected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
    backdropState: LiquidGlassBackdropState? = null
) {
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
            .height(44.dp)
            .liquidGlass(
                backdropState = backdropState,
                cornerRadius = 22.dp,
                style = LiquidGlassStyle.TabBar
            )
            .clip(TabBarShape)
            .padding(3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val indicatorProgress by animateFloatAsState(
                targetValue = if (selectedTab == LibraryTab.ALL_VIDEOS) 0f else 1f,
                animationSpec = IosMotion.indicator,
                label = "segmentedTabIndicatorProgress"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = size.width * indicatorProgress
                    }
                    .liquidGlassSurface(
                        cornerRadius = 19.dp,
                        tintColor = Color(0x42FFFFFF),
                        highlightStrength = 1.0f
                    )
                    .clip(TabPillShape)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                SegmentedTabItem(
                    label = "Semua Video",
                    iconRes = R.drawable.ic_play_arrow,
                    selected = selectedTab == LibraryTab.ALL_VIDEOS,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (selectedTab == LibraryTab.ALL_VIDEOS) {
                            onTabReselected(LibraryTab.ALL_VIDEOS)
                        } else {
                            onTabSelected(LibraryTab.ALL_VIDEOS)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                SegmentedTabItem(
                    label = "Folder",
                    iconRes = R.drawable.ic_folder,
                    selected = selectedTab == LibraryTab.FOLDERS,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (selectedTab == LibraryTab.FOLDERS) {
                            onTabReselected(LibraryTab.FOLDERS)
                        } else {
                            onTabSelected(LibraryTab.FOLDERS)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun SegmentedTabItem(
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0xB8D1D8E6),
        animationSpec = IosMotion.color,
        label = "segmentedTabContentColor"
    )

    Row(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
