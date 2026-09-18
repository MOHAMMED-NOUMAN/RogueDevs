package com.itantra.app.feature.communication.ui
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen

private val PrimaryGreen = Color(0xFF19B878)
private val Cyan = Color(0xFF25C7C7)
private val PrimaryText = Color(0xFF17231F)
private val SecondaryText = Color(0xFF71807A)

@Composable
fun PairingScreen(
    onScanQr: () -> Unit = {},
    onShareCode: () -> Unit = {}
) {
    var topTab by remember { mutableIntStateOf(0) }
    var qrTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {

        // Header
        Text(
            text = "QR Pairing",
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

        // Pair / Organisation Feed
        PairingSegmentedControl(
            selected = topTab,
            firstText = "Pair",
            secondText = "Org Feed",
            onSelected = { topTab = it }
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (topTab == 0) {

            // My QR / Scan
            PairingSegmentedControl(
                selected = qrTab,
                firstText = "My QR",
                secondText = "Scan",
                onSelected = {
                    qrTab = it
                    if (it == 1) {
                        onScanQr()
                    }
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            if (qrTab == 0) {
                MyQrContent(
                    onShareCode = onShareCode
                )
            } else {
                ScanQrContent(
                    onScanQr = onScanQr
                )
            }

        } else {
            OrganisationFeedContent()
        }
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
// My QR
// -----------------------------------------------------------------------------

@Composable
private fun MyQrContent(
    onShareCode: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // QR Card
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
                    modifier = Modifier.size(205.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Nouman — Ch. 2",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Ask a teammate to scan this code\nto join your emergency session.",
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
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                GrayBorder
            )
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
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
                        text = "ITN-4827",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepDarkGreen,
                        letterSpacing = 1.5.sp
                    )
                }

                Text(
                    text = "COPY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Share button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onShareCode() },
            shape = RoundedCornerShape(24.dp),
            color = PrimaryGreen,
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share code",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Share Code",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
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
// Scan QR
// -----------------------------------------------------------------------------

@Composable
private fun ScanQrContent(
    onScanQr: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Surface(
            modifier = Modifier.size(190.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                GrayBorder
            ),
            shadowElevation = 3.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "QR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Scan teammate's QR",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Scan a teammate's pairing code\nto connect to their emergency session.",
            fontSize = 12.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onScanQr() },
            shape = RoundedCornerShape(24.dp),
            color = PrimaryGreen
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen
                )
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Scan QR Code",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
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