package com.itantra.app.feature.settings.ui

import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
private val PrimaryText = Color(0xFF17231F)
private val SecondaryText = Color(0xFF71807A)
private val CardBackground = Color.White

@Composable
fun SettingsScreen() {

    var bluetoothEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                top = 24.dp,
                end = 20.dp,
                bottom = 120.dp
            )
    ) {

        // ---------------------------------------------------------
        // Header
        // ---------------------------------------------------------

        Text(
            text = "Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ---------------------------------------------------------
        // Profile Card
        // ---------------------------------------------------------

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardBackground
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                GrayBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SoftLightGreen)
                        .border(
                            2.dp,
                            PrimaryGreen,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "N",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Nouman",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Edit profile ›",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ---------------------------------------------------------
        // Preferences
        // ---------------------------------------------------------

        SectionTitle("PREFERENCES")

        Spacer(modifier = Modifier.height(10.dp))

        // App Language
        SettingsCard(
            title = "App Language",
            description = "Speech-to-text & text-to-speech",
            trailingContent = {
                Text(
                    text = "Urdu ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryGreen
                )
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bluetooth Discovery
        SettingsCard(
            title = "Bluetooth Discovery",
            description = "Let nearby phones find you",
            trailingContent = {
                Switch(
                    checked = bluetoothEnabled,
                    onCheckedChange = {
                        bluetoothEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.LightGray
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(22.dp))

        // ---------------------------------------------------------
        // Emergency Numbers
        // ---------------------------------------------------------

        SectionTitle("EMERGENCY NUMBERS")

        Spacer(modifier = Modifier.height(10.dp))

        EmergencyContactCard(
            title = "Primary Contact",
            number = "+91 98XXXXXXXX10"
        )

        Spacer(modifier = Modifier.height(10.dp))

        EmergencyContactCard(
            title = "Local Emergency (112)",
            number = "112"
        )

        Spacer(modifier = Modifier.height(10.dp))

        EmergencyContactCard(
            title = "Family Contact",
            number = "+91 90XXXXXXXX44"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Add Emergency Number
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable {
                    // Add emergency number later
                },
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                PrimaryGreen
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Add Emergency Number",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "iTantra v0.9 — Prototype build",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 10.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center
        )
    }
}

// -----------------------------------------------------------------------------
// Section title
// -----------------------------------------------------------------------------

@Composable
private fun SectionTitle(
    title: String
) {
    Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = SecondaryText,
        letterSpacing = 1.2.sp
    )
}

// -----------------------------------------------------------------------------
// Generic Settings Card
// -----------------------------------------------------------------------------

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    trailingContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            GrayBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = SecondaryText
                )
            }

            trailingContent()
        }
    }
}

// -----------------------------------------------------------------------------
// Emergency Contact Card
// -----------------------------------------------------------------------------

@Composable
private fun EmergencyContactCard(
    title: String,
    number: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            GrayBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 11.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Phone indicator
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "☎",
                    fontSize = 17.sp,
                    color = Color(0xFFD32F2F)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = number,
                    fontSize = 10.sp,
                    color = SecondaryText
                )
            }

            Text(
                text = "Edit",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryGreen
            )
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
private fun SettingsScreenPreview() {
    SettingsScreen()
}