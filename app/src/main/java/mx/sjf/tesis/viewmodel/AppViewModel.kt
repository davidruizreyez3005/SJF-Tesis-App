package mx.sjf.tesis.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mx.sjf.tesis.core.Constants
import mx.sjf.tesis.core.NetLog
import mx.sjf.tesis.data.local.Store
import mx.sjf.tesis.data.model.BatchState
import mx.sjf.tesis.data.model.CitationStyle
import mx.sjf.tesis.data.model.PdfExportMode
import mx.sjf.tesis.data.model.SearchResponse
import mx.sjf.tesis.data.model.Settings
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.model.ThemeMode
import mx.sjf.tesis.data.remote.SjfApi
import mx.sjf.tesis.data.util.PdfExporter
import mx.sjf.tesis.data.util.PdfStorage
import mx.sjf.tesis.data.util.countOccurrences
import mx.sjf.tesis.data.util.foldAccents
import mx.sjf.tesis.data.util.parseFecha
import mx.sjf.tesis.data.util.tipoGrupo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class SortMode(val label: String) {
    RELEVANCIA("Relevancia"),
    RECIENTES("Recientes"),
    ALFABETICO("Alfabético")
}

/** Filtro por tipo de documento (fila 1 de filtros). */
enum class TipoFilter(val label: String, val tipoDoc: String?) {
    TODOS("Todos los tipos", null),
    JURISPRUDENCIA("Jurisprudencia", "Jurisprudencia"),
    AISLADA("Tesis aislada", "Tesis")
}

/** Filtro por materia (fila 2 de filtros). Se combina con [TipoFilter]. */
enum class MateriaFilter(val label: String, val materia: String?) {
    TODAS("Todas las materias", null),
    CONSTITUCIONAL("Constitucional", "Constitucional"),
    PENAL("Penal", "Penal"),
    CIVIL("Civil", "Civil"),
    LABORAL("Laboral", "Laboral")
}

/** Acción opcional que acompaña a un mensaje (botón del Snackbar). */
sealed interface UiAction {
    /** Revierte la operación que generó el mensaje. */
    class Undo(val run: () -> Unit) : UiAction
    /** Abre un archivo generado en el visor del sistema. */
    data class OpenFile(val uri: Uri, val mimeType: String) : UiAction
    /** Lleva al usuario a la pestaña Descargas. */
    data object ShowDownloads : UiAction
}

/** Mensaje efímero para el usuario (se muestra como Snackbar). */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: UiAction? = null
)

data class SearchState(
    val query: String = "",
    val results: List<Tesis> = emptyList(),
    val totalFromApi: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val live: Boolean = false,
    val lastError: String? = null,
    val sort: SortMode = SortMode.RELEVANCIA,
    val tipo: TipoFilter = TipoFilter.TODOS,
    val materia: MateriaFilter = MateriaFilter.TODAS,
    val canLoadMore: Boolean = false,
    val searched: Boolean = false,
    val selectionMode: Boolean = false,
    val selected: Set<Long> = emptySet(),
    /** Se incrementa con cada búsqueda nueva: la lista vuelve al inicio. */
    val searchId: Int = 0
)

/**
 * ViewModel único de la app: search, saved, history, batch selection, PDF y citas.
 * Toda la navegación entre estados pasa por aquí.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    val settings = store.settingsFlow
    val saved = store.savedFlow
    val history = store.historyFlow

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()

    private val _search = MutableStateFlow(SearchState())
    val search = _search.asStateFlow()

    private val _selected = MutableStateFlow<Tesis?>(null)
    val selected = _selected.asStateFlow()

    // True mientras byId está corriendo para la tesis seleccionada. Permite a
    // DetailSheet distinguir entre "cargando" y "cargado pero sin texto".
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading = _detailLoading.asStateFlow()

    // True si la última carga del detalle falló por red (no por falta de texto).
    private val _detailError = MutableStateFlow(false)
    val detailError = _detailError.asStateFlow()

    private val _diagnostic = MutableStateFlow<List<String>>(emptyList())
    val diagnostic = _diagnostic.asStateFlow()

    private val _diagnosticRunning = MutableStateFlow(false)
    val diagnosticRunning = _diagnosticRunning.asStateFlow()

    private val _discoveryRunning = MutableStateFlow(false)
    val discoveryRunning = _discoveryRunning.asStateFlow()

    private val _batchState = MutableStateFlow<BatchState>(BatchState.Idle)
    val batchState = _batchState.asStateFlow()

    private var fetched: List<Tesis> = emptyList()
    private var page = 0
    private var totalPages = 0

    init {
        NetLog.log("SJF Tesis v${Constants.APP_VERSION} iniciada · API directa: ${if (store.settings.value.apiDirect) "activada" else "desactivada"}")
        // La sesión se abre en segundo plano para que ya esté lista cuando el
        // usuario abra su primera tesis (ver SjfApi.warmUp).
        if (store.settings.value.apiDirect) viewModelScope.launch { SjfApi.warmUp() }
    }

    fun notify(text: String, actionLabel: String? = null, action: UiAction? = null) {
        _messages.tryEmit(UiMessage(text, actionLabel, action))
    }
    fun updateQuery(q: String) { _search.value = _search.value.copy(query = q) }

    // Al cambiar un filtro se re-lanza la búsqueda con la query actual (que
    // puede ser vacía — en ese caso se hace browse de las últimas tesis).
    fun setTipo(tipo: TipoFilter) {
        if (tipo == _search.value.tipo) return
        _search.value = _search.value.copy(tipo = tipo)
        lanzarBusqueda(_search.value.query.trim())
    }

    fun setMateria(materia: MateriaFilter) {
        if (materia == _search.value.materia) return
        _search.value = _search.value.copy(materia = materia)
        lanzarBusqueda(_search.value.query.trim())
    }

    fun setSort(sort: SortMode) {
        _search.value = _search.value.copy(sort = sort)
        applyLocalView()
    }

    /**
     * Lanza una búsqueda. Una query vacía es perfectamente válida:
     * el modo «Todo» sin texto hace un browse de las últimas tesis
     * (igual que el sitio sjf2.scjn.gob.mx al pulsar Buscar en blanco).
     */
    fun search(query: String? = null) {
        val q = (query ?: _search.value.query).trim()
        lanzarBusqueda(q)
        // Solo agregamos al historial si el usuario escribió algo —
        // una búsqueda vacía no deja rastro en el historial.
        if (q.isNotEmpty()) store.addHistory(q)
    }

    /**
     * Refresca los resultados actuales (pull-to-refresh). Invalida las cachés
     * de la API y, si la red falla, conserva los resultados en pantalla en
     * lugar de sustituirlos por la base local de semillas.
     */
    fun refresh() {
        val s = _search.value
        if (!s.searched || s.loading || s.refreshing) return
        viewModelScope.launch {
            _search.value = s.copy(refreshing = true)
            SjfApi.bustCaches()
            try {
                runSearch(s.query.trim(), append = false, fallbackToLocal = false)
            } finally {
                _search.value = _search.value.copy(refreshing = false)
            }
        }
    }

    private fun lanzarBusqueda(q: String) {
        viewModelScope.launch {
            val prev = _search.value
            _search.value = prev.copy(query = q, loading = true, searched = true, searchId = prev.searchId + 1)
            runSearch(q, false)
            _search.value = _search.value.copy(loading = false)
        }
    }

    fun loadMore() {
        val s = _search.value
        if (s.loading || s.loadingMore || !s.live || page + 1 >= totalPages) return
        viewModelScope.launch {
            _search.value = s.copy(loadingMore = true)
            runSearch(s.query.trim(), true)
            _search.value = _search.value.copy(loadingMore = false)
        }
    }

    private suspend fun runSearch(q: String, append: Boolean, fallbackToLocal: Boolean = true) {
        val tipoDoc = _search.value.tipo.tipoDoc
        val materia = _search.value.materia.materia
        if (settings.value.apiDirect) {
            try {
                val targetPage = if (append) page + 1 else 0
                val response: SearchResponse = when {
                    q.isNotEmpty() -> SjfApi.search(q, tipoDoc, materia, targetPage)
                    tipoDoc != null || materia != null ->
                        SjfApi.browseFiltrado(tipoDoc, materia, targetPage)
                    else -> SjfApi.browse(targetPage)
                }
                fetched = if (append) (fetched + response.documents).distinctBy { it.registro }
                           else response.documents
                page = targetPage
                totalPages = if (response.totalPages > 1) response.totalPages
                             else maxOf(1, (response.totalElements + 19) / 20)
                _search.value = _search.value.copy(
                    totalFromApi = response.totalElements, live = true, lastError = null
                )
            } catch (e: Exception) {
                if (fallbackToLocal && !append) {
                    _search.value = _search.value.copy(live = false, lastError = e.message ?: e.toString())
                    fetched = localSearch(q, tipoDoc, materia)
                    page = 0; totalPages = 0
                    _search.value = _search.value.copy(totalFromApi = fetched.size)
                    NetLog.log("Fallback local · ${fetched.size} resultados para «$q»")
                    notify("Sin conexión con la SCJN. Se buscó en tus tesis guardadas.")
                } else {
                    // Al refrescar no descartamos lo que ya está en pantalla.
                    _search.value = _search.value.copy(lastError = e.message ?: e.toString())
                    if (!append) notify("No fue posible actualizar. Se conservan los resultados anteriores.")
                    else notify("No fue posible cargar más resultados")
                }
            }
        } else {
            fetched = localSearch(q, tipoDoc, materia)
            page = 0; totalPages = 0
            _search.value = _search.value.copy(live = false, lastError = null, totalFromApi = fetched.size)
            NetLog.log("Modo solo local · ${fetched.size} resultados para «$q»")
        }
        applyLocalView()
    }

    private fun applyLocalView() {
        val s = _search.value
        val visible = when (s.sort) {
            SortMode.RELEVANCIA -> fetched
            SortMode.RECIENTES -> fetched.sortedByDescending { parseFecha(it.fecha) }
            SortMode.ALFABETICO -> fetched.sortedBy { it.rubro.lowercase() }
        }
        _search.value = s.copy(
            results = visible,
            canLoadMore = s.live && page + 1 < totalPages && visible.isNotEmpty()
        )
    }

    /**
     * Búsqueda sin conexión: solo en las tesis que el usuario guardó (datos
     * reales descargados de la SCJN). Nunca se muestran tesis de ejemplo.
     */
    private fun localSearch(query: String, tipo: String?, materia: String?): List<Tesis> {
        val terms = query.lowercase().split(Regex("\\s+")).map { it.foldAccents() }.filter { it.length >= 3 }
        val base = saved.value
        if (terms.isEmpty()) return base
        fun hay(t: Tesis) = (t.rubro + " " + t.texto + " " + t.precedente + " " + t.materia).lowercase().foldAccents()
        fun score(t: Tesis): Int {
            val rubro = t.rubro.lowercase().foldAccents()
            val body = t.texto.lowercase().foldAccents()
            return terms.sumOf { term -> rubro.countOccurrences(term) * 3 + body.countOccurrences(term) }
        }
        var list = base.filter { t -> terms.all { hay(t).contains(it) } }
        if (list.isEmpty()) list = base.filter { t -> terms.any { hay(t).contains(it) } }
        if (tipo != null) { val g = tipoGrupo(tipo); list = list.filter { tipoGrupo(it.tipo) == g } }
        if (materia != null) list = list.filter { it.materia.contains(materia, ignoreCase = true) }
        return list.sortedByDescending(::score)
    }

    // ── Detalle ──
    fun openDetail(tesis: Tesis) {
        _selected.value = tesis
        _detailError.value = false
        // También se consulta cuando ya hay texto pero faltan los datos de
        // localización oficiales (clave, fuente…): p. ej. tesis guardadas con
        // versiones anteriores. Se completan en segundo plano.
        val incompleta = tesis.texto.isBlank() || !tesis.tieneDatosOficiales
        if (tesis.registro != 0L && incompleta && settings.value.apiDirect) loadDetail(tesis)
    }

    /** Reintenta obtener el texto completo de la tesis abierta. */
    fun retryDetail() {
        val tesis = _selected.value ?: return
        if (!_detailLoading.value) loadDetail(tesis)
    }

    private fun loadDetail(tesis: Tesis) {
        _detailLoading.value = true
        _detailError.value = false
        viewModelScope.launch {
            runCatching { SjfApi.byId(tesis) }.onSuccess { recibida ->
                // Nunca se pierde lo que ya teníamos (p. ej. el texto guardado).
                val completa = recibida.conMetadatosDe(tesis).let {
                    if (it.texto.isBlank()) it.copy(texto = tesis.texto) else it
                }
                if (_selected.value?.registro == tesis.registro) _selected.value = completa
                if (completa.texto.isNotBlank()) store.updateSaved(completa)
            }.onFailure {
                NetLog.log("Detalle ✗ ${it.message}")
                if (_selected.value?.registro == tesis.registro) _detailError.value = true
            }
            _detailLoading.value = false
        }
    }

    fun closeDetail() {
        _selected.value = null
        _detailLoading.value = false
        _detailError.value = false
    }

    // ── Guardadas ──
    fun toggleSaved(tesis: Tesis) {
        val index = saved.value.indexOfFirst { it.registro == tesis.registro }
        val nowSaved = store.toggleSaved(tesis)
        if (nowSaved) notify("Tesis guardada")
        else notify("Tesis quitada de Guardadas", "Deshacer", UiAction.Undo { store.restoreSaved(tesis, index) })
    }

    fun clearSaved() {
        val backup = saved.value
        store.clearSaved()
        notify("Se borraron ${backup.size} tesis guardadas", "Deshacer", UiAction.Undo { store.restoreAllSaved(backup) })
    }

    // ── Historial ──
    fun removeHistory(id: Long) {
        val index = history.value.indexOfFirst { it.id == id }
        val entry = history.value.getOrNull(index) ?: return
        store.removeHistory(id)
        notify("Búsqueda eliminada", "Deshacer", UiAction.Undo { store.restoreHistory(entry, index) })
    }

    fun clearHistory() {
        val backup = history.value
        store.clearHistory()
        notify("Historial borrado", "Deshacer", UiAction.Undo { store.restoreAllHistory(backup) })
    }

    // ── Ajustes ──
    fun setThemeMode(mode: ThemeMode) { store.updateSettings { it.copy(themeMode = mode) } }
    fun setApiDirect(enabled: Boolean) {
        store.updateSettings { it.copy(apiDirect = enabled) }
        NetLog.log("API Directa ${if (enabled) "activada" else "desactivada"} por el usuario")
        if (!enabled) { _search.value = _search.value.copy(live = false, lastError = null); applyLocalView() }
        else if (_search.value.searched) { lanzarBusqueda(_search.value.query.trim()) }
    }
    fun adjustFont(delta: Float) {
        store.updateSettings { it.copy(fontScale = (it.fontScale + delta).coerceIn(0.8f, 1.5f)) }
    }
    fun setCitationStyle(style: CitationStyle) {
        store.updateSettings { it.copy(defaultCitationStyle = style) }
    }

    // ── Selección múltiple ──
    fun toggleSelectionMode() {
        if (_search.value.selectionMode) exitSelection() else enterSelection(emptySet())
    }
    /** Entra a modo selección marcando la tesis indicada (long-press en la tarjeta). */
    fun beginSelection(registro: Long) {
        if (!_search.value.selectionMode) enterSelection(setOf(registro))
    }
    private fun enterSelection(initial: Set<Long>) {
        clearFinishedBatch()
        _search.value = _search.value.copy(selectionMode = true, selected = initial)
    }
    fun exitSelection() {
        _search.value = _search.value.copy(selectionMode = false, selected = emptySet())
    }
    fun toggleSelection(registro: Long) {
        val s = _search.value
        val next = if (registro in s.selected) s.selected - registro
                   else s.selected + registro
        _search.value = s.copy(selected = next)
    }
    fun selectAllResults() {
        val s = _search.value
        val all = s.results.map { it.registro }.toSet()
        // Segundo toque en «seleccionar todo» deselecciona todo.
        _search.value = s.copy(selected = if (s.selected == all) emptySet() else all)
    }
    fun getSelectedTesis(): List<Tesis> {
        val selectedIds = _search.value.selected
        return fetched.filter { it.registro in selectedIds }
    }

    // ── Exportación ──

    private val exporting get() = _batchState.value is BatchState.Progress

    /**
     * Descarga el texto completo de varias tesis en paralelo (máx. 3 simultáneas
     * para no parecer tráfico automatizado ante el WAF de Incapsula). Conserva
     * el orden de entrada y reporta avance por cada tesis terminada.
     */
    private suspend fun fetchTextsParallel(
        tesis: List<Tesis>,
        onProgress: (Int, Int, String) -> Unit
    ): List<Tesis> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(3)
        val done = AtomicInteger(0)
        tesis.map { t ->
            async {
                semaphore.withPermit {
                    val completa = if (t.texto.isNotBlank()) t
                                   else runCatching { SjfApi.byId(t) }.getOrDefault(t)
                    onProgress(done.incrementAndGet(), tesis.size, t.rubro)
                    completa
                }
            }
        }.awaitAll()
    }

    /**
     * Esqueleto común de toda exportación: valida, publica el avance en
     * [batchState], baja los textos completos y delega la escritura a [write].
     * Cualquier error se reporta con un mensaje y deja el estado en Idle, de
     * modo que la interfaz nunca queda «atorada» en un estado terminal.
     */
    private fun launchExport(
        tesis: List<Tesis>,
        stage: String,
        write: suspend (Application, List<Tesis>) -> Unit
    ) {
        if (tesis.isEmpty()) { notify("No hay tesis seleccionadas"); return }
        if (exporting) { notify("Espera a que termine la exportación en curso"); return }
        _batchState.value = BatchState.Progress(0, tesis.size, tesis.first().rubro, stage)
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            try {
                if (!PdfStorage.hasWritePermission(ctx)) {
                    _batchState.value = BatchState.Idle
                    notify("Concede el permiso de almacenamiento para guardar archivos")
                    return@launch
                }
                val withTexts = fetchTextsParallel(tesis) { done, total, rubro ->
                    _batchState.value = BatchState.Progress(done, total, rubro, stage)
                }
                write(ctx, withTexts)
                autoSave(withTexts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NetLog.log("Exportación ✗ ${e.message}")
                _batchState.value = BatchState.Idle
                notify("No se pudo generar el archivo. Inténtalo de nuevo.")
            }
        }
    }

    /** Las tesis exportadas se conservan en Guardadas para consultarlas sin red. */
    private fun autoSave(tesis: List<Tesis>) {
        val existing = saved.value.map { it.registro }.toSet()
        tesis.filter { it.registro !in existing }.forEach { store.toggleSaved(it) }
    }

    private fun sinTextoSuffix(tesis: List<Tesis>): String {
        val n = tesis.count { it.texto.isBlank() }
        return if (n == 0) "" else " · $n sin texto completo"
    }

    /**
     * Descarga por lote (acción principal de la barra de selección): un PDF
     * individual por tesis en Documents/SJF_Tesis/. Al terminar sale del modo
     * selección y ofrece ir a Descargas.
     */
    fun downloadBatch(tesis: List<Tesis>) = launchExport(tesis, "Descargando") { ctx, withTexts ->
        val uris = mutableListOf<Uri>()
        withTexts.forEachIndexed { index, t ->
            _batchState.value = BatchState.Progress(index + 1, withTexts.size, t.rubro, "Generando PDF")
            withContext(Dispatchers.IO) { runCatching { PdfExporter.exportSingle(ctx, t) } }
                .onSuccess { uris += it.uri }
        }
        _batchState.value = BatchState.Idle
        if (uris.isEmpty()) {
            notify("No se pudo generar ningún PDF")
            return@launchExport
        }
        exitSelection()
        val failed = withTexts.size - uris.size
        val text = (if (uris.size == 1) "1 PDF guardado" else "${uris.size} PDF guardados") +
            (if (failed > 0) " · $failed con error" else sinTextoSuffix(withTexts))
        notify(text, "Ver", UiAction.ShowDownloads)
    }

    /** Exporta las tesis seleccionadas como PDF combinado o ZIP (hoja de acciones). */
    fun exportBatch(mode: PdfExportMode, tesis: List<Tesis>) = launchExport(tesis, "Preparando") { ctx, withTexts ->
        _batchState.value = BatchState.Progress(withTexts.size, withTexts.size, "", "Generando archivo")
        val result = withContext(Dispatchers.IO) {
            when (mode) {
                PdfExportMode.COMBINED -> PdfExporter.exportCombined(ctx, withTexts)
                PdfExportMode.ZIP -> PdfExporter.exportZip(ctx, withTexts)
            }
        }
        _batchState.value = BatchState.Done(
            successes = withTexts.size,
            failures = withTexts.count { it.texto.isBlank() },
            outputUri = result.uri,
            fileName = result.fileName,
            mimeType = result.mimeType
        )
    }

    /** Exporta una sola tesis (botón PDF de la hoja de detalle). */
    fun exportSingle(tesis: Tesis) = launchExport(listOf(tesis), "Generando PDF") { ctx, withTexts ->
        val result = withContext(Dispatchers.IO) { PdfExporter.exportSingle(ctx, withTexts.first()) }
        _batchState.value = BatchState.Idle
        notify("PDF guardado en Descargas", "Abrir", UiAction.OpenFile(result.uri, result.mimeType))
        if (_selected.value?.registro == tesis.registro && withTexts.first().texto.isNotBlank()) {
            _selected.value = withTexts.first()
        }
    }

    /** Cierra el resultado de una exportación por lote y sale del modo selección. */
    fun finishBatch() {
        val wasDone = _batchState.value is BatchState.Done
        clearFinishedBatch()
        if (wasDone) exitSelection()
    }

    private fun clearFinishedBatch() {
        if (!exporting) _batchState.value = BatchState.Idle
    }

    // ── Diagnóstico y descubrimiento de API ──
    fun runApiDiscovery() {
        if (_discoveryRunning.value) return
        viewModelScope.launch {
            _discoveryRunning.value = true
            // Permitir que el usuario fuerce una nueva corrida desde Ajustes,
            // incluso si el discovery ya corrió automáticamente en esta sesión.
            SjfApi.resetDiscoveryFlag()
            notify("Verificando endpoint de detalle…")
            // Verificación rápida primero (~1s): si el endpoint por defecto ya
            // funciona, no hay necesidad de correr el discovery completo (~30s).
            val ok = runCatching { SjfApi.quickHealthCheck() }.getOrDefault(false)
            if (ok) {
                notify("Endpoint de detalle funcionando")
            } else {
                notify("El endpoint falló. Ejecutando descubrimiento completo (~30 s)…")
                runCatching { SjfApi.discoverApi() }
                notify("Descubrimiento finalizado. Revisa el registro de red.")
            }
            _discoveryRunning.value = false
        }
    }

    fun runDiagnostic() {
        if (_diagnosticRunning.value) return
        viewModelScope.launch {
            _diagnosticRunning.value = true
            val log = mutableListOf<String>()
            fun line(text: String) { log += text; _diagnostic.value = log.toList() }
            fun stamp() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            NetLog.log("── Diagnóstico de conexión iniciado ──")
            line("${stamp()} — Estableciendo sesión con sjf2.scjn.gob.mx…")
            val session = SjfApi.establishSession()
            if (session.isSuccess) {
                line("${stamp()} — Sesión establecida ✓ (${SjfApi.cookieCount()} cookies capturadas)")
                line("${stamp()} — Probando GET /health…")
                try {
                    val (code, ok) = SjfApi.health()
                    line("${stamp()} — /health → HTTP $code ${if (ok) "✓" else "✗"}")
                } catch (e: Exception) { line("${stamp()} — /health ✗ ${e.message}") }
                line("${stamp()} — Probando POST /tesis (búsqueda «amparo»)…")
                try {
                    val response = SjfApi.search("amparo", null, null, 0, size = 20)
                    line("${stamp()} — HTTP 200 ✓ · ${response.totalElements} resultados · ${response.documents.size} documentos")
                    line("${stamp()} — Conexión directa disponible · modo EN VIVO")
                    if (settings.value.apiDirect && _search.value.searched) {
                        lanzarBusqueda(_search.value.query.trim())
                    }
                } catch (e: Exception) {
                    line("${stamp()} — POST /tesis ✗ ${e.message}")
                    line("${stamp()} — Sin conexión directa · modo LOCAL")
                }
            } else {
                line("${stamp()} — ✗ Fallo de sesión: ${session.exceptionOrNull()?.message}")
                line("${stamp()} — Sin conexión directa · modo LOCAL")
            }
            NetLog.log("── Diagnóstico finalizado ──")
            _diagnosticRunning.value = false
        }
    }
}
