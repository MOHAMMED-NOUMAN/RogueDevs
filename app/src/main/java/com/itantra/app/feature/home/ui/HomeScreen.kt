package com.itantra.app.feature.home.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.itantra.app.core.messaging.IncomingMessage
import com.itantra.app.core.messaging.MessageLanguage
import com.itantra.app.core.messaging.SendStatus
import com.itantra.app.core.permissions.LinkPermissions
import com.itantra.app.core.transport.LinkStatus
import com.itantra.app.feature.communication.ui.PairingScreen
import com.itantra.app.feature.communication.viewmodel.CommunicationUiState
import com.itantra.app.feature.communication.viewmodel.CommunicationViewModel
import com.itantra.app.feature.emergency.IncomingSosAlert
import com.itantra.app.feature.emergency.SOS
import com.itantra.app.feature.emergency.viewmodel.SosViewModel
import com.itantra.app.feature.pairing.PairingViewModel
import com.itantra.app.feature.pairing.linkStatusLabel
import com.itantra.app.feature.settings.ui.SettingsScreen
import com.itantra.app.ui.components.HeaderLogoHeight
import com.itantra.app.ui.components.ItantraLogo
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen
import kotlin.math.roundToInt

// Tab indices match NavItems; SOS is a full screen without the bar. The Team Map tab is in
// backlog/team-map until it has real positions.
private const val TAB_HOME = 0
private const val TAB_PAIR = 1
private const val TAB_SETTINGS = 2
private const val TAB_SOS = 3

private val NavItems = listOf(
    NavigationItem("Home", Icons.Rounded.Home),
    NavigationItem("Pair", Icons.Rounded.Link),
    NavigationItem("Settings", Icons.Rounded.Settings)
)

@Composable
fun HomeScreen(
    communicationViewModel: CommunicationViewModel = hiltViewModel(),
    pairingViewModel: PairingViewModel = hiltViewModel(),
    sosViewModel: SosViewModel = hiltViewModel()
) {
    val uiState by communicationViewModel.uiState.collectAsState()
    val incoming by communicationViewModel.incoming.collectAsState()
    val pairing by pairingViewModel.pairing.collectAsState()
    val linkRunning by pairingViewModel.linkRunning.collectAsState()
    val linkStatus by pairingViewModel.linkStatus.collectAsState()
    var selectedTab by remember { mutableIntStateOf(TAB_HOME) }

    // A phone paired in an earlier session reconnects as soon as the app opens.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (LinkPermissions.allRequiredGranted(context)) pairingViewModel.ensureLinkRunning()
    }

    // Hold to Talk asks for the microphone on first use; the user presses again once allowed.
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) communicationViewModel.onMicPermissionDenied() }
    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
    ) {
        when (selectedTab) {
            TAB_PAIR -> PairingScreen(viewModel = pairingViewModel)
            TAB_SETTINGS -> SettingsScreen(pairingViewModel = pairingViewModel)
            TAB_SOS -> SOS(onBack = { selectedTab = TAB_HOME }, viewModel = sosViewModel)
            else -> {
                // Home: status, SOS and push-to-talk only
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = 90.dp) // Leave room for floating bottom nav
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HomeHeaderSection(
                        onSOSClick = { selectedTab = TAB_SOS },
                        linkLabel = linkStatusLabel(pairing, linkRunning, linkStatus),
                        linkUp = linkStatus is LinkStatus.Connected
                    )

                    incoming.firstOrNull()?.let { latest ->
                        Spacer(modifier = Modifier.height(16.dp))
                        IncomingMessageCard(latest)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Scrolls only when the cards don't fit (small screens).
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HomeHoldToTalkSection(
                                uiState = uiState,
                                onPressed = {
                                    if (micGranted()) communicationViewModel.onPushToTalkPressed()
                                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onReleased = { communicationViewModel.onPushToTalkReleased() }
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            TranscriptSection(uiState = uiState)
                        }
                    }
                }
            }
        }

        if (selectedTab != TAB_SOS) {
            FloatingBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }

        // An SOS from the teammate covers every tab until it is answered and closed.
        IncomingSosAlert(viewModel = sosViewModel)
    }
}

/**
 * Header: iTantra logo, live link status and the SOS button.
 */
@Composable
private fun HomeHeaderSection(
    onSOSClick: () -> Unit,
    linkLabel: String,
    linkUp: Boolean
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
        // App Title & Link Status
        Column {
            ItantraLogo(height = HeaderLogoHeight)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (linkUp) Color(0xFF2E7D32) else Color.Gray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = linkLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
            }
        }

        // Red SOS Button with Continuous Pulse Effect
        Box(contentAlignment = Alignment.Center) {
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
                onClick = onSOSClick,
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFD32F2F),
                border = BorderStroke(2.dp, Color(0xFFFFCDD2)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
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
 * Hold to Talk with press/release scale animation.
 */
@Composable
private fun HomeHoldToTalkSection(
    uiState: CommunicationUiState,
    onPressed: () -> Unit,
    onReleased: () -> Unit
) {
    val isRecording = uiState is CommunicationUiState.Recording

    val buttonScale by animateFloatAsState(
        targetValue = if (isRecording) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PttButtonScale"
    )

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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(180.dp)
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
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
                shadowElevation = if (isRecording) 14.dp else 8.dp,
                modifier = Modifier
                    .size(132.dp)
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
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Hold to talk",
                        tint = if (isRecording) DeepDarkGreen else Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = when (uiState) {
                CommunicationUiState.Recording -> "LISTENING..."
                CommunicationUiState.Transcribing -> "CONVERTING..."
                else -> "HOLD TO TALK"
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = when (uiState) {
                CommunicationUiState.Recording -> "Release when finished speaking"
                CommunicationUiState.Transcribing -> "Turning your speech into text"
                else -> "Press and hold, speak, then let go"
            },
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * What Hold to Talk heard, shown once after each release so it can be checked, with the
 * delivery status of the message it sent.
 */
@Composable
private fun TranscriptSection(uiState: CommunicationUiState) {
    when (uiState) {
        CommunicationUiState.Transcribing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = DeepDarkGreen
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Converting speech to text…", fontSize = 13.sp, color = Color.Gray)
        }

        is CommunicationUiState.Transcribed -> {
            val t = uiState.transcript
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, GrayBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "YOU SAID",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = t.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepDarkGreen,
                        lineHeight = 25.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${t.language.label} · ${"%.1f".format(t.speechSeconds)} s of speech · " +
                            "converted in ${"%.1f".format(t.convertMs / 1000f)} s",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SendStatusRow(uiState.sendStatus)
                }
            }
        }

        is CommunicationUiState.Problem -> Text(
            text = uiState.message,
            fontSize = 13.sp,
            color = Color(0xFFD32F2F),
            textAlign = TextAlign.Center
        )

        else -> Unit
    }
}

/** Delivery of the last message to the teammate. */
@Composable
private fun SendStatusRow(status: SendStatus) {
    val (icon, text, color) = when (status) {
        SendStatus.SENDING -> Triple(Icons.Rounded.Schedule, "Sending to your teammate…", Color.Gray)
        SendStatus.WAITING -> Triple(Icons.Rounded.Schedule, "Waiting for the link. Sends when it's back.", Color.Gray)
        SendStatus.DELIVERED -> Triple(Icons.Rounded.DoneAll, "Delivered", Color(0xFF19B878))
        SendStatus.FAILED -> Triple(Icons.Rounded.ErrorOutline, "Not delivered. Teammate out of reach.", Color(0xFFD32F2F))
        SendStatus.NOT_PAIRED -> Triple(Icons.Rounded.LinkOff, "Not sent. Pair a teammate first.", Color(0xFFD32F2F))
        SendStatus.LINK_OFF -> Triple(Icons.Rounded.LinkOff, "Not sent. Offline Link is off in Settings.", Color(0xFFD32F2F))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** The teammate's latest message. */
@Composable
private fun IncomingMessageCard(message: IncomingMessage) {
    val time = remember(message.receivedAtMs) {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(message.receivedAtMs))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SoftLightGreen.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, SoftLightGreen)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = null,
                    tint = DeepDarkGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FROM ${message.sender.ifBlank { "TEAMMATE" }.uppercase()}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(text = time, fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeepDarkGreen,
                lineHeight = 25.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (message.language) {
                    MessageLanguage.ENGLISH -> "English"
                    MessageLanguage.HINDI -> "Hindi"
                },
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

/**
 * Floating bottom navigation. The raised circle slides to the selected tab on a short
 * eased tween; its position is read in the layout phase so sliding doesn't recompose.
 */
@Composable
private fun FloatingBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedTabPosition by animateFloatAsState(
        targetValue = selectedTab.toFloat(),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "ActiveTabPosition"
    )
    val barShadow = DeepDarkGreen.copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        // Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .align(Alignment.BottomCenter)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(31.dp),
                    ambientColor = barShadow,
                    spotColor = barShadow
                ),
            shape = RoundedCornerShape(31.dp),
            color = Color.White,
            border = BorderStroke(1.dp, GrayBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItems.forEachIndexed { index, item ->
                    val isSelected = selectedTab == index
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // The sliding circle is the press feedback; no ripple box.
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(index) }
                    ) {
                        if (!isSelected) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            // The selected tab's icon sits in the raised circle; its label stays low.
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepDarkGreen,
                                modifier = Modifier.padding(top = 22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Raised circle for the selected tab
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .align(Alignment.BottomCenter)
        ) {
            val itemWidth = maxWidth / NavItems.size
            val selected = NavItems[selectedTab.coerceIn(NavItems.indices)]

            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .height(84.dp)
                    .offset {
                        IntOffset((itemWidth.toPx() * animatedTabPosition).roundToInt(), 0)
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            ambientColor = barShadow,
                            spotColor = barShadow
                        )
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(DeepDarkGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = selected.icon,
                            contentDescription = null,
                            tint = SoftLightGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class NavigationItem(val label: String, val icon: ImageVector)
