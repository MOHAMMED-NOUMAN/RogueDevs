package com.itantra.app.feature.nearby.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Search
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// -----------------------------------------------------------------------------
// Colors
// -----------------------------------------------------------------------------

private val AppBackground = Color(0xFFF7FAF8)
private val DeepGreen = Color(0xFF123D32)
private val PrimaryGreen = Color(0xFF18B878)
private val Cyan = Color(0xFF22C7C7)
private val SoftGreen = Color(0xFFE5F6EE)
private val CardBackground = Color.White
private val BorderColor = Color(0xFFE2EAE6)
private val PrimaryText = Color(0xFF17231F)
private val SecondaryText = Color(0xFF71807A)
private val MutedText = Color(0xFF9AA6A1)

// -----------------------------------------------------------------------------
// Main Screen
// -----------------------------------------------------------------------------

@Composable
fun NearbyDevicesScreen(
    onPairViaQr: () -> Unit = {},
    onConnect: (String) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(scrollState)
            .padding(
                start = 20.dp,
                top = 18.dp,
                end = 20.dp,
                bottom = 120.dp
            )    ) {

        // Header
        NearbyHeader()

        Spacer(modifier = Modifier.height(26.dp))

        // Bluetooth scanning animation
        BluetoothScanner()

        Spacer(modifier = Modifier.height(26.dp))

        // Found devices label
        Text(
            text = "4 DEVICES FOUND",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            color = SecondaryText
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Device list
        DeviceCard(
            initial = "R",
            name = "Rhea's Phone",
            distance = "8 m away",
            signal = SignalStrength.STRONG,
            avatarColor = PrimaryGreen,
            onConnect = { onConnect("Rhea's Phone") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        DeviceCard(
            initial = "Z",
            name = "Zain — Relief Camp",
            distance = "22 m away",
            signal = SignalStrength.STRONG,
            avatarColor = PrimaryGreen,
            onConnect = { onConnect("Zain — Relief Camp") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        DeviceCard(
            initial = "U",
            name = "Unknown Device",
            distance = "40 m away",
            signal = SignalStrength.WEAK,
            avatarColor = Color(0xFF8A9A94),
            onConnect = { onConnect("Unknown Device") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        DeviceCard(
            initial = "N",
            name = "NDRF Unit 07",
            distance = "55 m away",
            signal = SignalStrength.MEDIUM,
            avatarColor = Cyan,
            onConnect = { onConnect("NDRF Unit 07") }
        )

        Spacer(modifier = Modifier.height(26.dp))

        // QR fallback
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPairViaQr() }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Can't see a device? ",
                fontSize = 13.sp,
                color = SecondaryText
            )

            Text(
                text = "Pair via QR instead ›",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryGreen,
                modifier = Modifier.clickable {
                    onPairViaQr()
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// -----------------------------------------------------------------------------
// Header
// -----------------------------------------------------------------------------

@Composable
private fun NearbyHeader() {
    Column {
        Text(
            text = "Nearby Devices",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Bluetooth discovery",
            fontSize = 14.sp,
            color = SecondaryText
        )
    }
}

// -----------------------------------------------------------------------------
// Bluetooth Scanner
// -----------------------------------------------------------------------------

@Composable
private fun BluetoothScanner() {

    val infiniteTransition = rememberInfiniteTransition(
        label = "bluetoothScanner"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center
        ) {

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {

                val center = Offset(
                    x = size.width / 2,
                    y = size.height / 2
                )

                val maxRadius = size.minDimension / 2

                // Outer scanning ring
                drawCircle(
                    color = PrimaryGreen.copy(alpha = 0.20f),
                    radius = maxRadius * pulse,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx()
                    )
                )

                // Middle ring
                drawCircle(
                    color = PrimaryGreen.copy(alpha = 0.30f),
                    radius = maxRadius * 0.73f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx()
                    )
                )

                // Inner ring
                drawCircle(
                    color = Cyan.copy(alpha = 0.35f),
                    radius = maxRadius * 0.47f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx()
                    )
                )
            }

            // Center button
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = SoftGreen,
                shadowElevation = 3.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = PrimaryGreen
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Scan",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Scanning for devices...",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = SecondaryText
        )
    }
}

// -----------------------------------------------------------------------------
// Device Card
// -----------------------------------------------------------------------------

private enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK
}

@Composable
private fun DeviceCard(
    initial: String,
    name: String,
    distance: String,
    signal: SignalStrength,
    avatarColor: Color,
    onConnect: () -> Unit
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        color = CardBackground,
        shadowElevation = 1.dp
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 13.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.10f))
                    .border(
                        width = 1.5.dp,
                        color = avatarColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = avatarColor
                )
            }

            Spacer(modifier = Modifier.width(11.dp))

            // Device information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = distance,
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            }

            // Signal indicator
            SignalIndicator(signal)
            Spacer(modifier = Modifier.width(10.dp))

            // Connect button
            Button(
                onClick = onConnect,
                modifier = Modifier
                    .height(38.dp)
                    .width(98.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    contentColor = Color.White
                ),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text(
                    text = "Connect",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Signal Bars
// -----------------------------------------------------------------------------

@Composable
private fun SignalIndicator(
    strength: SignalStrength
) {

    val activeColor = when (strength) {
        SignalStrength.STRONG -> PrimaryGreen
        SignalStrength.MEDIUM -> Cyan
        SignalStrength.WEAK -> MutedText
    }

    Row(
        modifier = Modifier.height(25.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {

        SignalBar(
            height = 7.dp,
            active = true,
            color = activeColor
        )

        SignalBar(
            height = 11.dp,
            active = strength != SignalStrength.WEAK,
            color = activeColor
        )

        SignalBar(
            height = 16.dp,
            active = strength != SignalStrength.WEAK,
            color = activeColor
        )

        SignalBar(
            height = 21.dp,
            active = strength == SignalStrength.STRONG,
            color = activeColor
        )
    }
}

@Composable
private fun SignalBar(
    height: androidx.compose.ui.unit.Dp,
    active: Boolean,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .background(
                if (active) color else color.copy(alpha = 0.18f)
            )
    )
}

// -----------------------------------------------------------------------------
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NearbyDevicesScreenPreview() {
    MaterialTheme {
        NearbyDevicesScreen()
    }
}// -----------------------------------------------------------------------------

@Composable
private fun NearbyDevicesPreview() {
    MaterialTheme {
        NearbyDevicesScreen()
    }
}