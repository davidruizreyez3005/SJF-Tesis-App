package mx.sjf.tesis.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sjf.tesis.core.Constants
import mx.sjf.tesis.data.model.CitationStyle
import mx.sjf.tesis.data.model.ThemeMode
import mx.sjf.tesis.ui.components.DangerTextButton
import mx.sjf.tesis.ui.components.FontStepButton
import mx.sjf.tesis.ui.components.IconTile
import mx.sjf.tesis.ui.components.OutlineButton
import mx.sjf.tesis.ui.components.PillChip
import mx.sjf.tesis.ui.components.PulseDot
import mx.sjf.tesis.ui.components.SettingRow
import mx.sjf.tesis.ui.components.SettingsCard
import mx.sjf.tesis.ui.theme.ErrorRed
import mx.sjf.tesis.ui.theme.SuccessGreen
import mx.sjf.tesis.ui.theme.WarnYellow
import mx.sjf.tesis.ui.theme.semanticColor
import mx.sjf.tesis.viewmodel.AppViewModel
import kotlin.math.roundToInt

/** Confirmación pendiente en la sección «Datos». */
private enum class ClearTarget { HISTORY, SAVED }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, debugContent: @Composable () -> Unit) {
    val settings by vm.settings.collectAsState()
    val searchState by vm.search.collectAsState()
    val diagnostic by vm.diagnostic.collectAsState()
    val running by vm.diagnosticRunning.collectAsState()
    val saved by vm.saved.collectAsState()
    val history by vm.history.collectAsState()
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ClearTarget?>(null) }

    confirm?.let { target ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (target == ClearTarget.HISTORY) "¿Borrar el historial?" else "¿Borrar las tesis guardadas?") },
            text = {
                Text(
                    if (target == ClearTarget.HISTORY) "Se eliminarán tus ${history.size} búsquedas recientes."
                    else "Se eliminarán ${saved.size} tesis guardadas de este dispositivo. Los PDF descargados no se borran."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (target == ClearTarget.HISTORY) vm.clearHistory() else vm.clearSaved()
                    confirm = null
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsCard {
                CardTitle(Icons.Outlined.Palette, "Apariencia")
                Spacer(Modifier.height(14.dp))
                Text("Tema", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                activeContentColor = MaterialTheme.colorScheme.primary,
                                activeBorderColor = MaterialTheme.colorScheme.outline,
                                inactiveBorderColor = MaterialTheme.colorScheme.outline
                            )
                        ) { Text(mode.label) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tamaño del texto", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${(settings.fontScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FontStepButton("A−") { vm.adjustFont(-0.1f) }
                    Spacer(Modifier.width(8.dp))
                    FontStepButton("A+") { vm.adjustFont(0.1f) }
                }
            }
        }

        item {
            SettingsCard {
                CardTitle(Icons.Outlined.FormatQuote, "Citas jurídicas")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Formato predeterminado al copiar o compartir citas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CitationStyle.entries.forEach { style ->
                        PillChip(
                            label = style.label,
                            active = settings.defaultCitationStyle == style,
                            onClick = { vm.setCitationStyle(style) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    settings.defaultCitationStyle.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard {
                CardTitle(Icons.Outlined.Delete, "Datos")
                Spacer(Modifier.height(14.dp))
                DangerTextButton("Borrar historial (${history.size})") {
                    if (history.isNotEmpty()) confirm = ClearTarget.HISTORY
                }
                Spacer(Modifier.height(8.dp))
                DangerTextButton("Borrar tesis guardadas (${saved.size})") {
                    if (saved.isNotEmpty()) confirm = ClearTarget.SAVED
                }
            }
        }

        item {
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().clickable { advancedOpen = !advancedOpen },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(Icons.Outlined.Tune)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Avanzado", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Conexión, diagnóstico y registro de red",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val rotation by animateFloatAsState(if (advancedOpen) 180f else 0f, label = "chevron")
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = if (advancedOpen) "Contraer" else "Expandir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(rotation)
                    )
                }
                AnimatedVisibility(advancedOpen) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 14.dp))
                        SettingRow(
                            title = "Consulta en línea",
                            subtitle = "Busca directamente en el Semanario Judicial. Desactivada, la app solo busca en tus tesis guardadas.",
                            trailing = { Switch(checked = settings.apiDirect, onCheckedChange = vm::setApiDirect) }
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(if (searchState.live) SuccessGreen else WarnYellow, size = 8.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (searchState.live) "Conectado a la SCJN" else "Sin conexión con la SCJN",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        searchState.lastError?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("Último error: $it", style = MaterialTheme.typography.bodySmall, color = semanticColor(ErrorRed))
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlineButton(
                            "Probar conexión",
                            icon = Icons.Outlined.NetworkCheck,
                            onClick = vm::runDiagnostic,
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (running) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Ejecutando diagnóstico…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (diagnostic.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                diagnostic.forEach { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 14.dp))
                        debugContent()
                    }
                }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "SJF Tesis ${Constants.APP_VERSION}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Fuente de datos: sjf2.scjn.gob.mx\nAplicación no oficial, sin afiliación con la SCJN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CardTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
