package mx.sjf.tesis.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.data.model.HistoryEntry
import mx.sjf.tesis.ui.components.BrandMark
import mx.sjf.tesis.ui.components.EmptyState
import mx.sjf.tesis.ui.components.GoldButton
import mx.sjf.tesis.ui.components.GoldDivider
import mx.sjf.tesis.ui.components.PillChip
import mx.sjf.tesis.ui.components.PullToRefreshLayout
import mx.sjf.tesis.ui.components.ResultCard
import mx.sjf.tesis.ui.components.ResultSkeleton
import mx.sjf.tesis.ui.components.SectionLabel
import mx.sjf.tesis.ui.theme.WarnYellow
import mx.sjf.tesis.ui.theme.semanticColor
import mx.sjf.tesis.viewmodel.AppViewModel
import mx.sjf.tesis.viewmodel.MateriaFilter
import mx.sjf.tesis.viewmodel.SearchState
import mx.sjf.tesis.viewmodel.SortMode
import mx.sjf.tesis.viewmodel.TipoFilter
import java.text.NumberFormat
import java.util.Locale

private val suggestions = listOf(
    "extradición", "presunción de inocencia", "despido injustificado",
    "acceso a la información", "responsabilidad civil", "subordinación"
)

private val esMx = Locale("es", "MX")

@Composable
fun SearchScreen(vm: AppViewModel, listState: LazyListState) {
    val state by vm.search.collectAsState()
    val saved by vm.saved.collectAsState()
    val history by vm.history.collectAsState()
    val settings by vm.settings.collectAsState()
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val savedRegistro = remember(saved) { saved.map { it.registro }.toSet() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.background(
                // Degradado sutil tipo "papel premium" detrás del encabezado.
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)
                )
            )
        ) {
            HeaderRow(
                selectionMode = state.selectionMode,
                selectedCount = state.selected.size,
                allSelected = state.results.isNotEmpty() && state.selected.size == state.results.size,
                canSelect = state.results.isNotEmpty(),
                onToggleSelection = vm::toggleSelectionMode,
                onSelectAll = vm::selectAllResults
            )
            SearchBarRow(
                query = state.query,
                onQueryChange = vm::updateQuery,
                onSearch = { focus.clearFocus(); vm.search() }
            )
            FilterRow(
                tipo = state.tipo,
                materia = state.materia,
                onTipo = vm::setTipo,
                onMateria = vm::setMateria
            )
            // Ayuda contextual del modo selección: solo visible al entrar.
            AnimatedVisibility(
                visible = state.selectionMode && state.selected.isEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    "Toca las tesis que quieras incluir en la descarga",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            GoldDivider(Modifier.fillMaxWidth())
        }
        if (state.searched && !state.loading) {
            ResultsHeader(state, onSort = vm::setSort, onRetry = vm::refresh)
        }
        val phase = when {
            state.loading -> "loading"
            !state.searched -> "idle"
            state.results.isEmpty() -> "empty"
            else -> "results"
        }
        // Pull-to-refresh sobre la zona de resultados: desliza hacia abajo para
        // volver a consultar la SCJN (invalida cachés) sin perder lo en pantalla.
        PullToRefreshLayout(
            isRefreshing = state.refreshing,
            enabled = state.searched && !state.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Crossfade(targetState = phase, label = "fases") { p ->
                when (p) {
                    "loading" -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { ResultSkeleton() }
                    "idle" -> SearchIdle(
                        recent = history.take(5),
                        onSearch = { focus.clearFocus(); vm.search(it) }
                    )
                    "empty" -> if (!state.live && settings.apiDirect) EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "Sin conexión con la SCJN",
                        subtitle = "No fue posible consultar el Semanario Judicial y ninguna de tus tesis guardadas coincide con la búsqueda.",
                        actionLabel = "Reintentar",
                        onAction = vm::refresh
                    ) else EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "Sin resultados",
                        subtitle = if (state.query.isBlank()) {
                            "No hay tesis recientes con estos filtros.\nPrueba con otra materia o tipo de documento."
                        } else {
                            "No se encontraron tesis para «${state.query}».\nRevisa la ortografía, usa menos palabras o cambia los filtros."
                        }
                    )
                    else -> ResultsList(
                        state = state,
                        listState = listState,
                        savedRegistro = savedRegistro,
                        vm = vm,
                        haptics = haptics
                    )
                }
            }
        }
    }
}

/**
 * Lista de resultados con animación de colocación y long-press para seleccionar.
 *
 * Scroll infinito: al acercarse al final (a 4 tarjetas del borde) se solicita
 * automáticamente la siguiente página de 20 tesis — el usuario nunca toca
 * «Cargar más». El guard interno de loadMore() (loadingMore) evita disparos
 * duplicados, y si una carga falla el pie muestra «Reintentar» porque el
 * disparo automático no reintenta solo (sin bucles de errores).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultsList(
    state: SearchState,
    listState: LazyListState,
    savedRegistro: Set<Long>,
    vm: AppViewModel,
    haptics: HapticFeedback
) {
    // True cuando el último índice visible está a ≤4 tarjetas del final.
    // Solo depende de la geometría del scroll; los guards de estado
    // (canLoadMore) se evalúan dentro del efecto para evitar re-disparos.
    val nearBottom by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearBottom) {
        if (nearBottom && state.canLoadMore) vm.loadMore()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.results, key = { it.registro }) { tesis ->
            Box(Modifier.animateItemPlacement()) {
                ResultCard(
                    tesis = tesis,
                    query = state.query,
                    isSaved = tesis.registro in savedRegistro,
                    onClick = {
                        if (state.selectionMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.toggleSelection(tesis.registro)
                        } else vm.openDetail(tesis)
                    },
                    onBookmark = { vm.toggleSaved(tesis) },
                    onLongClick = {
                        // Long-press: entrada estándar al modo selección múltiple.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (state.selectionMode) vm.toggleSelection(tesis.registro)
                        else vm.beginSelection(tesis.registro)
                    },
                    selectionMode = state.selectionMode,
                    isSelected = tesis.registro in state.selected
                )
            }
        }
        if (state.canLoadMore) item(key = "load-more") {
            AutoLoadFooter(loading = state.loadingMore, onRetry = vm::loadMore)
        } else if (state.results.size >= 20) item(key = "end") {
            Text(
                "Fin de los resultados",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HeaderRow(
    selectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    canSelect: Boolean,
    onToggleSelection: () -> Unit,
    onSelectAll: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            // Encabezado contextual de selección: cerrar · contador · seleccionar todo.
            IconButton(onClick = onToggleSelection, modifier = Modifier.padding(end = 4.dp)) {
                Icon(Icons.Outlined.Close, "Salir de selección", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                when (selectedCount) {
                    0 -> "Seleccionar tesis"
                    1 -> "1 seleccionada"
                    else -> "$selectedCount seleccionadas"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Outlined.SelectAll,
                    if (allSelected) "Quitar selección" else "Seleccionar todo",
                    tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            BrandMark(size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "SJF Tesis",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Semanario Judicial de la Federación",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (canSelect) {
                IconButton(onClick = onToggleSelection) {
                    Icon(
                        Icons.Outlined.Checklist,
                        "Seleccionar varias tesis",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBarRow(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Buscar por rubro, texto o registro", style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, "Borrar texto", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** Filtros combinables: tipo de documento y materia, cada uno en su menú. */
@Composable
private fun FilterRow(
    tipo: TipoFilter,
    materia: MateriaFilter,
    onTipo: (TipoFilter) -> Unit,
    onMateria: (MateriaFilter) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DropdownChip(
            label = if (tipo == TipoFilter.TODOS) "Tipo" else tipo.label,
            active = tipo != TipoFilter.TODOS,
            options = TipoFilter.entries,
            current = tipo,
            optionLabel = { it.label },
            onSelect = onTipo
        )
        DropdownChip(
            label = if (materia == MateriaFilter.TODAS) "Materia" else materia.label,
            active = materia != MateriaFilter.TODAS,
            options = MateriaFilter.entries,
            current = materia,
            optionLabel = { it.label },
            onSelect = onMateria
        )
        if (tipo != TipoFilter.TODOS || materia != MateriaFilter.TODAS) {
            Text(
                "Limpiar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (tipo != TipoFilter.TODOS) onTipo(TipoFilter.TODOS)
                        if (materia != MateriaFilter.TODAS) onMateria(MateriaFilter.TODAS)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun <T> DropdownChip(
    label: String,
    active: Boolean,
    options: List<T>,
    current: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.dp,
                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(50)
                )
                .clickable { expanded = true }
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
            )
            Icon(Icons.Outlined.ArrowDropDown, null, tint = fg, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false },
                    trailingIcon = {
                        if (option == current) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
    }
}

@Composable
private fun ResultsHeader(state: SearchState, onSort: (SortMode) -> Unit, onRetry: () -> Unit) {
    val fmt = remember { NumberFormat.getIntegerInstance(esMx) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(buildAnnotatedString {
            val total = maxOf(state.totalFromApi, state.results.size)
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) {
                append(fmt.format(total))
            }
            append(if (total == 1) " resultado" else " resultados")
        }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.live) {
            Spacer(Modifier.width(10.dp))
            OfflineBadge(retrying = state.refreshing, onClick = onRetry)
        }
        Spacer(Modifier.weight(1f))
        SortMenu(current = state.sort, onSort = onSort)
    }
}

/** Aviso de que los resultados vienen de la base local; tocarlo reintenta. */
@Composable
private fun OfflineBadge(retrying: Boolean, onClick: () -> Unit) {
    val color = semanticColor(WarnYellow)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(WarnYellow.copy(alpha = 0.14f))
            .clickable(enabled = !retrying, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (retrying) "Reconectando…" else "Sin conexión · Reintentar", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SortMenu(current: SortMode, onSort: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Outlined.Sort, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(current.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = { onSort(mode); expanded = false },
                    trailingIcon = {
                        if (mode == current) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchIdle(recent: List<HistoryEntry>, onSearch: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(size = 64.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                "Consulta el Semanario Judicial",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tesis y jurisprudencias de la Suprema Corte de Justicia de la Nación y de los tribunales federales.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            GoldButton(
                "Ver tesis más recientes",
                onClick = { onSearch("") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (recent.isNotEmpty()) {
            SectionLabel("Búsquedas recientes")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                recent.forEachIndexed { i, entry ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSearch(entry.query) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            entry.query,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        SectionLabel("Sugerencias")
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { s -> PillChip(s, active = false) { onSearch(s) } }
        }
    }
}

/**
 * Pie de scroll infinito: rueda «Cargando más tesis…» mientras llega la
 * siguiente página y reintento manual solo si la carga automática falló.
 */
@Composable
private fun AutoLoadFooter(loading: Boolean, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Cargando más tesis…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            OutlinedButton(onClick = onRetry) { Text("Reintentar") }
        }
    }
}
