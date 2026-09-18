package com.itantra.app.feature.location.ui

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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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

val defaultTeamMembers = listOf(
    TeamMemberLocation(
        id = "you",
        name = "You",
        initials = "YOU",
        markerColor = Color(0xFF00E5FF), // Cyan
        lastSeenText = "Active now",
        distanceText = "0m",
        statusDotColor = Color(0xFF4CAF50),
        mapPercentX = 0.48f,
        mapPercentY = 0.45f
    ),
    TeamMemberLocation(
        id = "amit",
        name = "Amit K.",
        initials = "A",
        markerColor = Color(0xFF4CAF50), // Green
        lastSeenText = "2 min ago",
        distanceText = "240m away",
        statusDotColor = Color(0xFF4CAF50),
        mapPercentX = 0.28f,
        mapPercentY = 0.30f
    ),
    TeamMemberLocation(
        id = "rhea",
        name = "Rhea S.",
        initials = "R",
        markerColor = Color(0xFF4CAF50), // Green
        lastSeenText = "4 min ago",
        distanceText = "480m away",
        statusDotColor = Color(0xFF4CAF50),
        mapPercentX = 0.74f,
        mapPercentY = 0.25f
    ),
    TeamMemberLocation(
        id = "zain",
        name = "Zain M.",
        initials = "Z",
        markerColor = Color(0xFFFF9800), // Orange
        lastSeenText = "14 min ago",
        distanceText = "1.2km away",
        statusDotColor = Color(0xFFFF9800),
        mapPercentX = 0.22f,
        mapPercentY = 0.64f
    ),
    TeamMemberLocation(
        id = "oli",
        name = "Oli T.",
        initials = "O",
        markerColor = Color(0xFF9E9E9E), // Gray
        lastSeenText = "35 min ago",
        distanceText = "3.5km away",
        statusDotColor = Color(0xFF9E9E9E),
        mapPercentX = 0.78f,
        mapPercentY = 0.74f
    )
)

@Composable
fun LocationScreen() {
    var selectedViewMode by remember { mutableStateOf("Map") }
    var selectedMember by remember { mutableStateOf(defaultTeamMembers[1]) } // Amit K. selected by default

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header Section
            LocationHeaderSection()

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
                imageVector = Icons.Default.Refresh,
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
 * 3. Large Map Panel Section (Dark Grid with 5 markers + curved blue route line)
 */
@Composable
private fun LargeMapPanelSection(
    teamMembers: List<TeamMemberLocation>,
    selectedMember: TeamMemberLocation,
    onMemberSelected: (TeamMemberLocation) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .shadow(6.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // Dark slate grid background
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Canvas for Dark Grid & Curved Blue Route/Terrain Line
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridColor = Color(0xFF334155).copy(alpha = 0.6f)
                val gridSpacing = 40.dp.toPx()

                // Draw vertical grid lines
                var x = gridSpacing
                while (x < size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += gridSpacing
                }

                // Draw horizontal grid lines
                var y = gridSpacing
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += gridSpacing
                }

                // Curved Blue Route / Terrain Line near the bottom
                val path = Path().apply {
                    moveTo(0f, size.height * 0.78f)
                    cubicTo(
                        size.width * 0.3f, size.height * 0.65f,
                        size.width * 0.65f, size.height * 0.92f,
                        size.width, size.height * 0.72f
                    )
                }
                drawPath(
                    path = path,
                    color = Color(0xFF38BDF8), // Curved blue line
                    style = Stroke(width = 4.5f)
                )
            }

            // Render 5 Team Markers
            teamMembers.forEach { member ->
                val isSelected = member.id == selectedMember.id

                InteractiveMapMarker(
                    member = member,
                    isSelected = isSelected,
                    onClick = { onMemberSelected(member) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = (member.mapPercentX * 280).dp, // offset based on container scale
                            y = (member.mapPercentY * 230).dp
                        )
                )
            }
        }
    }
}

/**
 * Interactive Map Marker with Name Tag Above
 */
@Composable
private fun InteractiveMapMarker(
    member: TeamMemberLocation,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Name Tag Above Marker
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) member.markerColor else Color(0xFF0F172A).copy(alpha = 0.85f),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.White) else null,
            shadowElevation = 3.dp
        ) {
            Text(
                text = member.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected && member.markerColor == Color(0xFF00E5FF)) Color.Black else Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Circular Marker Pin
        Box(contentAlignment = Alignment.Center) {
            // Glow aura for selected pin
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(member.markerColor.copy(alpha = 0.35f))
                )
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(member.markerColor)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.initials,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (member.markerColor == Color(0xFF00E5FF)) Color.Black else Color.White
                )
            }
        }
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
                    .background(Color(0xFF4CAF50))
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
                    .background(Color(0xFFFF9800))
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
                    .background(Color(0xFF9E9E9E))
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
                            color = if (member.markerColor == Color(0xFF00E5FF)) Color.Black else Color.White
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
                        color = if (member.markerColor == Color(0xFF00E5FF)) Color.Black else Color.White
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
                        imageVector = Icons.Default.Send,
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
                        imageVector = Icons.Default.ArrowForward,
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
