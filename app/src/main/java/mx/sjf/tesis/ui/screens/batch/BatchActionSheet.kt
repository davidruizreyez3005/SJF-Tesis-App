package mx.sjf.tesis.ui.screens.batch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.data.model.BatchState
import mx.sjf.tesis.data.model.PdfExportMode
import mx.sjf.tesis.data.util.CitationBuilder
import mx.sjf.tesis.data.util.FileSharer
import mx.sjf.tesis.ui.components.ActionButton
import mx.sjf.tesis.ui.components.GoldButton
import mx.sjf.tesis.ui.components.OutlineButton
import mx.sjf.tesis.ui.components.ProgressBlock
import mx.sjf.tesis.ui.components.SectionLabel
import mx.sjf.tesis.ui.theme.WarnYellow
import mx.sjf.tesis.ui.theme.semanticColor
import mx.sjf.tesis.viewmodel.AppViewModel

/**
 * Hoja de acciones por lote: descarga individual, PDF combinado, ZIP, citas.
 * Muestra el progreso de la operación y ofrece compartir/abrir al finalizar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchActionSheet(vm: AppViewModel, onDismiss: () -> Unit, snackbarHost: @Composable () -> Unit) {
    val context = LocalContext.current
    val batchState by vm.batchState.collectAsState()
    val settings by vm.settings.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tesis = vm.getSelectedTesis()

    // Cerrar la hoja con un resultado en pantalla da la operación por terminada.
    fun close() {
        vm.finishBatch()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()
        ) {
            Text(
                "Acciones por lote",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (tesis.size == 1) "1 tesis seleccionada" else "${tesis.size} tesis seleccionadas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            when (val current = batchState) {
                is BatchState.Idle -> {
                    SectionLabel("Descargar")
                    ActionButton(
                        label = "Un PDF por tesis",
                        icon = Icons.Outlined.Download,
                        onClick = { vm.downloadBatch(tesis); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        primary = true
                    )

                    SectionLabel("Exportar en un solo archivo")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionButton(
                            label = "PDF con índice",
                            icon = Icons.AutoMirrored.Outlined.Article,
                            onClick = { vm.exportBatch(PdfExportMode.COMBINED, tesis) },
                            modifier = Modifier.weight(1f)
                        )
                        ActionButton(
                            label = "ZIP de PDF",
                            icon = Icons.Outlined.FolderZip,
                            onClick = { vm.exportBatch(PdfExportMode.ZIP, tesis) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SectionLabel("Citas · ${settings.defaultCitationStyle.label}")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionButton(
                            label = "Copiar citas",
                            icon = Icons.Outlined.ContentCopy,
                            onClick = {
                                val text = CitationBuilder.buildBatch(tesis, settings.defaultCitationStyle)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Citas", text))
                                vm.notify(if (tesis.size == 1) "Cita copiada" else "${tesis.size} citas copiadas")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ActionButton(
                            label = "Compartir citas",
                            icon = Icons.Outlined.Share,
                            onClick = {
                                val text = CitationBuilder.buildBatch(tesis, settings.defaultCitationStyle)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Citas · SJF Tesis (${tesis.size})")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(send, "Compartir citas"))
                                }.onFailure { vm.notify("No se pudo compartir") }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
                is BatchState.Progress -> {
                    ProgressBlock(
                        message = "${current.stage}…",
                        detail = if (current.total > 1) "${current.current} de ${current.total}" else null
                    )
                    LinearProgressIndicator(
                        progress = { if (current.total == 0) 0f else current.current.toFloat() / current.total },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                is BatchState.Done -> {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Archivo listo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        current.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Guardado en Documents/SJF_Tesis",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (current.failures > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (current.failures == 1) "1 tesis se incluyó sin texto completo (no disponible en la SCJN)."
                            else "${current.failures} tesis se incluyeron sin texto completo (no disponible en la SCJN).",
                            style = MaterialTheme.typography.labelMedium,
                            color = semanticColor(WarnYellow)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlineButton(
                            "Compartir",
                            icon = Icons.Outlined.Share,
                            onClick = {
                                val intent = FileSharer.buildShareIntent(current.outputUri, current.mimeType)
                                runCatching { context.startActivity(Intent.createChooser(intent, "Compartir")) }
                                    .onFailure { vm.notify("No se pudo compartir el archivo") }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        GoldButton(
                            "Abrir",
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            onClick = {
                                val intent = FileSharer.buildViewIntent(current.outputUri, current.mimeType)
                                runCatching { context.startActivity(intent) }
                                    .onFailure { vm.notify("No hay una app instalada para abrir este archivo") }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = ::close, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
                    Spacer(Modifier.height(12.dp))
                }
                is BatchState.Error -> {
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlineButton("Volver", onClick = { vm.finishBatch() }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
            }
            snackbarHost()
        }
    }
}
