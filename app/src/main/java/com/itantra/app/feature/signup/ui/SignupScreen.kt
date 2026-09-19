package com.itantra.app.feature.signup.ui

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import com.itantra.app.R
import com.itantra.app.feature.signup.SignupViewModel
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onContinue: () -> Unit = {},
    viewModel: SignupViewModel = hiltViewModel()
) {
    var username by remember { mutableStateOf("") }
    var selectedLanguages by remember {
        mutableStateOf(setOf<String>())
    }
    var languageDropdownExpanded by remember { mutableStateOf(false) }

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

        ExposedDropdownMenuBox(
            expanded = languageDropdownExpanded,
            onExpandedChange = {
                languageDropdownExpanded = !languageDropdownExpanded
            },
            modifier = Modifier.fillMaxWidth()
        ) {

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
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = languageDropdownExpanded
                    )
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),

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
                expanded = languageDropdownExpanded,
                onDismissRequest = {
                    languageDropdownExpanded = false
                },
                modifier = Modifier
                    .background(Color.White)
            ) {

                indianLanguages.forEach { language ->

                    val isSelected = selectedLanguages.contains(language)

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
                                Text(
                                    text = "✓",
                                    color = DeepDarkGreen,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },

                        onClick = {

                            selectedLanguages =
                                if (isSelected) {
                                    selectedLanguages - language
                                } else {
                                    selectedLanguages + language
                                }
                        },

                        modifier = Modifier.background(
                            if (isSelected) {
                                Color(0xFFE8F0E9)
                            } else {
                                Color.White
                            }
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Continue Button
        Button(
            onClick = {
                if (username.isNotBlank()) {
                    viewModel.saveUsername(username)
                }
                onContinue()
            },
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

@Preview(showBackground = true)
@Composable
fun SignupScreenPreview() {
    SignupScreen()
}