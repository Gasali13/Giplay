package com.personal.animeplayer.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.animeplayer.R
import com.personal.animeplayer.ui.compose.theme.AniPlayColors
import com.personal.animeplayer.ui.compose.theme.IosMotion

@Composable
fun ScrollToTopFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdropState: LiquidGlassBackdropState? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(IosMotion.fadeIn) + scaleIn(initialScale = 0.86f, animationSpec = IosMotion.dialogEnterScale),
        exit = fadeOut(IosMotion.fadeOut) + scaleOut(targetScale = 0.9f, animationSpec = IosMotion.dialogExitScale),
        modifier = modifier
    ) {
        LiquidGlassCircleButton(
            iconRes = R.drawable.ic_arrow_upward,
            contentDescription = stringResource(R.string.cd_scroll_to_top),
            onClick = onClick,
            size = 48.dp,
            iconSize = 22.dp,
            iconTint = AniPlayColors.SkyBlue,
            backdropState = backdropState,
            tintColor = Color(0x540B1B30)
        )
    }
}
