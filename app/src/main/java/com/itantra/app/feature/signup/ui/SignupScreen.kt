package com.itantra.app.feature.signup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itantra.app.core.prefs.UserProfile
import com.itantra.app.feature.signup.SignupViewModel
import com.itantra.app.ui.components.ItantraLogo
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen

@Composable
fun SignupScreen(
    viewModel: SignupViewModel = hiltViewModel(),
    onContinue: () -> Unit = {}
) {
    var username by rememberSaveable { mutableStateOf("") }
    var selectedLanguages by rememberSaveable { mutableStateOf(listOf<String>()) }
    val profile = UserProfile(username, selectedLanguages.toSet())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .statusBarsPadding()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo / Branding
        ItantraLogo(height = 64.dp)

        Spacer(modifier = Modifier.height(24.dp))

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
            textStyle = TextStyle(
                color = DeepDarkGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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

        LanguagePicker(
            selected = selectedLanguages.toSet(),
            onSelectedChange = { chosen -> selectedLanguages = IndianLanguages.filter { it in chosen } }
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Continue Button: only with a name and at least one language
        Button(
            onClick = { viewModel.save(profile, onSaved = onContinue) },
            enabled = profile.isComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DeepDarkGreen,
                contentColor = Color.White,
                disabledContainerColor = DeepDarkGreen.copy(alpha = 0.25f),
                disabledContentColor = Color.White
            )
        ) {
            Text(
                text = "Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = when {
                username.isBlank() && selectedLanguages.isEmpty() -> "Enter your name and pick at least one language."
                username.isBlank() -> "Enter your name to continue."
                selectedLanguages.isEmpty() -> "Pick at least one language to continue."
                else -> ""
            },
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
