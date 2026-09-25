package com.itantra.app.feature.location.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloatAsState

data class TeamMemberLocation(
    val id: String,
    val name: String,
    val initials: String,
    val markerColor: Color,
    val lastSeenText: String,
    val distanceText: String,
    val statusDotColor: Color,
    val mapPercentX: Float,
    val mapPercentY: Float
)

/**
 * Sample teammates for the demo map (not real data yet). Positions are fractions of the map
 * panel, with You at the centre; distances roughly match the 200 m / 400 m range rings.
 */
val defaultTeamMembers = listOf(
    TeamMemberLocation(
        id = "you",
        name = "You",
        initials = "You",
        markerColor = YouBlue,
        lastSeenText = "Active now",
        distanceText = "0 m",
        statusDotColor = ActiveGreen,
        mapPercentX = 0.50f,
        mapPercentY = 0.52f
    ),
    TeamMemberLocation(
        id = "arjun",
        name = "Arjun R.",
        initials = "A",
        markerColor = ActiveGreen,
        lastSeenText = "2 min ago",
        distanceText = "260 m away",
        statusDotColor = ActiveGreen,
        mapPercentX = 0.36f,
        mapPercentY = 0.36f
    ),
    TeamMemberLocation(
        id = "priya",
        name = "Priya N.",
        initials = "P",
        markerColor = ActiveGreen,
        lastSeenText = "4 min ago",
        distanceText = "400 m away",
        statusDotColor = ActiveGreen,
        mapPercentX = 0.73f,
        mapPercentY = 0.30f
    ),
    TeamMemberLocation(
        id = "imran",
        name = "Imran S.",
        initials = "I",
        markerColor = RecentOrange,
        lastSeenText = "14 min ago",
        distanceText = "410 m away",
        statusDotColor = RecentOrange,
        mapPercentX = 0.26f,
        mapPercentY = 0.74f
    ),
    TeamMemberLocation(
        id = "kavya",
        name = "Kavya M.",
        initials = "K",
        markerColor = InactiveGrey,
        lastSeenText = "35 min ago",
        distanceText = "500 m away",
        statusDotColor = InactiveGrey,
        mapPercentX = 0.80f,
        mapPercentY = 0.78f
    )
)

private val YouBlue get() = Color(0xFF2F6FED)
private val ActiveGreen get() = Color(0xFF3FA34D)
private val RecentOrange get() = Color(0xFFF08C00)
private val InactiveGrey get() = Color(0xFF9AA3A0)

// Offline map palette: quiet land, water and parks so the team markers stand out.
private val MapLand = Color(0xFFF1F3EE)
private val MapPark = Color(0xFFDDEBD6)
private val MapWater = Color(0xFFCFE1EA)
private val MapRoad = Color.White
private val MapRoadCasing = Color(0xFFDCE1DA)

@Composable
fun LocationScreen() {
    var selectedViewMode by remember { mutableStateOf("Map") }
    var selectedMember by remember { mutableStateOf(defaultTeamMembers[1]) } // first teammate selected by default

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp) // Room for bottom nav
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header Section
            LocationHeaderSection()

            Spacer(modifier = Modifier.height(16.dp))

            // Team alerts (moved here from Home)
            PriorityAlertSection()

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Map / List Toggle
            MapListToggleSection(
                selectedMode = selectedViewMode,
                onModeSelected = { selectedViewMode = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedViewMode == "Map") {
                // 3. Large Map Panel
                LargeMapPanelSection(
                    teamMembers = defaultTeamMembers,
                    selectedMember = selectedMember,
                    onMemberSelected = { selectedMember = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Map Legend Section
                MapLegendSection()
            } else {
                // List View Mode
                TeamListViewSection(
                    teamMembers = defaultTeamMembers,
                    selectedMember = selectedMember,
                    onMemberSelected = { selectedMember = it }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Selected Member Card
            SelectedMemberCardSection(member = selectedMember)

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Card Footer
            LocationFooterSection()
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 1. Header Component
 */
@Composable
private fun LocationHeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Team Map",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DeepDarkGreen,
                letterSpacing = 0.5.sp
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
                    text = "Offline tiles  •  last sync 3m ago",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, GrayBorder, CircleShape)
                .clickable { /* Refresh map data */ },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Sync",
                tint = DeepDarkGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 2. Map / List Pill Toggle
 */
@Composable
private fun MapListToggleSection(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFEFEFEF),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(3.dp)
    ) {
        val isMapSelected = selectedMode == "Map"

        val indicatorPosition by animateFloatAsState(
            targetValue = if (isMapSelected) 0f else 1f,
            animationSpec = androidx.compose.animation.core.tween<Float>(450),
            label = "togglePosition"
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val halfWidth = maxWidth / 2

            // Moving green pill
            Box(
                modifier = Modifier
                    .width(halfWidth)
                    .fillMaxSize()
                    .offset(x = halfWidth * indicatorPosition)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DeepDarkGreen)
            )

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .clickable { onModeSelected("Map") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Map",
                        fontSize = 14.sp,
                        fontWeight = if (isMapSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isMapSelected) SoftLightGreen else Color.Gray
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .clickable { onModeSelected("List") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "List",
                        fontSize = 14.sp,
                        fontWeight = if (!isMapSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isMapSelected) SoftLightGreen else Color.Gray
                    )
                }
            }
        }
    }
}
/**
 * 3. Offline team map: land, water, parks and roads drawn in place (no tiles), range rings
 * around You, a dashed line to the selected teammate, compass and scale bar.
 */
@Composable
private fun LargeMapPanelSection(
    teamMembers: List<TeamMemberLocation>,
    selectedMember: TeamMemberLocation,
    onMemberSelected: (TeamMemberLocation) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val you = teamMembers.first { it.id == "you" }
    val pulse = rememberInfiniteTransition(label = "youPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "youPulseProgress"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .shadow(3.dp, shape, ambientColor = DeepDarkGreen.copy(alpha = 0.12f), spotColor = DeepDarkGreen.copy(alpha = 0.12f))
            .clip(shape)
            .background(MapLand)
            .border(1.dp, GrayBorder, shape)
    ) {
        val mapWidth = maxWidth
        val mapHeight = maxHeight
        val ringStep = 52.dp // 200 m

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOfflineMapBase()

            val center = Offset(size.width * you.mapPercentX, size.height * you.mapPercentY)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))

            // Range rings: 200 m and 400 m
            for (ring in 1..2) {
                drawCircle(
                    color = DeepDarkGreen.copy(alpha = 0.22f),
                    radius = ringStep.toPx() * ring,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = dash)
                )
            }

            // Line to the selected teammate
            if (selectedMember.id != you.id) {
                drawLine(
                    color = DeepDarkGreen.copy(alpha = 0.7f),
                    start = center,
                    end = Offset(size.width * selectedMember.mapPercentX, size.height * selectedMember.mapPercentY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
                    cap = StrokeCap.Round
                )
            }

            // You: accuracy halo that ripples out, then the blue dot
            val t = pulse.value
            drawCircle(
                color = YouBlue.copy(alpha = 0.22f * (1f - t)),
                radius = 10.dp.toPx() + 22.dp.toPx() * t,
                center = center
            )
            drawCircle(color = Color.White, radius = 10.dp.toPx(), center = center)
            drawCircle(color = YouBlue, radius = 7.dp.toPx(), center = center)
        }

        // Ring labels, on the right of each ring
        for (ring in 1..2) {
            RingLabel(
                text = "${ring * 200} m",
                modifier = Modifier.offset(
                    x = mapWidth * you.mapPercentX + ringStep * ring - 18.dp,
                    y = mapHeight * you.mapPercentY - 9.dp
                )
            )
        }

        // "You" label under the dot
        MapNameChip(
            text = "You",
            selected = false,
            modifier = Modifier
                .width(64.dp)
                .offset(x = mapWidth * you.mapPercentX - 32.dp, y = mapHeight * you.mapPercentY + 13.dp)
        )

        // Teammates
        teamMembers.filter { it.id != you.id }.forEach { member ->
            InteractiveMapMarker(
                member = member,
                isSelected = member.id == selectedMember.id,
                onClick = { onMemberSelected(member) },
                modifier = Modifier.offset(
                    x = mapWidth * member.mapPercentX - MarkerWidth / 2,
                    y = mapHeight * member.mapPercentY - MarkerPinRadius
                )
            )
        }

        // Compass
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(36.dp),
            shape = CircleShape,
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = "North",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "N",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen
                )
            }
        }

        // Scale bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.92f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(width = ringStep, height = 8.dp)) {
                    val y = size.height / 2
                    val stroke = 2.dp.toPx()
                    drawLine(DeepDarkGreen, Offset(0f, y), Offset(size.width, y), stroke)
                    drawLine(DeepDarkGreen, Offset(0f, 0f), Offset(0f, size.height), stroke)
                    drawLine(DeepDarkGreen, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "200 m",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepDarkGreen
                )
            }
        }
    }
}

/** Parks, a river and a road grid, as fractions of the panel so every screen size matches. */
private fun DrawScope.drawOfflineMapBase() {
    val w = size.width
    val h = size.height

    // Parks
    drawRoundRect(
        color = MapPark,
        topLeft = Offset(w * 0.06f, h * 0.07f),
        size = Size(w * 0.24f, h * 0.20f),
        cornerRadius = CornerRadius(18.dp.toPx())
    )
    drawOval(
        color = MapPark,
        topLeft = Offset(w * 0.60f, h * 0.54f),
        size = Size(w * 0.30f, h * 0.18f)
    )

    // River
    val river = Path().apply {
        moveTo(-20f, h * 0.60f)
        cubicTo(w * 0.30f, h * 0.50f, w * 0.55f, h * 0.98f, w + 20f, h * 0.86f)
    }
    drawPath(river, MapWater, style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round))

    // Roads: light casing under a white fill, major then minor
    fun road(from: Offset, to: Offset, width: Float) {
        drawLine(MapRoadCasing, from, to, width + 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(MapRoad, from, to, width, cap = StrokeCap.Round)
    }
    val major = 7.dp.toPx()
    val minor = 3.5.dp.toPx()
    road(Offset(0f, h * 0.44f), Offset(w, h * 0.40f), major)
    road(Offset(w * 0.58f, 0f), Offset(w * 0.54f, h), major)
    road(Offset(w * 0.08f, h), Offset(w * 0.44f, 0f), minor)
    road(Offset(w * 0.54f, h * 0.20f), Offset(w, h * 0.22f), minor)
    road(Offset(0f, h * 0.30f), Offset(w * 0.40f, h * 0.28f), minor)
    road(Offset(w * 0.20f, h * 0.44f), Offset(w * 0.18f, h * 0.62f), minor)
    road(Offset(w * 0.56f, h * 0.66f), Offset(w, h * 0.64f), minor)
}

private val MarkerWidth = 76.dp
private val MarkerPinRadius = 15.dp

@Composable
private fun RingLabel(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MapLand.copy(alpha = 0.9f)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeepDarkGreen.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun MapNameChip(text: String, selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (selected) DeepDarkGreen else Color.White,
            border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
        ) {
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else DeepDarkGreen,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Teammate marker: initials in a status-coloured pin (centred on the teammate's position),
 * name underneath. The selected teammate gets a ring and a dark name chip.
 */
@Composable
private fun InteractiveMapMarker(
    member: TeamMemberLocation,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(MarkerWidth)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(MarkerPinRadius * 2),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(MarkerPinRadius * 2)
                        .clip(CircleShape)
                        .background(member.markerColor.copy(alpha = 0.25f))
                )
            }
            Box(
                modifier = Modifier
                    .size(if (isSelected) 26.dp else 24.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(member.markerColor)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.initials,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        MapNameChip(text = member.name, selected = isSelected)
    }
}

/**
 * 4. Legend Section
 */
@Composable
private fun MapLegendSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Green <5m
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ActiveGreen)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "<5m active",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }

        // Orange <20m
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(RecentOrange)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "<20m active",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }

        // Gray/Blue >20m
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(InactiveGrey)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = ">20m inactive",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Team List View Mode
 */
@Composable
private fun TeamListViewSection(
    teamMembers: List<TeamMemberLocation>,
    selectedMember: TeamMemberLocation,
    onMemberSelected: (TeamMemberLocation) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        teamMembers.forEach { member ->
            val isSelected = member.id == selectedMember.id

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onMemberSelected(member) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) SoftLightGreen.copy(alpha = 0.25f) else Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) DeepDarkGreen else GrayBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(member.markerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member.initials,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepDarkGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Last seen ${member.lastSeenText} • ${member.distanceText}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(member.statusDotColor)
                    )
                }
            }
        }
    }
}

/**
 * 5. Selected Member Card Section
 */
@Composable
private fun SelectedMemberCardSection(member: TeamMemberLocation) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Member Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(member.markerColor)
                        .border(2.dp, SoftLightGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.initials,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = member.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepDarkGreen
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last seen ${member.lastSeenText} • ${member.distanceText}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Message & Directions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Message Button
                Button(
                    onClick = { /* Message Member Action */ },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepDarkGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Message",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Message",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Directions Button
                OutlinedButton(
                    onClick = { /* Directions Action */ },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DeepDarkGreen
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DeepDarkGreen)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Directions,
                        contentDescription = "Directions",
                        tint = DeepDarkGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Directions",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 6. Card Footer Section
 */
@Composable
private fun LocationFooterSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "4 team members connected",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Tap a pin for details",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LocationScreenPreview() {
    LocationScreen()
}

/**
 * Priority alert from the team
 */
@Composable
private fun PriorityAlertSection() {
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
                    imageVector = Icons.Rounded.Warning,
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

