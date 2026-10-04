package mx.sjf.tesis.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.sjf.tesis.data.model.CitationStyle
import mx.sjf.tesis.data.model.HistoryEntry
import mx.sjf.tesis.data.model.Settings
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistencia local vía SharedPreferences: configuración del usuario, tesis
 * guardadas (favoritos) e historial de búsquedas.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("sjf_tesis_store", Context.MODE_PRIVATE)

    val settings = MutableStateFlow(readSettings())
    val saved = MutableStateFlow(readSaved())
    val history = MutableStateFlow(readHistory())

    val settingsFlow = settings.asStateFlow()
    val savedFlow = saved.asStateFlow()
    val historyFlow = history.asStateFlow()

    fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(settings.value)
        settings.value = next
        prefs.edit()
            .putBoolean("apiDirect", next.apiDirect)
            .putString("themeMode", next.themeMode.name)
            .putFloat("fontScale", next.fontScale)
            .putString("citationStyle", next.defaultCitationStyle.name)
            .apply()
    }

    fun toggleSaved(tesis: Tesis): Boolean {
        val current = saved.value
        val exists = current.any { it.registro == tesis.registro }
        val next = if (exists) current.filterNot { it.registro == tesis.registro }
                   else listOf(tesis) + current
        persistSaved(next)
        return !exists
    }

    /** Actualiza una tesis guardada con datos más completos (si está guardada). */
    fun updateSaved(tesis: Tesis) {
        val current = saved.value
        val index = current.indexOfFirst { it.registro == tesis.registro }
        if (index < 0 || current[index] == tesis) return
        persistSaved(current.toMutableList().apply { this[index] = tesis })
    }

    /** Reinserta una tesis en su posición original (acción «Deshacer»). */
    fun restoreSaved(tesis: Tesis, index: Int) {
        val current = saved.value.filterNot { it.registro == tesis.registro }
        persistSaved(current.toMutableList().apply { add(index.coerceIn(0, size), tesis) })
    }

    /** Reemplaza toda la lista de guardadas (deshacer un «borrar todo»). */
    fun restoreAllSaved(list: List<Tesis>) = persistSaved(list)

    private fun persistSaved(next: List<Tesis>) {
        saved.value = next
        val arr = JSONArray()
        next.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("saved", arr.toString()).apply()
    }

    fun clearSaved() {
        saved.value = emptyList()
        prefs.edit().remove("saved").apply()
    }

    fun addHistory(query: String) {
        val now = System.currentTimeMillis()
        val next = (listOf(HistoryEntry(now, query, now)) +
            history.value.filterNot { it.query == query }).take(50)
        history.value = next
        persistHistory()
    }

    fun removeHistory(id: Long) {
        history.value = history.value.filterNot { it.id == id }
        persistHistory()
    }

    /** Reinserta una entrada borrada en su posición original (acción «Deshacer»). */
    fun restoreHistory(entry: HistoryEntry, index: Int) {
        val current = history.value.filterNot { it.id == entry.id }
        history.value = current.toMutableList().apply { add(index.coerceIn(0, size), entry) }
        persistHistory()
    }

    fun restoreAllHistory(list: List<HistoryEntry>) {
        history.value = list
        persistHistory()
    }

    fun clearHistory() {
        history.value = emptyList()
        persistHistory()
    }

    private fun persistHistory() {
        val arr = JSONArray()
        history.value.forEach {
            arr.put(JSONObject().put("id", it.id).put("q", it.query).put("t", it.timestamp))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun readSettings(): Settings = Settings(
        apiDirect = prefs.getBoolean("apiDirect", true),
        themeMode = readThemeMode(),
        fontScale = prefs.getFloat("fontScale", 1.0f),
        defaultCitationStyle = runCatching {
            CitationStyle.valueOf(prefs.getString("citationStyle", CitationStyle.LEGAL.name)!!)
        }.getOrDefault(CitationStyle.LEGAL)
    )

    /**
     * Lee el tema. Las versiones ≤ 2.2 guardaban un booleano «darkMode»: si el
     * usuario ya había elegido uno, se respeta; en instalaciones nuevas se
     * sigue al sistema.
     */
    private fun readThemeMode(): ThemeMode {
        prefs.getString("themeMode", null)?.let { raw ->
            return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
        }
        if (prefs.contains("darkMode")) {
            return if (prefs.getBoolean("darkMode", true)) ThemeMode.DARK else ThemeMode.LIGHT
        }
        return ThemeMode.SYSTEM
    }

    private fun readSaved(): List<Tesis> {
        val raw = prefs.getString("saved", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Tesis::fromJson) }
        }.getOrDefault(emptyList())
    }

    private fun readHistory(): List<HistoryEntry> {
        val raw = prefs.getString("history", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    HistoryEntry(it.optLong("id"), it.optString("q"), it.optLong("t"))
                }
            }
        }.getOrDefault(emptyList())
    }
}
