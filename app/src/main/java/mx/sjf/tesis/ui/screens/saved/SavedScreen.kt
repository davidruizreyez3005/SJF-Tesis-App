package mx.sjf.tesis.ui.screens.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.data.util.foldAccents
import mx.sjf.tesis.ui.components.EmptyState
import mx.sjf.tesis.ui.components.ResultCard
import mx.sjf.tesis.viewmodel.AppViewModel

@Composable
fun SavedScreen(vm: AppViewModel, onBrowse: () -> Unit) {
    val saved by vm.saved.collectAsState()
    var filter by rememberSaveable { mutableStateOf("") }

    if (saved.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.BookmarkBorder,
            title = "Sin tesis guardadas",
            subtitle = "Toca el marcador en cualquier resultado para conservar la tesis aquí, disponible sin conexión.",
            actionLabel = "Buscar tesis",
            onAction = onBrowse
        )
        return
    }

    // Filtro local por rubro, texto o número de registro (sin acentos).
    val visible = remember(saved, filter) {
        val q = filter.trim().lowercase().foldAccents()
        if (q.isEmpty()) saved
        else saved.filter {
            it.registro.toString().contains(q) ||
                (it.rubro + " " + it.texto + " " + it.materia).lowercase().foldAccents().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    Text(
                        if (saved.size == 1) "Filtrar 1 tesis guardada" else "Filtrar ${saved.size} tesis guardadas",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.FilterList, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        IconButton(onClick = { filter = "" }) {
                            Icon(Icons.Outlined.Close, "Borrar filtro", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
        if (visible.isEmpty()) {
            item(key = "none") {
                Text(
                    "Ninguna tesis guardada coincide con «${filter.trim()}».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        items(visible, key = { it.registro }) { tesis ->
            ResultCard(
                tesis = tesis,
                query = filter.trim(),
                isSaved = true,
                onClick = { vm.openDetail(tesis) },
                onBookmark = { vm.toggleSaved(tesis) }
            )
        }
    }
}
