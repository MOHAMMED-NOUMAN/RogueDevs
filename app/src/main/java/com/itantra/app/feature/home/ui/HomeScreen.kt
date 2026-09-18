package com.itantra.app.feature.home.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import com.itantra.app.feature.communication.ui.PairingScreen
import com.itantra.app.feature.emergency.SOS
import com.itantra.app.feature.settings.ui.SettingsScreen
import com.itantra.app.feature.nearby.ui.NearbyDevicesScreen
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.itantra.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.itantra.app.feature.communication.viewmodel.CommunicationUiState
import com.itantra.app.feature.communication.viewmodel.CommunicationViewModel
import com.itantra.app.feature.location.ui.LocationScreen
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen

@Composable
fun HomeScreen(
    communicationViewModel: CommunicationViewModel = hiltViewModel(),
    onNavigateToTab: (Int) -> Unit = {}
) {
    val uiState by communicationViewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    // The bar sits below the content (not floating over it), so no screen needs to leave
    // blank room at the bottom. Each screen pads itself for the status bar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                1 -> {
                    // Full Team Map Screen
                    LocationScreen()

                }
                2 -> {
                    NearbyDevicesScreen(
                        onPairViaQr = {
                            selectedTab = 3
                        },
                        onConnect = { deviceName ->
                            // Bluetooth connection logic will be added later
                        }
                    )
                }
                3 -> {
                    PairingScreen()
                }
                4 -> {
                    SettingsScreen()
                }
                5 -> {
                    SOS(
                        onBack = {
                            selectedTab = 0
                        }
                    )
                }
                else -> {
                    // Main Home Dashboard Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Top Header with SOS Button (Pulse Glow Animation)
                        HomeHeaderSection(
                            onSOSClick = {
                                selectedTab = 5
                            }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 2. Team Map Card
                        HomeMapCardSection(onExpandMap = { selectedTab = 1 })

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Priority Alert Card
                        HomePriorityAlertSection()

                        Spacer(modifier = Modifier.height(24.dp))

                        // 4. Hold To Talk Section (Smooth Scale & Ripple Animation)
                        HomeHoldToTalkSection(
                            uiState = uiState,
                            onPressed = { communicationViewModel.onPushToTalkPressed() },
                            onReleased = { communicationViewModel.onPushToTalkReleased() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

        }

        // 5. Bottom navigation (hidden on the full-screen SOS page)
        if (selectedTab != 5) {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { tabIndex ->
                    selectedTab = tabIndex
                    onNavigateToTab(tabIndex)
                }
            )
        }
    }
}

/**
 * 1. Top Header Component with Continuous SOS Pulse Animation
 */
@Composable
private fun HomeHeaderSection(
    onSOSClick: () -> Unit
) {
    // SOS Button Continuous Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "SosPulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SosPulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SosPulseScale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Peer Status
        Column {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "iTantra",
                modifier = Modifier
                    .width(120.dp)
                    .height(110.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "4 Peers Online  •  Channel 01",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
            }
        }

        // Floating Glowing Red SOS Button with Continuous Pulse Effect
        Box(contentAlignment = Alignment.Center) {
            // Animated Pulse Glow Ring Behind SOS Button
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFD32F2F).copy(alpha = pulseAlpha * 0.35f))
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFD32F2F),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .clickable {
                        onSOSClick()                    }
                    .border(2.dp, Color(0xFFFFCDD2), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFFD32F2F))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "SOS",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * 2. Map Card Section (Inspired by Image 2 clean style)
 */
@Composable
private fun HomeMapCardSection(
    onExpandMap: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clickable { onExpandMap() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Simulated Map Background with subtle grid roads
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridColor = Color(0xFFE8ECE9)
                val strokeWidth = 3f

                // Draw map route lines
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height * 0.3f),
                    end = Offset(size.width, size.height * 0.4f),
                    strokeWidth = strokeWidth * 2
                )
                drawLine(
                    color = gridColor,
                    start = Offset(size.width * 0.35f, 0f),
                    end = Offset(size.width * 0.4f, size.height),
                    strokeWidth = strokeWidth * 2
                )
                drawLine(
                    color = gridColor,
                    start = Offset(size.width * 0.7f, 0f),
                    end = Offset(size.width * 0.65f, size.height),
                    strokeWidth = strokeWidth * 1.5f
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height * 0.75f),
                    end = Offset(size.width, size.height * 0.7f),
                    strokeWidth = strokeWidth * 2
                )
            }

            // Map Overlay Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(SoftLightGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Team Map • 4 nearby",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepDarkGreen
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onExpandMap() }
                ) {
                    Text(
                        text = "Expand →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepDarkGreen
                    )
                }
            }

            // User Markers on Map (Inspired by Image 2 clean circular avatar markers)
            // Marker 1: Alex
            MapUserMarker(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 40.dp, y = (-10).dp),
                initials = "AK",
                name = "Alex (200m)",
                isSelf = false
            )

            // Marker 2: Rohan
            MapUserMarker(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 10.dp, y = 20.dp),
                initials = "YOU",
                name = "You",
                isSelf = true
            )

            // Marker 3: Saman
            MapUserMarker(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-45).dp, y = (-25).dp),
                initials = "SK",
                name = "Saman (1.1km)",
                isSelf = false
            )

            // Compass / My Location Action Button inside Map
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, GrayBorder, CircleShape)
                    .clickable { /* Re-center map */ },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    drawCircle(color = DeepDarkGreen, radius = size.width / 4, style = Stroke(width = 3f))
                    drawCircle(color = DeepDarkGreen, radius = size.width / 8)
                }
            }
        }
    }
}

/**
 * Clean User Marker on Map Card with Pulse Ring
 */
@Composable
private fun MapUserMarker(
    modifier: Modifier = Modifier,
    initials: String,
    name: String,
    isSelf: Boolean
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Pulse Glow Ring
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelf) DeepDarkGreen.copy(alpha = 0.2f) else SoftLightGreen.copy(alpha = 0.4f))
            )
            // Inner Avatar
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelf) DeepDarkGreen else Color.White)
                    .border(2.dp, if (isSelf) SoftLightGreen else DeepDarkGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelf) Color.White else DeepDarkGreen
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.9f),
            shadowElevation = 2.dp
        ) {
            Text(
                text = name,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = DeepDarkGreen,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * 3. Priority Alert Section
 */
@Composable
private fun HomePriorityAlertSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4F2)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCCBC))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alert",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "PRIORITY ALERT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "2m ago",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Perimeter check requested by Team Alpha",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                )
            }
        }
    }
}

/**
 * Custom Canvas Microphone Icon Component
 */
@Composable
private fun MicrophoneIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 5f

        // Capsule body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.35f, h * 0.15f),
            size = Size(w * 0.3f, h * 0.45f),
            cornerRadius = CornerRadius(w * 0.15f, w * 0.15f)
        )

        // Arc holder
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.25f, h * 0.3f),
            size = Size(w * 0.5f, h * 0.4f),
            style = Stroke(width = stroke)
        )

        // Stand stem & base
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.7f),
            end = Offset(w * 0.5f, h * 0.85f),
            strokeWidth = stroke
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.35f, h * 0.85f),
            end = Offset(w * 0.65f, h * 0.85f),
            strokeWidth = stroke
        )
    }
}

/**
 * 4. Hold to Talk Section with Smooth Press/Release Scale Animation
 */
@Composable
private fun HomeHoldToTalkSection(
    uiState: CommunicationUiState,
    onPressed: () -> Unit,
    onReleased: () -> Unit
) {
    val isRecording = uiState is CommunicationUiState.Recording

    // Smooth Framer Motion-style Spring Scale Animation
    val buttonScale by animateFloatAsState(
        targetValue = if (isRecording) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PttButtonScale"
    )

    // Outer Glow Ring Scale & Alpha
    val glowScale by animateFloatAsState(
        targetValue = if (isRecording) 1.28f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "PttGlowScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Large Circular Button with Smooth Scale
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp)
        ) {
            // Outer Ring Glow Animation
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            scaleX = glowScale
                            scaleY = glowScale
                        }
                        .clip(CircleShape)
                        .background(SoftLightGreen.copy(alpha = 0.45f))
                )
            }

            Surface(
                shape = CircleShape,
                color = if (isRecording) SoftLightGreen else DeepDarkGreen,
                shadowElevation = if (isRecording) 16.dp else 10.dp,
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onPressed()
                                tryAwaitRelease()
                                onReleased()
                            }
                        )
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    MicrophoneIcon(
                        tint = if (isRecording) DeepDarkGreen else Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isRecording) "LISTENING..." else "HOLD TO TALK",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isRecording) "Release when finished speaking" else "Press and hold to broadcast voice message",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 5. Bottom navigation, docked to the bottom edge. It extends under the system gesture bar
 * and pads its items above it.
 */
@Composable
private fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavigationItem("Home", Icons.Default.Home),
        NavigationItem("Map", Icons.Default.LocationOn),
        NavigationItem("Nearby", BluetoothIcon),
        NavigationItem("QR", QrCodeIcon),
        NavigationItem("Settings", Icons.Default.Settings)
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = GrayBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(68.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    BottomNavigationItem(
                        item = item,
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationItem(
    item: NavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) DeepDarkGreen else NavUnselectedGray,
        label = "NavItemContent"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) SoftLightGreen.copy(alpha = 0.45f) else Color.Transparent,
        label = "NavItemIndicator"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null, // the label below names the tab
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

private val NavUnselectedGray = Color(0xFF6B716C)

/** Material "bluetooth" glyph (Apache 2.0); the core icon set doesn't include it. */
private val BluetoothIcon: ImageVector = materialIcon(
    "Bluetooth",
    "M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 " +
        "-4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zM14.88,16.29L13,18.17v-3.76l1.88,1.88z"
)

/** Material "qr_code" glyph (Apache 2.0); the core icon set doesn't include it. */
private val QrCodeIcon: ImageVector = materialIcon(
    "QrCode",
    "M3,11h8V3H3V11zM5,5h4v4H5V5zM3,21h8v-8H3V21zM5,15h4v4H5V15zM13,3v8h8V3H13zM19,9h-4V5h4V9z" +
        "M19,19h2v2h-2zM13,13h2v2h-2zM15,15h2v2h-2zM13,17h2v2h-2zM15,19h2v2h-2zM17,17h2v2h-2z" +
        "M17,13h2v2h-2zM19,15h2v2h-2z"
)

private fun materialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()

private data class NavigationItem(val label: String, val icon: ImageVector)

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen()
}
