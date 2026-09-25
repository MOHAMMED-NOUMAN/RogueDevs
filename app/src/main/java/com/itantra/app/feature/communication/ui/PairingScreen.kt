package com.itantra.app.feature.communication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itantra.app.core.transport.LinkStatus
import com.itantra.app.core.transport.PairingState
import com.itantra.app.feature.pairing.PairingViewModel
import com.itantra.app.feature.pairing.linkStatusLabel
import com.itantra.app.feature.pairing.rememberPairingActions
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen

private val PrimaryGreen = Color(0xFF19B878)
private val PrimaryText = Color(0xFF17231F)
private val SecondaryText = Color(0xFF71807A)
private val ProblemRed = Color(0xFFC0392B)

/**
 * Pair tab. One question at a time: choose Show my code (this phone waits) or Enter a code
 * (this phone joins); then only the steps for that choice; then a clear connected state.
 */
@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel()
) {
    val pairing by viewModel.pairing.collectAsState()
    val pairingError by viewModel.pairingError.collectAsState()
    val linkRunning by viewModel.linkRunning.collectAsState()
    val linkStatus by viewModel.linkStatus.collectAsState()
    val actions = rememberPairingActions(viewModel)

    // Local steps that don't exist in LinkManager: typing a code, and re-pairing while paired.
    var enteringCode by rememberSaveable { mutableStateOf(false) }
    var pairingAnother by rememberSaveable { mutableStateOf(false) }

    // A finished pairing (or a new attempt) resets the local steps.
    LaunchedEffect(pairing) {
        if (pairing is PairingState.Paired || pairing is PairingState.Joining) enteringCode = false
        if (pairing is PairingState.Paired) pairingAnother = false
    }
    BackHandler(enabled = enteringCode || pairingAnother) {
        if (enteringCode) enteringCode = false else pairingAnother = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 120.dp)
    ) {
        Text(
            text = "Pair a teammate",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Connect two phones. No internet or SIM needed.",
            fontSize = 14.sp,
            color = SecondaryText
        )

        Spacer(modifier = Modifier.height(22.dp))

        val problem = actions.problem ?: pairingError?.let { friendlyPairingError(it) }
        if (problem != null) {
            ProblemCard(problem)
            Spacer(modifier = Modifier.height(16.dp))
        }

        val current = pairing
        when {
            current is PairingState.Hosting -> ShowCodeStep(
                code = current.code,
                onCancel = viewModel::cancelPairing
            )

            current is PairingState.Joining -> SearchingStep(
                code = current.code,
                onCancel = viewModel::cancelPairing
            )

            enteringCode -> EnterCodeStep(
                onBack = { enteringCode = false },
                onConnect = actions.join
            )

            current is PairingState.Paired && !pairingAnother -> ConnectedStep(
                address = current.peer.address,
                status = linkStatusLabel(current, linkRunning, linkStatus),
                connected = linkStatus is LinkStatus.Connected,
                onPairAnother = { pairingAnother = true },
                onForget = viewModel::forget
            )

            else -> ChooseStep(
                showBack = current is PairingState.Paired,
                onBack = { pairingAnother = false },
                onShowCode = actions.host,
                onEnterCode = { enteringCode = true }
            )
        }
    }
}

/** Turns transport errors into something a person can act on. */
private fun friendlyPairingError(raw: String): String = when {
    raw.startsWith("no nearby phone is hosting") ->
        "Couldn't find your teammate's phone. Check the code, keep the phones close, and make sure " +
            "the other phone is still showing its code."
    raw.contains("Bluetooth is off", ignoreCase = true) -> "Turn Bluetooth on and try again."
    else -> "Pairing didn't finish ($raw). Try again."
}

// -----------------------------------------------------------------------------
// Step: choose what this phone does
// -----------------------------------------------------------------------------

@Composable
private fun ChooseStep(
    showBack: Boolean,
    onBack: () -> Unit,
    onShowCode: () -> Unit,
    onEnterCode: () -> Unit
) {
    Column {
        if (showBack) {
            BackRow(text = "Back to your teammate", onClick = onBack)
            Spacer(modifier = Modifier.height(12.dp))
        }

        StepLabel("DO THIS ON EACH PHONE")

        Spacer(modifier = Modifier.height(10.dp))

        ChoiceCard(
            icon = Icons.Rounded.Pin,
            title = "Show my code",
            description = "Pick this on one phone. You'll get a 4-digit code to share.",
            onClick = onShowCode
        )

        Spacer(modifier = Modifier.height(10.dp))

        ChoiceCard(
            icon = Icons.Rounded.Dialpad,
            title = "Enter a code",
            description = "Pick this on the other phone, then type the code it shows.",
            onClick = onEnterCode
        )

        Spacer(modifier = Modifier.height(10.dp))

        ChoiceCard(
            icon = Icons.Rounded.QrCodeScanner,
            title = "Scan a QR code",
            description = "Pair by scanning your teammate's screen.",
            comingSoon = true,
            onClick = null
        )

        Spacer(modifier = Modifier.height(22.dp))

        HowItWorks()
    }
}

@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: (() -> Unit)?,
    comingSoon: Boolean = false
) {
    val enabled = onClick != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.6f),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, GrayBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (enabled) SoftLightGreen.copy(alpha = 0.45f) else GrayBorder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DeepDarkGreen,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    if (comingSoon) {
                        Spacer(modifier = Modifier.width(8.dp))
                        ComingSoonChip()
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = SecondaryText,
                    lineHeight = 17.sp
                )
            }

            if (enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SecondaryText
                )
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SoftLightGreen.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, GrayBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StepLabel("HOW IT WORKS")
            Spacer(modifier = Modifier.height(10.dp))
            NumberedLine(1, "Phone A taps Show my code.")
            NumberedLine(2, "Phone B taps Enter a code and types it.")
            NumberedLine(3, "The phones connect by themselves: Wi-Fi Direct first, Bluetooth as backup.")
        }
    }
}

@Composable
private fun NumberedLine(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(DeepDarkGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = PrimaryText,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// Step: this phone shows its code and waits
// -----------------------------------------------------------------------------

@Composable
private fun ShowCodeStep(
    code: String,
    onCancel: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepLabel("YOUR CODE", modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, GrayBorder)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    code.forEach { digit -> DigitBox(digit) }
                }

                Spacer(modifier = Modifier.height(18.dp))

                SearchingPulse(modifier = Modifier.size(150.dp))

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Waiting for your teammate…",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This phone stays visible for 2 minutes.",
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = SoftLightGreen.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, GrayBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StepLabel("ON YOUR TEAMMATE'S PHONE")
                Spacer(modifier = Modifier.height(10.dp))
                NumberedLine(1, "Open iTantra and go to Pair.")
                NumberedLine(2, "Tap Enter a code.")
                NumberedLine(3, "Type $code and tap Connect.")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PairingButton(text = "Cancel", filled = false, onClick = onCancel)
    }
}

@Composable
private fun DigitBox(digit: Char) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SoftLightGreen.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen
        )
    }
}

// -----------------------------------------------------------------------------
// Step: this phone types the teammate's code
// -----------------------------------------------------------------------------

@Composable
private fun EnterCodeStep(
    onBack: () -> Unit,
    onConnect: (String) -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }

    Column {
        BackRow(text = "Back", onClick = onBack)

        Spacer(modifier = Modifier.height(12.dp))

        StepLabel("ENTER YOUR TEAMMATE'S CODE")

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "It's the 4 digits on the phone that tapped Show my code.",
            fontSize = 13.sp,
            color = SecondaryText,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(4) },
            placeholder = {
                Text(
                    text = "0000",
                    fontSize = 30.sp,
                    letterSpacing = 12.sp,
                    color = GrayBorder,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 12.sp,
                color = DeepDarkGreen,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = GrayBorder,
                focusedBorderColor = PrimaryGreen,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        PairingButton(
            text = "Connect",
            filled = true,
            enabled = code.length == 4,
            onClick = { onConnect(code) }
        )
    }
}

// -----------------------------------------------------------------------------
// Step: looking for the phone showing the code
// -----------------------------------------------------------------------------

@Composable
private fun SearchingStep(
    code: String,
    onCancel: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, GrayBorder)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 26.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SearchingPulse(modifier = Modifier.size(170.dp))

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Looking for the phone showing $code…",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Keep both phones close. This takes about 15 seconds.",
                    fontSize = 12.sp,
                    color = SecondaryText,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PairingButton(text = "Cancel", filled = false, onClick = onCancel)
    }
}

// -----------------------------------------------------------------------------
// Step: paired
// -----------------------------------------------------------------------------

@Composable
private fun ConnectedStep(
    address: String,
    status: String,
    connected: Boolean,
    onPairAnother: () -> Unit,
    onForget: () -> Unit
) {
    val accent = if (connected) PrimaryGreen else SecondaryText

    Column {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, GrayBorder)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f))
                        .border(2.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (connected) Icons.Rounded.CheckCircle else Icons.Rounded.BluetoothSearching,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (connected) "You're connected" else "Paired",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Teammate phone …${address.takeLast(5)}",
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (connected) "Go to Home and hold the mic to talk."
            else "The phones reconnect by themselves when they're near each other.",
            fontSize = 13.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        PairingButton(text = "Pair a different phone", filled = false, onClick = onPairAnother)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Forget this teammate",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ProblemRed,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onForget)
                .padding(vertical = 12.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// Shared pieces
// -----------------------------------------------------------------------------

@Composable
private fun StepLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = SecondaryText,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}

@Composable
private fun BackRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            tint = DeepDarkGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeepDarkGreen
        )
    }
}

@Composable
private fun ProblemCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ProblemRed.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, ProblemRed.copy(alpha = 0.35f))
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = ProblemRed,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ComingSoonChip() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DeepDarkGreen
    ) {
        Text(
            text = "COMING SOON",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun PairingButton(
    text: String,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(26.dp),
        color = when {
            !filled -> Color.White
            enabled -> PrimaryGreen
            else -> PrimaryGreen.copy(alpha = 0.4f)
        },
        border = if (filled) null else BorderStroke(1.dp, GrayBorder),
        shadowElevation = if (filled && enabled) 3.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (filled) Color.White else DeepDarkGreen
            )
        }
    }
}

/** Searching animation: rings ripple out from a Bluetooth icon at the centre. */
@Composable
private fun SearchingPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "searching")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2
            val minRadius = 30.dp.toPx()
            // Three rings a third of a cycle apart, each growing and fading out.
            repeat(3) { ring ->
                val t = (progress + ring / 3f) % 1f
                drawCircle(
                    color = PrimaryGreen.copy(alpha = 0.35f * (1f - t)),
                    radius = minRadius + (maxRadius - minRadius) * t,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(PrimaryGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.BluetoothSearching,
                contentDescription = "Searching",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
