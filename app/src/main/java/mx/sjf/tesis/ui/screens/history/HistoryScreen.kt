package mx.sjf.tesis.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.data.model.HistoryEntry
import mx.sjf.tesis.data.util.relativeTime
import mx.sjf.tesis.ui.components.EmptyState
import mx.sjf.tesis.ui.theme.ErrorRed
import mx.sjf.tesis.ui.theme.semanticColor
import mx.sjf.tesis.viewmodel.AppViewModel

@Composable
fun HistoryScreen(vm: AppViewModel, onReRun: (String) -> Unit) {
    val history by vm.history.collectAsState()
    if (history.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = "Sin búsquedas",
            subtitle = "Tus últimas 50 búsquedas aparecerán aquí. Toca cualquiera para repetirla."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Desliza a la izquierda para eliminar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = vm::clearHistory) { Text("Borrar todo") }
                }
            }
            items(history, key = { it.id }) { entry ->
                HistoryRow(
                    entry = entry,
                    onClick = { onReRun(entry.query) },
                    onDelete = { vm.removeHistory(entry.id) }
                )
            }
        }
    }
}

@Composable
fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    var offsetX by remember(entry.id) { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pointerInput(entry.id) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount).coerceIn(-threshold * 2.5f, 0f)
                },
                onDragEnd = { if (offsetX < -threshold) onDelete() else offsetX = 0f }
            )
        }
    ) {
        Row(
            Modifier.matchParentSize().background(semanticColor(ErrorRed).copy(alpha = 0.14f)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Delete, "Eliminar", tint = semanticColor(ErrorRed), modifier = Modifier.padding(end = 18.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .graphicsLayer { translationX = offsetX }
                .clickable(onClick = onClick)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.query, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(relativeTime(entry.timestamp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onDelete) {
                Icon(Icons.Outlined.Delete, "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}
