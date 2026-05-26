package at.aau.pulverfass.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.app.ui.theme.PulverfassColors

/**
 * Gold-gerahmtes Wood-Texture-Input im Pulverfass-Theme.
 * Placeholder zentriert, Text zentriert, gold Cursor.
 */
@Composable
fun PulverfassInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    fontSize: Int = 18,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = PulverfassColors.TextMuted,
                fontSize = fontSize.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        textStyle =
            LocalTextStyle.current.copy(
                color = PulverfassColors.Gold,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
            ),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PulverfassColors.GoldBright,
                unfocusedBorderColor = PulverfassColors.GoldDark,
                focusedContainerColor = PulverfassColors.SurfaceWood,
                unfocusedContainerColor = PulverfassColors.SurfaceWood,
                cursorColor = PulverfassColors.GoldBright,
            ),
    )
}
