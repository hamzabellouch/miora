package com.tkno.miora.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkno.miora.R
// Official Brand Color
val LinksBlue = Color(0xFF004AAD)

/**
 * Screen 1 Illustration: 1.svg Hero Vector Graphic
 * Spans full width from far right to far left.
 */
@Composable
fun WelcomeIllustration(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_onboarding_welcome_hero),
        contentDescription = null,
        modifier = modifier.fillMaxWidth(),
        contentScale = ContentScale.FillWidth
    )
}

/**
 * Screen 2 Illustration: 11.svg Vector Graphic (Wi-Fi Direct, Bluetooth & P2P Device Transfer)
 * Centered illustration above "Wi-Fi Direct & Bluetooth".
 */
@Composable
fun FeaturesIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_onboarding_features_hero),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Screen 3 Illustration: 3.svg Vector Graphic (Level Up Growth Arrow)
 * Centered illustration above "Level up your experience".
 */
@Composable
fun LevelUpIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_onboarding_level_up_hero),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Official Brand Badge - Launcher Icon Foreground
 */
@Composable
fun LinksBrandBadge(
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp
) {
    Image(
        painter = painterResource(id = R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier.size(iconSize)
    )
}

