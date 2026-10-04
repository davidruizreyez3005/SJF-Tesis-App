package mx.sjf.tesis.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.data.model.BatchState
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.util.CitationBuilder
import mx.sjf.tesis.data.util.formatDateLong
import mx.sjf.tesis.ui.components.FooterButton
import mx.sjf.tesis.ui.components.HighlightedText
import mx.sjf.tesis.ui.components.InfoRow
import mx.sjf.tesis.ui.components.SectionLabel
import mx.sjf.tesis.ui.components.TesisTagRow
import mx.sjf.tesis.ui.components.TextoFormateado
import mx.sjf.tesis.viewmodel.AppViewModel

/**
 * Hoja de detalle con todas las acciones: guardar, compartir, citar,
 * generar PDF y abrir el original.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    vm: AppViewModel,
    tesis: Tesis,
    onCitation: () -> Unit,
    snackbarHost: @Composable () -> Unit
) {
    val context = LocalContext.current
    val saved by vm.saved.collectAsState()
    val searchState by vm.search.collectAsState()
    val batchState by vm.batchState.collectAsState()
    val detailLoading by vm.detailLoading.collectAsState()
    val detailError by vm.detailError.collectAsState()
    val isSaved = saved.any { it.registro == tesis.registro }
    val isExporting = batchState is BatchState.Progress
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val url = tesis.urlDetalle

    fun openOriginal() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { vm.notify("No se encontró un navegador para abrir el enlace") }
    }

    ModalBottomSheet(
        onDismissRequest = { vm.closeDetail() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                // Encabezado: clave, rubro completo y etiquetas. Todo se desplaza
                // junto con el texto para que un rubro largo nunca quede cortado.
                if (tesis.clave.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append(if (tesis.esJurisprudencia) "Jurisprudencia  " else "Tesis aislada  ")
                                }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                                    append(tesis.clave)
                                }
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                HighlightedText(
                    text = tesis.rubro.ifBlank { "Tesis sin rubro" },
                    query = searchState.query,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(12.dp))
                TesisTagRow(
                    epoca = tesis.epoca,
                    instancia = tesis.instancia,
                    tipo = tesis.tipo,
                    materia = tesis.materia
                )
                Spacer(Modifier.height(4.dp))
                SectionLabel("Texto")
                when {
                    tesis.texto.isNotBlank() -> TextoFormateado(tesis.texto, MaterialTheme.typography.bodyLarge)
                    detailLoading -> TextPlaceholder(
                        title = "Obteniendo el texto completo…",
                        body = "Consultando el Semanario Judicial de la Federación.",
                        loading = true
                    )
                    detailError -> TextPlaceholder(
                        icon = Icons.Outlined.CloudOff,
                        title = "No se pudo conectar con la SCJN",
                        body = "Revisa tu conexión a internet e inténtalo de nuevo.",
                        actionLabel = "Reintentar",
                        onAction = vm::retryDetail
                    )
                    else -> TextPlaceholder(
                        icon = Icons.Outlined.Info,
                        title = "Texto completo no disponible",
                        body = "La SCJN aún no publica el texto de esta tesis en su servicio de consulta. " +
                            "Es común en tesis recién publicadas; puedes revisar el documento en el sitio oficial.",
                        actionLabel = "Ver en el sitio oficial",
                        onAction = ::openOriginal
                    )
                }

                if (tesis.precedente.isNotBlank()) {
                    SectionLabel("Precedentes")
                    TextoFormateado(tesis.precedente, MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }

                val publicada = CitationBuilder.fechaDePublicacion(tesis.publicacion)
                val obligatoria = CitationBuilder.obligatoriaDesde(tesis.publicacion)
                if (publicada != null || obligatoria != null) {
                    SectionLabel("Publicación")
                    PublicationBox(publicada, obligatoria)
                }

                SectionLabel("Datos de localización")
                InfoRow("Registro digital", tesis.registro.toString())
                InfoRow("Tesis", tesis.clave)
                InfoRow("Época", CitationBuilder.epocaCompleta(tesis.epoca))
                InfoRow("Instancia", tesis.instancia)
                InfoRow("Tipo", tesis.tipo)
                InfoRow("Materia(s)", tesis.materia)
                InfoRow("Fuente", tesis.fuente)
                InfoRow("Libro / Tomo", listOf(tesis.volumen, tesis.tomo).filter { it.isNotBlank() }.joinToString(", "))
                InfoRow("Página", tesis.pagina)
                if (publicada == null) InfoRow("Fecha", formatDateLong(tesis.fecha))
                Spacer(Modifier.height(16.dp))

                // Enlace al documento original en sjf2.scjn.gob.mx.
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable(onClick = ::openOriginal)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ver en el sitio oficial", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "sjf2.scjn.gob.mx/detalle/tesis/${tesis.registro}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            snackbarHost()

            // Barra inferior con 4 acciones: Guardar, Compartir, Citar, PDF.
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FooterButton(
                    label = if (isSaved) "Guardada" else "Guardar",
                    icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    modifier = Modifier.weight(1f)
                ) { vm.toggleSaved(tesis) }

                FooterButton("Compartir", Icons.Outlined.Share, modifier = Modifier.weight(1f)) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, tesis.rubro)
                        putExtra(Intent.EXTRA_TEXT, "${tesis.rubro}\n\nRegistro digital: ${tesis.registro}\n$url")
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, "Compartir tesis"))
                    }.onFailure { vm.notify("No se pudo compartir") }
                }

                FooterButton("Citar", Icons.Outlined.FormatQuote, modifier = Modifier.weight(1f)) {
                    onCitation()
                }

                FooterButton(
                    if (isExporting) "Generando" else "PDF",
                    Icons.Outlined.PictureAsPdf,
                    primary = true,
                    loading = isExporting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!isExporting) vm.exportSingle(tesis)
                }
            }
        }
    }
}

/** Fecha oficial de publicación y, en jurisprudencia, desde cuándo obliga. */
@Composable
private fun PublicationBox(publicada: String?, obligatoria: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (publicada != null) {
            PublicationLine(Icons.Outlined.Event, "Publicada el $publicada en el Semanario Judicial de la Federación.")
        }
        if (obligatoria != null) {
            PublicationLine(Icons.Outlined.Gavel, "De aplicación obligatoria a partir del $obligatoria.")
        }
    }
}

@Composable
private fun PublicationLine(icon: ImageVector, text: String) {
    Row {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Estado del área de texto cuando todavía no hay texto que mostrar. */
@Composable
private fun TextPlaceholder(
    title: String,
    body: String,
    icon: ImageVector? = null,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
        } else if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
