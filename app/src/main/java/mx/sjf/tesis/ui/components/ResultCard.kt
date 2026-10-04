package mx.sjf.tesis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.util.formatDateLong
import mx.sjf.tesis.data.util.textoPlano
import mx.sjf.tesis.ui.theme.Gold
import mx.sjf.tesis.ui.theme.GoldLight

/**
 * Tarjeta premium de resultado: barra dorada lateral, tags, rubro destacado,
 * vista previa del texto, registro y fecha, acciones (guardar / eliminar).
 *
 * Soporta modo selección con checkbox circular y **long-press** para entrar a
 * selección desde cualquier tarjeta. La tarjeta responde al toque con una
 * micro-animación de escala (feedback táctil tipo app premium).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ResultCard(
    tesis: Tesis,
    query: String,
    isSaved: Boolean,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val previa = remember(tesis.texto) { textoPlano(tesis.texto) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Micro-animación de escala al presionar — la tarjeta "responde" al dedo.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 800f),
        label = "cardScale"
    )
    val container by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                      else MaterialTheme.colorScheme.surface,
        animationSpec = tween(220), label = "cardColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline,
        animationSpec = tween(220), label = "cardBorder"
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // IntrinsicSize.Min: sin esto la barra lateral (fillMaxHeight) mide 0.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Acento dorado lateral — estilo documento legal.
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Gold, GoldLight)))
            )
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TesisTagRow(
                            epoca = tesis.epoca,
                            instancia = tesis.instancia,
                            tipo = tesis.tipo,
                            materia = tesis.materia
                        )
                    }
                    if (selectionMode) {
                        Spacer(Modifier.width(8.dp))
                        CheckToggle(checked = isSelected)
                    }
                }
                Spacer(Modifier.height(10.dp))
                HighlightedText(
                    text = tesis.rubro.ifBlank { "Tesis sin rubro" },
                    query = query,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (previa.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        previa,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Registro ${tesis.registro}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    if (tesis.fecha.isNotBlank()) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            // Fecha en formato completo «10 de enero del 2014»:
                            // mismo formato que la hoja de detalle y las citas.
                            formatDateLong(tesis.fecha),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else Spacer(Modifier.weight(1f))

                    // En modo selección toda la tarjeta selecciona: sin acciones sueltas.
                    if (!selectionMode) {
                        // offset: alinea el ícono con el borde del texto sin perder área táctil.
                        IconButton(onClick = onBookmark, modifier = Modifier.size(40.dp).offset(x = 10.dp)) {
                            Icon(
                                if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Quitar de Guardadas" else "Guardar",
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
