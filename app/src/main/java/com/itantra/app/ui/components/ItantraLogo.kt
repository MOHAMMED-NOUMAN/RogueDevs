package com.itantra.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.R
import com.itantra.app.ui.theme.DeepDarkGreen

/** The iTantra mark followed by the name, sized by the mark's [height]. */
@Composable
fun ItantraLogo(
    height: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(height * 0.28f)
    ) {
        Image(
            painter = painterResource(id = R.drawable.itantra_logo),
            contentDescription = null,
            modifier = Modifier.height(height)
        )
        Text(
            text = "iTantra",
            fontSize = (height.value * 0.62f).sp,
            fontWeight = FontWeight.Bold,
            color = DeepDarkGreen,
            letterSpacing = (-0.3).sp
        )
    }
}

/** Default header size used on the main screens. */
val HeaderLogoHeight: Dp = 34.dp
