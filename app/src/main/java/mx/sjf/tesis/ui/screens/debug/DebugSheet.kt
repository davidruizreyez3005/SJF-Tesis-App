package mx.sjf.tesis.ui.screens.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sjf.tesis.core.NetLog
import mx.sjf.tesis.ui.components.OutlineButton
import mx.sjf.tesis.viewmodel.AppViewModel

@Composable
fun DebugPanel(vm: AppViewModel, compact: Boolean = false) {
    val context = LocalContext.current
    val entries by NetLog.entries.collectAsState()
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.BugReport, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Registro de red", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                "${entries.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF23364D), RoundedCornerShape(10.dp))
                .background(Color(0xFF0A1017))
        ) {
            Column(
                Modifier.heightIn(max = if (compact) 200.dp else 340.dp)
                    .verticalScroll(scroll)
                    .padding(10.dp)
            ) {
                if (entries.isEmpty()) {
                    Text(
                        "Sin actividad de red todavía.\nEjecuta una búsqueda o el diagnóstico.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFF8FA3BC)
                    )
                } else {
                    SelectionContainer {
                        Column {
                            entries.forEach { e -> LogLine(e.time, e.text) }
                        }
                    }
                }
            }
        }
        LaunchedEffect(entries.size) { scroll.scrollTo(scroll.maxValue) }
        Spacer(Modifier.height(8.dp))
        val descubriendo by vm.discoveryRunning.collectAsState()
        OutlineButton("Verificar endpoint de detalle", icon = Icons.Outlined.Search, onClick = { vm.runApiDiscovery() }, modifier = Modifier.fillMaxWidth())
        if (descubriendo) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Ejecutando verificación…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Registro de red", NetLog.fullText()))
                    vm.notify("Registro de red copiado")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copiar", maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "SJF Tesis · Registro de red")
                        putExtra(Intent.EXTRA_TEXT, NetLog.fullText())
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, "Compartir registro"))
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Share, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Compartir", maxLines = 1)
            }
            OutlinedButton(onClick = { NetLog.clear() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Limpiar", maxLines = 1)
            }
        }
    }
}

@Composable
fun LogLine(time: String, text: String) {
    val color = when {
        text.contains("✗") -> Color(0xFFF87171)
        text.contains("✓") -> Color(0xFF4ADE80)
        text.startsWith("←") || text.startsWith("→") -> Color(0xFFE8D5A0)
        else -> Color(0xFF9FB3CC)
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFF5F7189))) { append("[$time] ") }
            append(text)
        },
        fontFamily = FontFamily.Monospace,
        fontSize = 10.5f.sp,
        lineHeight = 15.sp,
        color = color
    )
}
