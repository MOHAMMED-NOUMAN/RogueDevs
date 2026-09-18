package com.itantra.app.feature.signup.ui

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import com.itantra.app.R
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onContinue: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var selectedLanguages by remember {
        mutableStateOf(setOf<String>())
    }
    var languageSheetOpen by remember { mutableStateOf(false) }

    val indianLanguages = listOf(
        "Hindi", "English", "Telugu", "Tamil", "Kannada", "Malayalam",
        "Marathi", "Bengali", "Gujarati", "Punjabi", "Odia", "Assamese",
        "Urdu", "Kashmiri", "Konkani", "Sanskrit", "Nepali", "Manipuri",
        "Maithili", "Sindhi"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .systemBarsPadding() // shown before Home, so no bottom bar here
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo / Branding
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "iTantra",
            modifier = Modifier
                .width(140.dp)
                .height(100.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Heading
        Text(
            text = "Create your profile",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Set up your account to start communicating offline.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Username Field
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            placeholder = { Text("Username", color = Color.Gray) },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = DeepDarkGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DeepDarkGreen,
                unfocusedTextColor = DeepDarkGreen,
                unfocusedBorderColor = GrayBorder,
                focusedBorderColor = SoftLightGreen,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tapping the field opens a bottom sheet to pick one or more languages.
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = when {
                    selectedLanguages.isEmpty() -> ""
                    selectedLanguages.size <= 2 -> selectedLanguages.joinToString(", ")
                    else -> "${selectedLanguages.size} languages selected"
                },
                onValueChange = {},
                readOnly = true,
                placeholder = {
                    Text(
                        "Preferred Languages",
                        color = Color.Gray
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = DeepDarkGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DeepDarkGreen,
                    unfocusedTextColor = DeepDarkGreen,
                    unfocusedBorderColor = GrayBorder,
                    focusedBorderColor = SoftLightGreen,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )
            // A read-only text field swallows taps, so a transparent layer on top opens the sheet.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button) { languageSheetOpen = true }
            )
        }

        if (languageSheetOpen) {
            LanguagePickerSheet(
                languages = indianLanguages,
                selected = selectedLanguages,
                onToggle = { language ->
                    selectedLanguages =
                        if (language in selectedLanguages) selectedLanguages - language
                        else selectedLanguages + language
                },
                onDismiss = { languageSheetOpen = false }
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Continue Button
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DeepDarkGreen,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    languages: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Preferred languages",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeepDarkGreen,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = if (selected.isEmpty()) "Choose one or more" else "${selected.size} selected",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp)
            )
            HorizontalDivider(color = GrayBorder)

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(languages) { language ->
                    val checked = language in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(language) }
                            )
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = language,
                            fontSize = 16.sp,
                            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                            color = DeepDarkGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null, // the whole row toggles
                            colors = CheckboxDefaults.colors(
                                checkedColor = DeepDarkGreen,
                                uncheckedColor = Color.Gray
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = GrayBorder)
            Button(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepDarkGreen,
                    contentColor = Color.White
                )
            ) {
                Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignupScreenPreview() {
    SignupScreen()
}