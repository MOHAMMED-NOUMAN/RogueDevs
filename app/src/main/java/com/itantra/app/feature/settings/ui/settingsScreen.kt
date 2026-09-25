package com.itantra.app.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itantra.app.core.permissions.LinkPermissions
import com.itantra.app.core.prefs.EmergencyContact
import com.itantra.app.core.prefs.SpeechLanguage
import com.itantra.app.core.prefs.UserProfile
import com.itantra.app.core.transport.PairingState
import com.itantra.app.feature.pairing.PairingViewModel
import com.itantra.app.feature.settings.SettingsViewModel
import com.itantra.app.feature.signup.ui.LanguagePicker
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen

private val PrimaryGreen = Color(0xFF19B878)
private val PrimaryText = Color(0xFF17231F)
private val SecondaryText = Color(0xFF71807A)
private val CardBackground = Color.White
private val DangerRed = Color(0xFFD32F2F)

/** Which contact the edit dialog is showing: an existing one by index, or a new one. */
private sealed interface ContactEdit {
    data object New : ContactEdit
    data class Existing(val index: Int) : ContactEdit
}

@Composable
fun SettingsScreen(
    pairingViewModel: PairingViewModel,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()
    val speechLanguage by viewModel.speechLanguage.collectAsState()
    val contacts by viewModel.emergencyContacts.collectAsState()
    val pairing by pairingViewModel.pairing.collectAsState()
    val linkRunning by pairingViewModel.linkRunning.collectAsState()

    var editingProfile by remember { mutableStateOf(false) }
    var choosingLanguage by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<ContactEdit?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (LinkPermissions.allRequiredGranted(context)) pairingViewModel.ensureLinkRunning()
    }

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
            onClick = { editingProfile = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardBackground
            ),
            border = BorderStroke(1.dp, GrayBorder)
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
                        text = profile.username.trim().take(1).uppercase(),
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
                        text = profile.username,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = profile.languages.joinToString(", "),
                        fontSize = 11.sp,
                        color = SecondaryText,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Edit profile",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryGreen
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SecondaryText
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ---------------------------------------------------------
        // Preferences
        // ---------------------------------------------------------

        SectionTitle("PREFERENCES")

        Spacer(modifier = Modifier.height(10.dp))

        // Speech language
        SettingsCard(
            title = "Speech Language",
            description = "Language you speak into Hold to Talk",
            onClick = { choosingLanguage = true },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = speechLanguage.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Offline link to the paired teammate
        val paired = pairing is PairingState.Paired
        SettingsCard(
            title = "Offline Link",
            description = if (paired) "Keep the link to your teammate open"
            else "Pair a teammate first (Pair tab)",
            trailingContent = {
                Switch(
                    checked = linkRunning,
                    enabled = paired,
                    onCheckedChange = { on ->
                        when {
                            !on -> pairingViewModel.stopLink()
                            LinkPermissions.allRequiredGranted(context) -> pairingViewModel.ensureLinkRunning()
                            else -> permissionLauncher.launch(LinkPermissions.required + LinkPermissions.optional)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.LightGray,
                        uncheckedBorderColor = Color.LightGray
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

        contacts.forEachIndexed { index, contact ->
            EmergencyContactCard(
                title = contact.name,
                number = contact.number,
                onEdit = { editingContact = ContactEdit.Existing(index) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Add Emergency Number
        Surface(
            onClick = { editingContact = ContactEdit.New },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, PrimaryGreen)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Emergency Number",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull().orEmpty()
        }
        Text(
            text = "iTantra $version · Prototype build",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 10.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center
        )
    }

    if (editingProfile) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { editingProfile = false },
            onSave = {
                viewModel.saveProfile(it)
                editingProfile = false
            }
        )
    }

    if (choosingLanguage) {
        SpeechLanguageDialog(
            current = speechLanguage,
            onDismiss = { choosingLanguage = false },
            onChoose = {
                viewModel.setSpeechLanguage(it)
                choosingLanguage = false
            }
        )
    }

    editingContact?.let { edit ->
        val index = (edit as? ContactEdit.Existing)?.index
        EmergencyContactDialog(
            contact = index?.let { contacts.getOrNull(it) },
            onDismiss = { editingContact = null },
            onSave = {
                viewModel.saveContact(index, it)
                editingContact = null
            },
            onDelete = index?.let {
                {
                    viewModel.deleteContact(it)
                    editingContact = null
                }
            }
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
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        border = BorderStroke(1.dp, GrayBorder)
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
    number: String,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        border = BorderStroke(1.dp, GrayBorder)
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
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(18.dp)
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
                color = PrimaryGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Dialogs
// -----------------------------------------------------------------------------

@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(profile.username) }
    var languages by remember { mutableStateOf(profile.languages) }
    val edited = UserProfile(name, languages)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Edit profile", color = DeepDarkGreen, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Username") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LanguagePicker(selected = languages, onSelectedChange = { languages = it })
                if (!edited.isComplete) {
                    Text(
                        text = "A name and at least one language are needed.",
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(edited) }, enabled = edited.isComplete) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SpeechLanguageDialog(
    current: SpeechLanguage,
    onDismiss: () -> Unit,
    onChoose: (SpeechLanguage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Speech language", color = DeepDarkGreen, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SpeechLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = language == current,
                                role = Role.RadioButton,
                                onClick = { onChoose(language) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == current,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(language.label, fontSize = 15.sp, color = PrimaryText)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "More languages arrive as their offline speech packs are added.",
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun EmergencyContactDialog(
    contact: EmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by rememberSaveable { mutableStateOf(contact?.name.orEmpty()) }
    var number by rememberSaveable { mutableStateOf(contact?.number.orEmpty()) }
    val valid = name.isNotBlank() && number.any(Char::isDigit)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = if (contact == null) "Add emergency number" else "Edit emergency number",
                color = DeepDarkGreen,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { typed -> number = typed.filter { it.isDigit() || it in "+ -" } },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    Text(
                        text = "Delete this number",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDelete)
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(EmergencyContact(name.trim(), number.trim())) },
                enabled = valid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
