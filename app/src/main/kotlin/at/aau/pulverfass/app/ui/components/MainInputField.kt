package at.aau.pulverfass.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassFonts

/**
 * Pulverfass-Standard-Input mit Wood-Skin Background.
 * Verwendet `main_input_design.png` als stretchable BG.
 */
@Composable
fun MainInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    fontSize: Int = 18,
) {
    Box(
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.main_input_design),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(PulverfassColors.GoldBright),
            textStyle =
                TextStyle(
                    color = PulverfassColors.Parchment,
                    fontSize = fontSize.sp,
                    fontFamily = PulverfassFonts.CinzelDecorative,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            decorationBox = { innerField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = PulverfassColors.Parchment.copy(alpha = 0.5f),
                            fontFamily = PulverfassFonts.CinzelDecorative,
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    innerField()
                }
            },
        )
    }
}
