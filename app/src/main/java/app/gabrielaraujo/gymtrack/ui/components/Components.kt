package app.gabrielaraujo.gymtrack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gabrielaraujo.gymtrack.ui.theme.TC

/** Cartão escuro reutilizável (equiv. ao <Card> do web). */
@Composable
fun GymTrackCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var m = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(TC.Slate900)
        .border(1.dp, TC.Slate800, RoundedCornerShape(16.dp))
    if (onClick != null) m = m.clickable { onClick() }
    Box(m.padding(16.dp)) { content() }
}

enum class BtnVariant { Primary, Success, Danger, Ghost }

@Composable
fun GymTrackButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BtnVariant = BtnVariant.Primary,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    val bg = when (variant) {
        BtnVariant.Primary -> TC.Indigo600
        BtnVariant.Success -> TC.Emerald600
        BtnVariant.Danger -> TC.Red600
        BtnVariant.Ghost -> Color.Transparent
    }
    val fg = when (variant) {
        BtnVariant.Ghost -> TC.Slate300
        else -> TC.White
    }
    var m = modifier
        .clip(RoundedCornerShape(14.dp))
        .background(if (enabled) bg else bg.copy(alpha = 0.4f))
    if (variant == BtnVariant.Ghost) m = m.border(1.dp, TC.Slate700, RoundedCornerShape(14.dp))
    if (enabled) m = m.clickable { onClick() }
    Box(m.padding(vertical = 14.dp, horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/** Stepper numérico com −/+ (equiv. ao <NumberInput>). */
@Composable
fun NumberStepper(
    value: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    min: Double = 0.0,
    max: Double = 9999.0,
    isInt: Boolean = false,
) {
    fun fmt(v: Double) = if (isInt || v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    // Texto local editável; ressincroniza quando o valor muda por fora (ex.: botões +/-).
    var text by remember(value) { mutableStateOf(fmt(value)) }

    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TC.Slate800)
            .border(1.dp, TC.Slate700, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepBtn(Icons.Default.Remove) { onChange((value - step).coerceIn(min, max)) }
        BasicTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.replace(',', '.').toDoubleOrNull()?.let { onChange(it.coerceIn(min, max)) }
            },
            singleLine = true,
            textStyle = TextStyle(color = TC.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(TC.Indigo400),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isInt) KeyboardType.Number else KeyboardType.Decimal,
            ),
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        StepBtn(Icons.Default.Add) { onChange((value + step).coerceIn(min, max)) }
    }
}

@Composable
private fun StepBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = TC.Slate300, modifier = Modifier.size(18.dp))
    }
}

/** Barra de progresso fina. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = TC.Indigo500, track: Color = TC.Slate800) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = TC.Slate400) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}
