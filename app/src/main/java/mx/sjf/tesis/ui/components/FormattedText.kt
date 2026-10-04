package mx.sjf.tesis.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import mx.sjf.tesis.data.util.foldAccents
import mx.sjf.tesis.data.util.parrafosDeTexto

/** Construye un AnnotatedString con resaltado de términos de búsqueda. */
fun buildHighlighted(text: String, query: String, color: Color, background: Color): AnnotatedString {
    val terms = query.lowercase().split(Regex("\\s+")).map { it.foldAccents() }.filter { it.length >= 3 }.distinct()
    if (terms.isEmpty() || text.isBlank()) return AnnotatedString(text)
    val hay = text.foldAccents().lowercase()
    val ranges = mutableListOf<Pair<Int, Int>>()
    for (term in terms) {
        var index = hay.indexOf(term)
        while (index >= 0) {
            ranges += index to (index + term.length)
            index = hay.indexOf(term, index + term.length)
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)
    val merged = mutableListOf<Pair<Int, Int>>()
    ranges.sortedBy { it.first }.forEach { range ->
        val last = merged.lastOrNull()
        if (last != null && range.first < last.second) merged[merged.size - 1] = last.first to maxOf(last.second, range.second)
        else merged += range
    }
    return buildAnnotatedString {
        var cursor = 0
        for ((start, end) in merged) {
            append(text.substring(cursor, start))
            withStyle(SpanStyle(color = color, background = background, textDecoration = TextDecoration.Underline)) {
                append(text.substring(start, end))
            }
            cursor = end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

/** Texto con resaltado de términos de búsqueda. */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val color = MaterialTheme.colorScheme.primary
    val background = color.copy(alpha = 0.18f)
    val annotated = remember(text, query, color, background) { buildHighlighted(text, query, color, background) }
    Text(annotated, style = style, modifier = modifier, maxLines = maxLines, overflow = overflow)
}

/** Etiquetas de párrafo que se ponen en negritas. */
private val ETIQUETAS_PARRAFO = listOf(
    "Hechos:", "Criterio jurídico:", "Justificación:",
    "Precedentes:", "Votos:", "Ejecutorias:"
)

/** Texto formateado: párrafos, enlaces y etiquetas de sección resaltadas. */
@Composable
fun TextoFormateado(texto: String, style: TextStyle, modifier: Modifier = Modifier) {
    val colorEnlace = MaterialTheme.colorScheme.secondary
    // Las etiquetas «Hechos:», «Criterio jurídico:», «Justificación:» de las
    // tesis de la 11a. y 12a. Época se destacan para leer la estructura de un vistazo.
    val colorEtiqueta = MaterialTheme.colorScheme.primary
    val annotated = remember(texto, colorEnlace, colorEtiqueta) {
        buildAnnotatedString {
            val parrafos = parrafosDeTexto(texto)
            parrafos.forEachIndexed { i, p ->
                if (i > 0) append("\n\n")
                var restantes = p
                val primero = p.firstOrNull()
                if (primero != null && !primero.esEnlace) {
                    val etiqueta = ETIQUETAS_PARRAFO.firstOrNull { primero.texto.startsWith(it, ignoreCase = true) }
                    if (etiqueta != null) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colorEtiqueta)) { append(primero.texto.take(etiqueta.length)) }
                        append(primero.texto.substring(etiqueta.length))
                        restantes = p.drop(1)
                    }
                }
                restantes.forEach { f ->
                    if (f.esEnlace) {
                        withStyle(SpanStyle(color = colorEnlace, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)) {
                            append(f.texto)
                        }
                    } else append(f.texto)
                }
            }
        }
    }
    SelectionContainer {
        Text(annotated, style = style, modifier = modifier, textAlign = TextAlign.Justify)
    }
}
