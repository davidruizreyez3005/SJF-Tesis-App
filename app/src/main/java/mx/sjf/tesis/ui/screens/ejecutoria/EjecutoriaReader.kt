package mx.sjf.tesis.ui.screens.ejecutoria

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
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
import mx.sjf.tesis.data.model.Ejecutoria
import mx.sjf.tesis.data.util.CitationBuilder
import mx.sjf.tesis.data.util.textoPlano
import mx.sjf.tesis.pdf.DocumentosPdf
import mx.sjf.tesis.ui.components.SectionLabel

/**
 * Lector de pantalla completa para una ejecutoria (sentencia). Las ejecutorias
 * pueden pasar de 150 000 caracteres, así que el texto se divide en párrafos y
 * se dibuja con una lista perezosa: solo se componen los que están en pantalla.
 */
@Composable
fun EjecutoriaReader(
    ejecutoria: Ejecutoria,
    exportando: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    snackbarHost: @Composable () -> Unit
) {
    val context = LocalContext.current
    val parrafos = remember(ejecutoria.registro) {
        textoPlano(ejecutoria.texto)
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun abrirOficial() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ejecutoria.urlDetalle))) }
    }

    fun compartir() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, ejecutoria.asunto.ifBlank { "Ejecutoria ${ejecutoria.registro}" })
            putExtra(
                Intent.EXTRA_TEXT,
                listOf(ejecutoria.asunto, ejecutoria.localizacion, "Registro digital: ${ejecutoria.registro}", ejecutoria.urlDetalle)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            )
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Compartir ejecutoria")) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Barra superior: cerrar · título · compartir · sitio oficial.
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Cerrar") }
                    Column(Modifier.weight(1f)) {
                        Text("Ejecutoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Registro digital ${ejecutoria.registro}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (exportando) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, "Descargar PDF") }
                    }
                    IconButton(onClick = ::compartir) { Icon(Icons.Outlined.Share, "Compartir") }
                    IconButton(onClick = ::abrirOficial) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Ver en el sitio oficial")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        item(key = "encabezado") {
                            SelectionContainer {
                                Column {
                                    if (ejecutoria.asunto.isNotBlank()) {
                                        Text(ejecutoria.asunto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    Text(
                                        ejecutoria.localizacion,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    CitationBuilder.fechaDePublicacion(ejecutoria.publicacion)?.let {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Publicada el $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (ejecutoria.rubro.isNotBlank()) {
                                        SectionLabel("Tesis relacionada")
                                        Text(
                                            ejecutoria.rubro,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 6,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    SectionLabel("Sentencia")
                                }
                            }
                        }
                        items(parrafos) { parrafo ->
                            SelectionContainer {
                                // Encabezados de la sentencia («RESULTANDO», «CONSIDERANDO»): centrados
                                // y en negrita, igual que en el PDF.
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
                            OutlinedButton(onClick = ::abrirOficial, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ver en el sitio oficial")
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
                }
            }
        }
    }
}
