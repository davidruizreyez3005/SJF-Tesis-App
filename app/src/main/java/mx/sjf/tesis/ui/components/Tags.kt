package mx.sjf.tesis.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sjf.tesis.ui.theme.Gold
import mx.sjf.tesis.ui.theme.InfoBlue
import mx.sjf.tesis.ui.theme.SuccessGreen
import mx.sjf.tesis.ui.theme.WarnYellow
import mx.sjf.tesis.ui.theme.semanticColor

/** Color del tag según tipo: Jurisprudencia → dorado, Aislada → verde. */
fun colorTipo(tipo: String): Color =
    if (tipo.contains("Jurisprudencia", true)) Gold else SuccessGreen

/** Tag pequeño con color e ícono para metadatos. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = semanticColor(color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Tag compacto con prefijo opcional. */
@Composable
fun LabeledTag(label: String, value: String, color: Color) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = semanticColor(color), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, color = semanticColor(color), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Conjunto estándar de tags para una tesis. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TesisTagRow(
    epoca: String,
    instancia: String,
    tipo: String,
    materia: String,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Tag(epoca, InfoBlue)
        Tag(instancia, Gold)
        Tag(tipo, colorTipo(tipo))
        Tag(materia, WarnYellow)
    }
}

/** Línea decorativa dorada para separar secciones premium. */
@Composable
fun GoldDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Gold.copy(alpha = 0.55f), Color.Transparent)
                )
            )
    )
}

/** Etiqueta de sección con barra dorada lateral. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(16.dp).height(2.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2f.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
