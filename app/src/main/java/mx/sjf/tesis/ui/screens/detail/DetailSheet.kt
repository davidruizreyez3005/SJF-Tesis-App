package mx.sjf.tesis.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import mx.sjf.tesis.data.model.Ejecutoria
import mx.sjf.tesis.data.model.Voto
import mx.sjf.tesis.data.util.dividirPrecedentes
import mx.sjf.tesis.data.util.textoPlano
import mx.sjf.tesis.viewmodel.DocumentosUi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import androidx.compose.ui.text.style.TextDecoration
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
    val votosUi by vm.votos.collectAsState()
    val ejecutoriasUi by vm.ejecutorias.collectAsState()
    val isSaved = saved.any { it.registro == tesis.registro }
    val isExporting = batchState is BatchState.Progress
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val url = tesis.urlDetalle

    fun openUrl(destino: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destino))) }
            .onFailure { vm.notify("No se encontró un navegador para abrir el enlace") }
    }

    fun openOriginal() = openUrl(url)

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

                EjecutoriasSection(ejecutoriasUi, onOpen = vm::abrirEjecutoria, onRetry = vm::retryRelacionados)

                if (tesis.precedente.isNotBlank()) {
                    PrecedentesSection(tesis.precedente)
                }

                VotosSection(votosUi, onRetry = vm::retryRelacionados, onOpenUrl = ::openUrl, onDownload = vm::exportVoto)

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

                // Con precedentes o votos, el PDF ofrece también el expediente
                // completo (tesis + ejecutorias + votos en un solo archivo).
                val conRelacionados = tesis.ejecutorias.isNotEmpty() || tesis.votos.isNotEmpty()
                var menuPdf by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    FooterButton(
                        if (isExporting) "Generando" else "PDF",
                        Icons.Outlined.PictureAsPdf,
                        primary = true,
                        loading = isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isExporting) return@FooterButton
                        if (conRelacionados) menuPdf = true else vm.exportSingle(tesis)
                    }
                    DropdownMenu(expanded = menuPdf, onDismissRequest = { menuPdf = false }) {
                        DropdownMenuItem(
                            text = { MenuPdfTexto("Solo la tesis", "Ficha, texto y precedentes") },
                            leadingIcon = { Icon(Icons.Outlined.Description, null) },
                            onClick = { menuPdf = false; vm.exportSingle(tesis) }
                        )
                        DropdownMenuItem(
                            text = {
                                MenuPdfTexto(
                                    "Expediente completo",
                                    listOfNotNull(
                                        "Tesis",
                                        tesis.ejecutorias.size.takeIf { it > 0 }?.let { if (it == 1) "ejecutoria" else "$it ejecutorias" },
                                        tesis.votos.size.takeIf { it > 0 }?.let { if (it == 1) "voto" else "$it votos" }
                                    ).joinToString(" + ") + ", con índice"
                                )
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.LibraryBooks, null) },
                            onClick = { menuPdf = false; vm.exportExpediente(tesis) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * «Precedente(s) de la tesis», como en el Semanario: las ejecutorias de las
 * que deriva la tesis, numeradas, con su registro y localización. Tocar una
 * abre la sentencia completa.
 */
@Composable
private fun EjecutoriasSection(
    ui: DocumentosUi<Ejecutoria>,
    onOpen: (Ejecutoria) -> Unit,
    onRetry: () -> Unit
) {
    val titulo = { n: Int -> if (n == 1) "Precedente de la tesis" else "Precedentes de la tesis ($n)" }
    when (ui) {
        DocumentosUi.Ninguno -> return
        is DocumentosUi.Cargando -> {
            SectionLabel(titulo(ui.cuantos))
            CargandoFila(if (ui.cuantos == 1) "Cargando el precedente…" else "Cargando ${ui.cuantos} precedentes…")
        }
        is DocumentosUi.Error -> {
            SectionLabel(titulo(ui.cuantos))
            ErrorFila("No se pudieron cargar los precedentes.", onRetry)
        }
        is DocumentosUi.Listos -> {
            if (ui.documentos.isEmpty()) return
            SectionLabel(titulo(ui.documentos.size))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ui.documentos.forEachIndexed { i, e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { onOpen(e) }
                            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${i + 1}.  Registro ${e.registro}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(e.localizacion, style = MaterialTheme.typography.bodyMedium)
                            if (e.asunto.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    e.asunto,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = "Leer la ejecutoria",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CargandoFila(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorFila(texto: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

/**
 * Asuntos que integran la tesis: lista numerada (el
 * asunto destacado y, debajo, fecha, votación y ponente), seguidos de las
 * notas oficiales (aprobación, notas, criterios contendientes).
 */
@Composable
private fun PrecedentesSection(precedente: String) {
    val divididos = remember(precedente) { dividirPrecedentes(precedente) }
    val asuntos = divididos.asuntos
    SectionLabel(if (asuntos.size > 1) "Asuntos que integran la tesis (${asuntos.size})" else "Asunto que integra la tesis")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        asuntos.forEachIndexed { i, p ->
            Row {
                if (asuntos.size > 1) {
                    Text(
                        "${i + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(24.dp)
                    )
                }
                SelectionContainer {
                    Column {
                        Text(p.asunto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (p.detalle.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                p.detalle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (divididos.notas.isNotEmpty()) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    divididos.notas.forEach { nota ->
                        Text(
                            nota,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Votos publicados con la tesis; cada uno se despliega para leerlo completo. */
@Composable
private fun VotosSection(
    ui: DocumentosUi<Voto>,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownload: (Voto) -> Unit
) {
    when (ui) {
        DocumentosUi.Ninguno -> return
        is DocumentosUi.Cargando -> {
            SectionLabel("Votos (${ui.cuantos})")
            CargandoFila(if (ui.cuantos == 1) "Cargando el voto…" else "Cargando ${ui.cuantos} votos…")
        }
        is DocumentosUi.Error -> {
            SectionLabel("Votos (${ui.cuantos})")
            ErrorFila("No se pudieron cargar los votos.", onRetry)
        }
        is DocumentosUi.Listos -> {
            if (ui.documentos.isEmpty()) return
            SectionLabel("Votos (${ui.documentos.size})")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ui.documentos.forEach { VotoCard(it, onOpenUrl, onDownload) }
            }
        }
    }
}

@Composable
private fun VotoCard(voto: Voto, onOpenUrl: (String) -> Unit, onDownload: (Voto) -> Unit) {
    var expanded by rememberSaveable(voto.registro) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "votoChevron")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (voto.tipo.isNotBlank()) {
                    Text(
                        "Voto ${voto.tipo}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    voto.titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Ocultar voto" else "Leer voto",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                // El primer párrafo del texto oficial es el título que ya se ve
                // en el encabezado de la tarjeta: no se repite.
                val cuerpo = remember(voto.texto) {
                    val parrafos = textoPlano(voto.texto).split(Regex("\\n\\s*\\n"))
                    val sinTitulo = if (parrafos.firstOrNull()?.trim()?.trimEnd('.') == voto.titulo) parrafos.drop(1) else parrafos
                    sinTitulo.joinToString("\n\n")
                }
                TextoFormateado(cuerpo, MaterialTheme.typography.bodyMedium)
                CitationBuilder.fechaDePublicacion(voto.publicacion)?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Publicado el $it en el Semanario Judicial de la Federación · Registro digital ${voto.registro}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { onDownload(voto) }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Outlined.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Descargar PDF")
                    }
                    TextButton(onClick = { onOpenUrl(voto.urlDetalle) }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sitio oficial")
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPdfTexto(titulo: String, detalle: String) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
