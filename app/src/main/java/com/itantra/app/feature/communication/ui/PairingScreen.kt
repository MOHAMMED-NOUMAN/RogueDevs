package com.itantra.app.feature.communication.ui
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itantra.app.core.transport.PairingState
import com.itantra.app.feature.pairing.PairingViewModel
import com.itantra.app.feature.pairing.linkStatusLabel
import com.itantra.app.feature.pairing.rememberPairingActions
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen

private val PrimaryGreen = Color(0xFF19B878)
private val Cyan = Color(0xFF25C7C7)
private val PrimaryText = Color(0xFF17231F)
private val SecondaryText = Color(0xFF71807A)
private val ProblemRed = Color(0xFFC0392B)

@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel()
) {
    val pairing by viewModel.pairing.collectAsState()
    val pairingError by viewModel.pairingError.collectAsState()
    val linkRunning by viewModel.linkRunning.collectAsState()
    val linkStatus by viewModel.linkStatus.collectAsState()
    val actions = rememberPairingActions(viewModel)

    var topTab by remember { mutableIntStateOf(0) }
    var codeTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 120.dp)
    ) {

        // Header
        Text(
            text = "Pairing",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Connect securely with nearby teammates",
            fontSize = 14.sp,
            color = SecondaryText
        )

        Spacer(modifier = Modifier.height(22.dp))

        if (pairing is PairingState.Paired) {
            PairedCard(
                status = linkStatusLabel(pairing, linkRunning, linkStatus),
                onForget = viewModel::forget
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        val problem = actions.problem ?: pairingError?.let { "Pairing failed: $it" }
        if (problem != null) {
            ProblemCard(problem)
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Pair / Organisation Feed
        PairingSegmentedControl(
            selected = topTab,
            firstText = "Pair",
            secondText = "Org Feed",
            onSelected = { topTab = it }
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (topTab == 0) {

            // My code / Enter code
            PairingSegmentedControl(
                selected = codeTab,
                firstText = "My Code",
                secondText = "Enter Code",
                onSelected = { codeTab = it }
            )

            Spacer(modifier = Modifier.height(22.dp))

            if (codeTab == 0) {
                MyCodeContent(
                    code = viewModel.hostCode,
                    hosting = pairing is PairingState.Hosting,
                    onHost = actions.host,
                    onCancel = viewModel::cancelPairing
                )
            } else {
                EnterCodeContent(
                    joining = pairing is PairingState.Joining,
                    onJoin = actions.join,
                    onCancel = viewModel::cancelPairing
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            QrComingSoonNote()

        } else {
            OrganisationFeedContent()
        }
    }
}

// -----------------------------------------------------------------------------
// Pairing status
// -----------------------------------------------------------------------------

@Composable
private fun PairedCard(
    status: String,
    onForget: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, GrayBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PAIRED TEAMMATE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = status,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen
                )
            }

            Text(
                text = "FORGET",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ProblemRed,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onForget() }
                    .padding(8.dp)
            )
        }
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
            fontSize = 12.sp,
            color = ProblemRed,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// Segmented Control
// -----------------------------------------------------------------------------

@Composable
private fun PairingSegmentedControl(
    selected: Int,
    firstText: String,
    secondText: String,
    onSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFE8EFEB)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
        ) {

            SegmentItem(
                text = firstText,
                selected = selected == 0,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(0) }
            )

            SegmentItem(
                text = secondText,
                selected = selected == 1,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(1) }
            )
        }
    }
}

@Composable
private fun SegmentItem(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(21.dp))
            .background(
                if (selected) PrimaryGreen
                else Color.Transparent
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
            color = if (selected) {
                Color.White
            } else {
                SecondaryText
            }
        )
    }
}

// -----------------------------------------------------------------------------
// My Code (this phone hosts)
// -----------------------------------------------------------------------------

@Composable
private fun MyCodeContent(
    code: String,
    hosting: Boolean,
    onHost: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // QR card: placeholder until QR pairing ships
        Surface(
            modifier = Modifier
                .size(250.dp)
                .border(
                    width = 1.dp,
                    color = GrayBorder,
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                FakeQrCode(
                    modifier = Modifier
                        .size(205.dp)
                        .alpha(0.12f)
                )
                ComingSoonChip()
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Your pairing code",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tap Wait for teammate, then ask them to\nenter this code under Enter Code.",
            fontSize = 12.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Pairing code
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SoftLightGreen.copy(alpha = 0.35f),
            border = BorderStroke(
                1.dp,
                GrayBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
            ) {
                Text(
                    text = "PAIRING CODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = code,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen,
                    letterSpacing = 6.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (hosting) {
            Text(
                text = "Waiting for teammate… this phone stays\nvisible over Bluetooth for 2 minutes.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeepDarkGreen,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            PairingButton(text = "Cancel", filled = false, onClick = onCancel)
        } else {
            PairingButton(text = "Wait for teammate", filled = true, onClick = onHost)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✓",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryGreen
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "Works offline with nearby devices",
                fontSize = 11.sp,
                color = SecondaryText
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Enter Code (this phone joins)
// -----------------------------------------------------------------------------

@Composable
private fun EnterCodeContent(
    joining: Boolean,
    onJoin: (String) -> Unit,
    onCancel: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Enter teammate's code",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Type the 4-digit code shown on your\nteammate's phone after they tap Wait for teammate.",
            fontSize = 12.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(4) },
            label = { Text("Pairing code") },
            enabled = !joining,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (joining) {
            Text(
                text = "Looking for your teammate's phone…\nthis takes about 15 seconds.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeepDarkGreen,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            PairingButton(text = "Cancel", filled = false, onClick = onCancel)
        } else {
            PairingButton(
                text = "Join",
                filled = true,
                enabled = code.length == 4,
                onClick = { onJoin(code) }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// QR pairing: coming soon
// -----------------------------------------------------------------------------

@Composable
private fun ComingSoonChip() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DeepDarkGreen
    ) {
        Text(
            text = "QR · COMING SOON",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun QrComingSoonNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, GrayBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "QR PAIRING · COMING SOON",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = SecondaryText,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Scan-to-Trust",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DeepDarkGreen
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "One QR scan will join a teammate and exchange encryption keys " +
                    "(X25519). Every message after that is sealed with AES-256-GCM, and " +
                    "replayed messages are dropped. Until then, pair with the 4-digit code.",
                fontSize = 12.sp,
                color = SecondaryText,
                lineHeight = 18.sp
            )
        }
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
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(24.dp),
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (filled) Color.White else DeepDarkGreen
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Organisation Feed
// -----------------------------------------------------------------------------

@Composable
private fun OrganisationFeedContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {

        Text(
            text = "ORGANISATION QR FEED",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = SecondaryText
        )

        Spacer(modifier = Modifier.height(12.dp))

        OrganisationCard(
            name = "NDRF Unit 07",
            description = "Emergency response channel"
        )

        Spacer(modifier = Modifier.height(10.dp))

        OrganisationCard(
            name = "Relief Camp Alpha",
            description = "Team communication channel"
        )

        Spacer(modifier = Modifier.height(10.dp))

        OrganisationCard(
            name = "Field Operations",
            description = "Local emergency network"
        )
    }
}

@Composable
private fun OrganisationCard(
    name: String,
    description: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                GrayBorder,
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftLightGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "QR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Temporary QR Code
// -----------------------------------------------------------------------------

@Composable
private fun FakeQrCode(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(Color.White)
            .padding(4.dp)
    ) {

        val cells = 21
        val cellSize = size.minDimension / cells

        // Deterministic pattern for UI/demo purposes.
        // Replace with a real QR generator when pairing logic is implemented.

        for (row in 0 until cells) {
            for (column in 0 until cells) {

                val finderZone =
                    (column < 7 && row < 7) ||
                            (column >= cells - 7 && row < 7) ||
                            (column < 7 && row >= cells - 7)

                val finderPattern = if (finderZone) {
                    val localColumn = when {
                        column < 7 -> column
                        else -> column - (cells - 7)
                    }

                    val localRow = when {
                        row < 7 -> row
                        true -> row - (cells - 7)
                        else -> row
                    }

                    localColumn == 0 ||
                            localColumn == 6 ||
                            localRow == 0 ||
                            localRow == 6 ||
                            (localColumn in 2..4 && localRow in 2..4)
                } else {
                    false
                }

                val randomPattern =
                    ((row * 17 + column * 31 + row * column) % 7 < 3)

                if (finderPattern || (!finderZone && randomPattern)) {
                    drawRect(
                        color = Color(0xFF101717),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            column * cellSize,
                            row * cellSize
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            cellSize,
                            cellSize
                        ),
                        style = Fill
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Preview
// -----------------------------------------------------------------------------

@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun PairingScreenPreview() {
    PairingScreen()
}