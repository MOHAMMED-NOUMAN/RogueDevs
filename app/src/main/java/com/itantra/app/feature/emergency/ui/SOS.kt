package com.itantra.app.feature.emergency

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itantra.app.core.messaging.SosLocation
import com.itantra.app.core.sos.OutgoingSos
import com.itantra.app.feature.emergency.viewmodel.SosViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val OffWhite = Color(0xFFF8FAF7)
private val DeepDarkGreen = Color(0xFF193D25)
private val SecondaryText = Color(0xFF71807A)
private val SOSRed = Color(0xFFE53945)

/** How long SOS must be held, so it can't go off by accident. */
private const val HOLD_MS = 3_000

@Composable
fun SOS(
    onBack: () -> Unit = {},
    viewModel: SosViewModel = hiltViewModel()
) {
    val outgoing by viewModel.outgoing.collectAsState()
    val current = outgoing
    val active = current?.active == true
    val acknowledged = current?.status == OutgoingSos.Status.ACKNOWLEDGED
    var holding by remember { mutableStateOf(false) }

    // Location goes with the SOS; ask once when the screen opens (not mid-hold).
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    LaunchedEffect(Unit) {
        if (!viewModel.hasLocationPermission()) {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "sosPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.10f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 650 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = if (active) 0.40f else 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 650 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val backgroundColor = when {
        acknowledged -> DeepDarkGreen
        active -> SOSRed
        else -> OffWhite
    }
    val onBackground = if (active || acknowledged) Color.White else DeepDarkGreen
    val buttonColor = if (active || acknowledged) Color.White else SOSRed
    val buttonTextColor = when {
        acknowledged -> DeepDarkGreen
        active -> SOSRed
        else -> Color.White
    }

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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = onBackground,
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
                color = onBackground
            )
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = onBackground,
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
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                HoldToSendButton(
                    enabled = !active && !acknowledged,
                    pulseScale = pulseScale,
                    glowAlpha = glowAlpha,
                    glowColor = if (active || acknowledged) Color.White else SOSRed,
                    buttonColor = buttonColor,
                    textColor = buttonTextColor,
                    onHoldChange = { holding = it },
                    onTrigger = viewModel::trigger
                )

                Spacer(modifier = Modifier.height(40.dp))

                val (title, body) = statusText(current, holding)
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = body,
                    fontSize = 13.sp,
                    color = if (active || acknowledged) Color.White.copy(alpha = 0.88f) else SecondaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                if (current != null && (active || acknowledged)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LocationLine(current.location, current.locating, onBackground)
                }

                Spacer(modifier = Modifier.height(28.dp))

                when {
                    active -> SosButton("Cancel SOS", onBackground, onClick = viewModel::cancel)
                    current != null -> SosButton("Done", onBackground, onClick = viewModel::clearOutgoing)
                }
            }
        }
    }
}

/** Title and explanation for the current SOS state. */
private fun statusText(sos: OutgoingSos?, holding: Boolean): Pair<String, String> = when {
    sos == null && holding -> "Keep holding…" to "The SOS sends when the ring is full."
    sos == null -> "Emergency assistance" to
        "Hold the SOS button for 3 seconds. Your name and location go to your paired teammate, " +
        "and keep going until they respond."
    else -> when (sos.status) {
        OutgoingSos.Status.SENDING -> "SENDING SOS" to "Reaching your teammate's phone. Keep iTantra open."
        OutgoingSos.Status.LINK_OFF -> "SOS WAITING FOR THE LINK" to
            "Your teammate's phone isn't connected. The SOS sends by itself as soon as it is."
        OutgoingSos.Status.DELIVERED -> "SOS DELIVERED" to
            "Your teammate's phone is ringing. Waiting for them to respond."
        OutgoingSos.Status.ACKNOWLEDGED -> "HELP IS ON THE WAY" to
            "${sos.responder?.ifBlank { null } ?: "Your teammate"} is coming."
        OutgoingSos.Status.NOT_PAIRED -> "SOS NOT SENT" to "Pair a teammate first in the Pair tab."
        OutgoingSos.Status.CANCELLED -> "SOS CANCELLED" to "Your teammate has been told."
    }
}

/**
 * The SOS circle. Pressing starts a ring that fills over [HOLD_MS]; letting go early resets it,
 * and a full ring sends the SOS.
 */
@Composable
private fun HoldToSendButton(
    enabled: Boolean,
    pulseScale: Float,
    glowAlpha: Float,
    glowColor: Color,
    buttonColor: Color,
    textColor: Color,
    onHoldChange: (Boolean) -> Unit,
    onTrigger: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.size(270.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer and middle glow
        Box(
            modifier = Modifier
                .size(270.dp)
                .scale(pulseScale)
                .alpha(glowAlpha)
                .background(glowColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(225.dp)
                .scale(pulseScale)
                .alpha(glowAlpha + 0.08f)
                .background(glowColor, CircleShape)
        )

        // Hold progress ring
        Canvas(modifier = Modifier.size(206.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            if (progress.value > 0f) {
                drawArc(
                    color = SOSRed.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = SOSRed,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        // Main SOS button
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(buttonColor)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = {
                        onHoldChange(true)
                        val fill = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        }
                        val released = withTimeoutOrNull(HOLD_MS.toLong()) { tryAwaitRelease() }
                        fill.cancel()
                        if (released == null) onTrigger()
                        scope.launch { progress.snapTo(0f) }
                        onHoldChange(false)
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SOS",
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun LocationLine(location: SosLocation?, locating: Boolean, color: Color) {
    val text = when {
        location != null -> "Sharing location %.5f, %.5f (±%d m)".format(
            location.latitude, location.longitude, location.accuracyMeters
        ) + if (locating) "\nGetting a more accurate fix…" else ""
        locating -> "Getting your location…"
        else -> "Location not available. Allow location access and turn on location."
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.padding(start = 6.dp))
        Text(text = text, fontSize = 12.sp, color = color, lineHeight = 18.sp)
    }
}

@Composable
private fun SosButton(text: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, color),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
