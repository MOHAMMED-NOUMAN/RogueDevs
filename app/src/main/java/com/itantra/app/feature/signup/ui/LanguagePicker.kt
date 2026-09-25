package com.itantra.app.feature.signup.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.SoftLightGreen
import kotlinx.coroutines.launch

/** Languages offered as the user's preferred languages. */
val IndianLanguages = listOf(
    "Hindi", "English", "Telugu", "Tamil", "Kannada", "Malayalam",
    "Marathi", "Bengali", "Gujarati", "Punjabi", "Odia", "Assamese",
    "Urdu", "Kashmiri", "Konkani", "Sanskrit", "Nepali", "Manipuri",
    "Maithili", "Sindhi"
)

/**
 * Field showing the chosen languages; tapping it opens a bottom sheet to tick one or more.
 * (A dropdown kept open for multi-select re-measured and moved on every tick, which jittered.)
 */
@Composable
fun LanguagePicker(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = when {
                selected.isEmpty() -> ""
                selected.size <= 2 -> IndianLanguages.filter { it in selected }.joinToString(", ")
                else -> "${selected.size} languages selected"
            },
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("Preferred Languages", color = Color.Gray) },
            textStyle = TextStyle(
                color = DeepDarkGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.Gray
                )
            },
            singleLine = true,
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
                .clickable(role = Role.Button) { sheetOpen = true }
        )
    }

    if (sheetOpen) {
        LanguagePickerSheet(
            selected = selected,
            onToggle = { language ->
                onSelectedChange(if (language in selected) selected - language else selected + language)
            },
            onDismiss = { sheetOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
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
                items(IndianLanguages, key = { it }) { language ->
                    val checked = language in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(language) }
                            )
                            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
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
