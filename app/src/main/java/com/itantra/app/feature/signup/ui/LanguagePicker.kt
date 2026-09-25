package com.itantra.app.feature.signup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.SoftLightGreen

/** Languages offered as the user's preferred languages. */
val IndianLanguages = listOf(
    "Hindi", "English", "Telugu", "Tamil", "Kannada", "Malayalam",
    "Marathi", "Bengali", "Gujarati", "Punjabi", "Odia", "Assamese",
    "Urdu", "Kashmiri", "Konkani", "Sanskrit", "Nepali", "Manipuri",
    "Maithili", "Sindhi"
)

private val SelectedRow = Color(0xFFE8F0E9)

/** Multi-select dropdown of [IndianLanguages]; stays open while languages are toggled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePicker(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
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
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
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

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            IndianLanguages.forEach { language ->
                val isSelected = language in selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language,
                            color = DeepDarkGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = DeepDarkGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    onClick = {
                        onSelectedChange(if (isSelected) selected - language else selected + language)
                    },
                    modifier = Modifier.background(if (isSelected) SelectedRow else Color.White)
                )
            }
        }
    }
}
