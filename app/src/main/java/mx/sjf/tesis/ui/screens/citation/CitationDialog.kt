package mx.sjf.tesis.ui.screens.citation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.data.model.CitationStyle
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.ui.theme.WarnYellow
import mx.sjf.tesis.ui.theme.semanticColor
import mx.sjf.tesis.data.util.CitationBuilder
import mx.sjf.tesis.ui.components.PillChip
import mx.sjf.tesis.ui.components.SectionLabel

/**
 * Diálogo modal para construir y copiar citas en 4 formatos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitationDialog(tesis: Tesis, defaultStyle: CitationStyle, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var style by remember { mutableStateOf(defaultStyle) }
    val citation = remember(tesis, style) { CitationBuilder.build(tesis, style) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()
        ) {
            Text("Cita jurídica", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Elige un formato y copia o comparte la cita generada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            // Selector de estilo — píldoras horizontales.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CitationStyle.entries.forEach { s ->
                    PillChip(label = s.label, active = s == style, onClick = { style = s })
                }
            }
            Spacer(Modifier.height(8.dp))

            Text(
                style.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!tesis.tieneDatosOficiales) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Faltan datos oficiales de localización (clave o fuente) para esta tesis. " +
                        "Verifica la cita en el sitio oficial antes de usarla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColor(WarnYellow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(WarnYellow.copy(alpha = 0.12f))
                        .padding(10.dp)
                )
            }

            SectionLabel("Cita generada")
            SelectionContainer {
                Text(
                    citation,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            Spacer(Modifier.height(14.dp))

            // Botones de acción.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CitationActionButton(
                    label = "Copiar",
                    icon = Icons.Outlined.ContentCopy,
                    modifier = Modifier.weight(1f)
                ) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Cita ${style.label}", citation))
                    // Android 13+ ya muestra su propia confirmación al copiar.
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                        android.widget.Toast.makeText(context, "Cita copiada", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                CitationActionButton(
                    label = "Compartir",
                    icon = Icons.Outlined.Share,
                    modifier = Modifier.weight(1f)
                ) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Cita · ${tesis.rubro.take(80)}")
                        putExtra(Intent.EXTRA_TEXT, citation)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, "Compartir cita"))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CitationActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
    }
}
