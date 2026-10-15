package mx.sjf.tesis.ui.screens.lector

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.sjf.tesis.data.util.CitationBuilder
import mx.sjf.tesis.data.util.autorDeVoto
import mx.sjf.tesis.data.util.textoPlano
import mx.sjf.tesis.pdf.DocumentosPdf
import mx.sjf.tesis.ui.components.SectionLabel
import mx.sjf.tesis.viewmodel.Lectura

/** Lo que el lector muestra de una ejecutoria o un voto. */
private class Contenido(
    val asunto: String,
    val localizacion: String,
    val publicacion: String,
    val rubro: String,
    val seccion: String,
    val parrafos: List<String>
)

private fun parrafos(html: String) =
    textoPlano(html).split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

private fun contenido(l: Lectura): Contenido? = when (l) {
    is Lectura.DeEjecutoria -> with(l.ejecutoria) {
        Contenido(asunto, localizacion, publicacion, rubro, "Sentencia", parrafos(texto))
    }
    is Lectura.DeVoto -> with(l.voto) {
        // El primer párrafo del texto oficial repite el título: no se duplica.
        val p = parrafos(texto).let { if (it.firstOrNull()?.trimEnd('.') == titulo) it.drop(1) else it }
        Contenido(if (tipo.isNotBlank()) autorDeVoto(titulo) else titulo, "", publicacion, "", "Voto", p)
    }
    is Lectura.Cargando, is Lectura.Fallo -> null
}

/**
 * Lector de pantalla completa para una ejecutoria (sentencia) o un voto.
 * Las ejecutorias pueden pasar de 150 000 caracteres, así que el texto se
 * divide en párrafos y se dibuja con una lista perezosa: solo se componen los
 * que están en pantalla. También muestra la descarga de un documento abierto
 * desde un enlace, y su error.
 */
@Composable
fun LectorDocumento(
    lectura: Lectura,
    exportando: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    snackbarHost: @Composable () -> Unit
) {
    val context = LocalContext.current
    val c = remember(lectura) { contenido(lectura) }
    val titulo = when (lectura) {
        is Lectura.DeVoto -> lectura.voto.tipo.takeIf { it.isNotBlank() }?.let { "Voto $it" } ?: "Voto"
        else -> lectura.tipo.nombre
    }

    fun abrirOficial() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lectura.url))) }
    }

    fun compartir() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, c?.asunto?.ifBlank { null } ?: "$titulo ${lectura.registro}")
            putExtra(
                Intent.EXTRA_TEXT,
                listOf(titulo, c?.asunto.orEmpty(), c?.localizacion.orEmpty(), "Registro digital: ${lectura.registro}", lectura.url)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            )
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Compartir")) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Barra superior: cerrar · título · descargar · compartir · sitio oficial.
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Cerrar") }
                    Column(Modifier.weight(1f)) {
                        Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Registro digital ${lectura.registro}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (c != null) {
                        if (exportando) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, "Descargar PDF") }
                        }
                        IconButton(onClick = ::compartir) { Icon(Icons.Outlined.Share, "Compartir") }
                    }
                    IconButton(onClick = ::abrirOficial) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Ver en el sitio oficial")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

                Box(Modifier.weight(1f)) {
                    when {
                        c != null -> Texto(c, exportando, onDownload, ::abrirOficial)
                        lectura is Lectura.Fallo -> Aviso(
                            "No se pudo abrir el documento",
                            "Revisa tu conexión a internet. También puedes consultarlo en el sitio oficial.",
                            cargando = false,
                            onRetry = onRetry,
                            onOficial = ::abrirOficial
                        )
                        else -> Aviso(
                            "Obteniendo el documento…",
                            "Consultando el Semanario Judicial de la Federación.",
                            cargando = true
                        )
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
                }
            }
        }
    }
}

@Composable
private fun Texto(c: Contenido, exportando: Boolean, onDownload: () -> Unit, onOficial: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item(key = "encabezado") {
            SelectionContainer {
                Column {
                    if (c.asunto.isNotBlank()) {
                        Text(c.asunto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (c.localizacion.isNotBlank()) {
                        Text(
                            c.localizacion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CitationBuilder.fechaDePublicacion(c.publicacion)?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Publicado el $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (c.rubro.isNotBlank()) {
                        SectionLabel("Tesis relacionada")
                        Text(
                            c.rubro,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SectionLabel(c.seccion)
                }
            }
        }
        items(c.parrafos) { parrafo ->
            SelectionContainer {
                // Encabezados de la sentencia («RESULTANDO», «CONSIDERANDO»):
                // centrados y en negrita, igual que en el PDF.
                val encabezado = DocumentosPdf.esEncabezado(parrafo)
                Text(
                    parrafo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (encabezado) FontWeight.SemiBold else null,
                    textAlign = if (encabezado) TextAlign.Center else TextAlign.Justify,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
        }
        item(key = "pie") {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDownload, enabled = !exportando, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (exportando) "Generando PDF…" else "Descargar PDF")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOficial, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ver en el sitio oficial")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Aviso(
    titulo: String,
    detalle: String,
    cargando: Boolean,
    onRetry: (() -> Unit)? = null,
    onOficial: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (cargando) CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
        else Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text("Reintentar") }
        }
        if (onOficial != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOficial) { Text("Ver en el sitio oficial") }
        }
    }
}
