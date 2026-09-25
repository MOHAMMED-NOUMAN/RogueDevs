package com.itantra.app.feature.emergency

import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.core.messaging.SosLocation
import com.itantra.app.core.sos.IncomingSos
import com.itantra.app.feature.emergency.viewmodel.SosViewModel
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val SOSRed = Color(0xFFE53945)
private val DeepDarkGreen = Color(0xFF193D25)

/**
 * Full-screen alert on the teammate's phone while an SOS is open. Covers every tab (and eats
 * taps behind it) until it is answered and closed.
 */
@Composable
fun IncomingSosAlert(viewModel: SosViewModel) {
    val incoming by viewModel.incoming.collectAsState()
    val myLocation by viewModel.myLocation.collectAsState()
    val sos = incoming ?: return

    LaunchedEffect(sos.id) { viewModel.refreshMyLocation() }

    val name = sos.sender.ifBlank { "Your teammate" }
    val answered = sos.state == IncomingSos.State.ACKNOWLEDGED
    val cancelled = sos.state == IncomingSos.State.CANCELLED
    val background = if (answered) DeepDarkGreen else SOSRed
    val sentAt = remember(sos.sentAtEpochSeconds) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(sos.sentAtEpochSeconds * 1000))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when {
                    answered -> "YOU'RE ON YOUR WAY"
                    cancelled -> "SOS CANCELLED"
                    else -> "SOS"
                },
                fontSize = if (answered || cancelled) 26.sp else 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    answered -> "$name has been told you're coming."
                    cancelled -> "$name called off the SOS."
                    else -> "$name needs help"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Sent $sentAt", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))

            Spacer(modifier = Modifier.height(28.dp))

            LocationCard(sos.location, myLocation)

            Spacer(modifier = Modifier.height(32.dp))

            when {
                answered || cancelled -> AlertButton("Close", filled = false, onClick = viewModel::dismissIncoming)
                else -> {
                    AlertButton("I'm coming", filled = true, onClick = viewModel::acknowledge)
                    if (sos.state == IncomingSos.State.RINGING) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AlertButton("Silence alarm", filled = false, onClick = viewModel::silence)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationCard(location: SosLocation?, mine: Location?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "WHERE THEY ARE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (location == null) {
                Text(
                    text = "No location shared yet. It appears here as soon as their GPS finds one.",
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp
                )
                return@Column
            }

            if (mine != null) {
                val result = FloatArray(2)
                Location.distanceBetween(mine.latitude, mine.longitude, location.latitude, location.longitude, result)
                val bearing = (result[1] + 360f) % 360f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Navigation,
                            contentDescription = "Direction",
                            tint = SOSRed,
                            modifier = Modifier
                                .size(26.dp)
                                .rotate(bearing)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "About ${distanceText(result[0])} ${compassName(bearing)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(text = "from you, as the crow flies", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = "%.5f, %.5f".format(location.latitude, location.longitude),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = if (location.accuracyMeters > 0) "Accurate to about ${location.accuracyMeters} m" else "Accuracy unknown",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            if (mine == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Turn on location on this phone to see how far away they are.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun AlertButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (filled) Color.White else Color.Transparent,
        border = if (filled) null else BorderStroke(1.5.dp, Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (filled) SOSRed else Color.White
            )
        }
    }
}

private fun distanceText(meters: Float): String =
    if (meters < 1000) "${(meters / 10).roundToInt() * 10} m" else "%.1f km".format(meters / 1000)

private fun compassName(bearing: Float): String {
    val names = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
    return names[((bearing + 22.5f) / 45f).toInt() % 8]
}
