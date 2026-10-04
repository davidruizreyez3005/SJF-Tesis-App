package mx.sjf.tesis.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.data.model.BatchState
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.util.FileSharer
import mx.sjf.tesis.ui.components.BrandSplash
import mx.sjf.tesis.ui.components.GoldButton
import mx.sjf.tesis.ui.screens.batch.BatchActionSheet
import mx.sjf.tesis.ui.screens.citation.CitationDialog
import mx.sjf.tesis.ui.screens.debug.DebugPanel
import mx.sjf.tesis.ui.screens.detail.DetailSheet
import mx.sjf.tesis.ui.screens.downloads.DownloadsScreen
import mx.sjf.tesis.ui.screens.history.HistoryScreen
import mx.sjf.tesis.ui.screens.saved.SavedScreen
import mx.sjf.tesis.ui.screens.search.SearchScreen
import mx.sjf.tesis.ui.screens.settings.SettingsScreen
import mx.sjf.tesis.ui.theme.Gold
import mx.sjf.tesis.viewmodel.AppViewModel
import mx.sjf.tesis.viewmodel.UiAction
import kotlinx.coroutines.launch

private data class NavItem(val icon: ImageVector, val selectedIcon: ImageVector, val label: String)

private const val TAB_SEARCH = 0
private const val TAB_DOWNLOADS = 2

private val navItems = listOf(
    NavItem(Icons.Outlined.Search, Icons.Filled.Search, "Buscar"),
    NavItem(Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark, "Guardadas"),
    NavItem(Icons.Outlined.FileDownload, Icons.Filled.FileDownload, "Descargas"),
    NavItem(Icons.Outlined.History, Icons.Filled.History, "Historial"),
    NavItem(Icons.Outlined.Settings, Icons.Filled.Settings, "Ajustes")
)

@Composable
fun SjfApp(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_SEARCH) }
    var showBatchActions by remember { mutableStateOf(false) }
    var citationFor by remember { mutableStateOf<Tesis?>(null) }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    val selected by vm.selected.collectAsState()
    val searchState by vm.search.collectAsState()
    val batchState by vm.batchState.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // El estado de la lista vive aquí (y no dentro de la pestaña) para que
    // la posición de scroll se conserve al cambiar de pestaña.
    val resultsListState = rememberLazyListState()
    // Cada búsqueda nueva vuelve al inicio de la lista. El id atendido se
    // guarda para que girar la pantalla no repita el salto.
    var scrolledForSearch by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(searchState.searchId) {
        if (searchState.searchId != scrolledForSearch) {
            scrolledForSearch = searchState.searchId
            resultsListState.scrollToItem(0)
        }
    }

    fun selectTab(index: Int) {
        if (index != TAB_SEARCH && searchState.selectionMode) vm.exitSelection()
        tab = index
    }

    fun handleAction(action: UiAction) {
        when (action) {
            is UiAction.Undo -> action.run()
            is UiAction.OpenFile -> runCatching {
                context.startActivity(FileSharer.buildViewIntent(action.uri, action.mimeType))
            }.onFailure { vm.notify("No hay una app instalada para abrir este archivo") }
            UiAction.ShowDownloads -> {
                vm.closeDetail()
                showBatchActions = false
                selectTab(TAB_DOWNLOADS)
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            // Un mensaje nuevo reemplaza al anterior en lugar de hacer cola.
            snackbarHostState.currentSnackbarData?.dismiss()
            launch {
                val result = snackbarHostState.showSnackbar(
                    message = msg.text,
                    actionLabel = msg.actionLabel,
                    duration = if (msg.action != null) SnackbarDuration.Long else SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) msg.action?.let(::handleAction)
            }
        }
    }

    // Atrás: primero sale del modo selección, después vuelve a «Buscar».
    // Las hojas modales manejan su propio «atrás».
    BackHandler(enabled = searchState.selectionMode) { vm.exitSelection() }
    BackHandler(enabled = !searchState.selectionMode && tab != TAB_SEARCH) { selectTab(TAB_SEARCH) }

    // Los Snackbars se dibujan en la superficie más alta: una hoja modal
    // abierta taparía el host del Scaffold.
    val host = when {
        showBatchActions -> "batch"
        selected != null -> "detail"
        else -> "app"
    }
    val snackbarSlot: @Composable () -> Unit = { AppSnackbarHost(snackbarHostState) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { if (host == "app") snackbarSlot() },
        bottomBar = {
            Column {
                // Barra de acciones por lote cuando hay selección activa.
                AnimatedVisibility(
                    visible = tab == TAB_SEARCH && searchState.selectionMode && searchState.selected.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    BatchSelectionBar(
                        count = searchState.selected.size,
                        batchState = batchState,
                        onDownload = { vm.downloadBatch(vm.getSelectedTesis()) },
                        onMoreActions = { showBatchActions = true }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    navItems.forEachIndexed { index, item ->
                        val isSelected = tab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectTab(index) },
                            icon = {
                                Icon(if (isSelected) item.selectedIcon else item.icon, contentDescription = null)
                            },
                            label = { Text(item.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> SearchScreen(vm, listState = resultsListState)
                1 -> SavedScreen(vm, onBrowse = { selectTab(TAB_SEARCH) })
                2 -> DownloadsScreen(vm)
                3 -> HistoryScreen(vm, onReRun = { query -> selectTab(TAB_SEARCH); vm.search(query) })
                4 -> SettingsScreen(vm, debugContent = { DebugPanel(vm) })
            }
        }
    }

    // Hoja de detalle.
    selected?.let { tesis ->
        DetailSheet(
            vm = vm,
            tesis = tesis,
            onCitation = { citationFor = tesis },
            snackbarHost = { if (host == "detail") snackbarSlot() }
        )
    }

    // Diálogo de cita.
    citationFor?.let { pedida ->
        // Si el detalle terminó de cargar datos (clave, fuente…) después de
        // abrir el diálogo, la cita se arma con la versión más completa.
        val tesis = selected?.takeIf { it.registro == pedida.registro } ?: pedida
        CitationDialog(
            tesis = tesis,
            defaultStyle = settings.defaultCitationStyle,
            onDismiss = { citationFor = null }
        )
    }

    // Hoja de acciones por lote.
    if (showBatchActions) {
        BatchActionSheet(
            vm = vm,
            onDismiss = { showBatchActions = false },
            snackbarHost = { if (host == "batch") snackbarSlot() }
        )
    }

    // Intro de marca: solo al abrir la app, no al rotar la pantalla.
    if (showSplash) {
        BrandSplash(onFinished = { showSplash = false })
    }
}

/** Snackbar con la paleta de la app: superficie inversa y acción dorada. */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = Gold
        )
    }
}

/**
 * Barra de acciones por lote: descarga directa (primaria, dorada) y acceso a
 * más acciones (PDF combinado, ZIP, citas). Mientras corre la descarga
 * muestra el avance tesis por tesis en lugar de los botones.
 */
@Composable
private fun BatchSelectionBar(
    count: Int,
    batchState: BatchState,
    onDownload: () -> Unit,
    onMoreActions: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            when (val current = batchState) {
                is BatchState.Progress -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${current.stage}… ${current.current} de ${current.total}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                current.currentRubro,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (current.total == 0) 0f
                            else current.current.toFloat() / current.total.toFloat()
                        },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                else -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (count == 1) "Descargar 1 PDF" else "Descargar $count PDF",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Se guardan en Documents/SJF_Tesis",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        // Acción secundaria: más acciones (combinado, ZIP, citas).
                        FilledTonalIconButton(
                            onClick = onMoreActions,
                            shape = RoundedCornerShape(12.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "Más acciones")
                        }
                        Spacer(Modifier.width(8.dp))
                        GoldButton(
                            text = "Descargar",
                            icon = Icons.Outlined.Download,
                            onClick = onDownload
                        )
                    }
                }
            }
        }
    }
}
