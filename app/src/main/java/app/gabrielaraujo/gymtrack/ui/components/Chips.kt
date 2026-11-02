package app.gabrielaraujo.gymtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gabrielaraujo.gymtrack.ui.theme.TC

/** Chip selecionável: roxo quando ativo, como as abas do app. */
@Composable
fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) TC.White else TC.Slate400,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) TC.Indigo600 else TC.Slate800)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Linha de chips de escolha única, com rolagem horizontal quando não cabe. */
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyRow(modifier.fillMaxWidth(), contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option -> Chip(label(option), option == selected) { onSelect(option) } }
    }
}

/** Abas em faixa escura, como as do Progresso; com rolagem quando são muitas. */
@Composable
fun <T> SegmentedTabs(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(options) { option ->
            val sel = option == selected
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) TC.Indigo600 else Color.Transparent)
                    .clickable { onSelect(option) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label(option), color = if (sel) TC.White else TC.Slate400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
