package com.itantra.app.feature.emergency

import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OffWhite = Color(0xFFF8FAF7)
private val DeepDarkGreen = Color(0xFF193D25)
private val SecondaryText = Color(0xFF71807A)
private val SOSRed = Color(0xFFE53945)

@Composable
fun SOS(
    onBack: () -> Unit = {}
) {
    var sosActivated by remember {
        mutableStateOf(false)
    }

    val transition = rememberInfiniteTransition(
        label = "sosPulse"
    )

    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (sosActivated) 1.10f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (sosActivated) 650 else 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = if (sosActivated) 0.40f else 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (sosActivated) 650 else 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val backgroundColor =
        if (sosActivated) SOSRed else OffWhite

    val buttonColor =
        if (sosActivated) Color.White else SOSRed

    val buttonTextColor =
        if (sosActivated) SOSRed else Color.White

    val textColor =
        if (sosActivated) Color.White else DeepDarkGreen

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 24.dp,
                    vertical = 20.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = textColor,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(8.dp)
            )

            Text(
                text = "EMERGENCY",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
        }

        // Main content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // SOS button + glow
                Box(
                    modifier = Modifier.size(270.dp),
                    contentAlignment = Alignment.Center
                ) {

                    // Outer glow
                    Box(
                        modifier = Modifier
                            .size(270.dp)
                            .scale(pulseScale)
                            .alpha(glowAlpha)
                            .background(
                                color = if (sosActivated) {
                                    Color.White
                                } else {
                                    SOSRed
                                },
                                shape = CircleShape
                            )
                    )

                    // Middle glow
                    Box(
                        modifier = Modifier
                            .size(225.dp)
                            .scale(pulseScale)
                            .alpha(glowAlpha + 0.08f)
                            .background(
                                color = if (sosActivated) {
                                    Color.White
                                } else {
                                    SOSRed
                                },
                                shape = CircleShape
                            )
                    )

                    // Main SOS button
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(buttonColor)
                            .clickable {
                                sosActivated = !sosActivated
                            },
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = "SOS",
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = buttonTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(55.dp))

                Text(
                    text = if (sosActivated) {
                        "SOS ACTIVATED"
                    } else {
                        "Emergency assistance"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (sosActivated) {
                        "Emergency alert sent to your team.\nPlease stay calm and wait for assistance."
                    } else {
                        "Press the SOS button to alert your\nteam and share your current location."
                    },
                    fontSize = 13.sp,
                    color = if (sosActivated) {
                        Color.White.copy(alpha = 0.85f)
                    } else {
                        SecondaryText
                    },
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun SOSPreview() {
    SOS()
}