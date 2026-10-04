package mx.sjf.tesis.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro global de actividad de red: cada solicitud HTTP queda asentada con hora
 * y resultado. Se muestra en el panel de depuración y se puede exportar.
 */
object NetLog {
    data class Entry(val time: String, val text: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries = _entries.asStateFlow()

    private fun stamp(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    fun log(text: String) {
        _entries.update { (it + Entry(stamp(), text)).takeLast(400) }
    }

    fun clear() { _entries.value = emptyList() }

    fun fullText(): String = buildString {
        appendLine("SJF Tesis v${Constants.APP_VERSION} · Registro de red")
        appendLine("Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("Sesión SCJN: ${if (mx.sjf.tesis.data.remote.SjfApi.hasSession()) "activa (${mx.sjf.tesis.data.remote.SjfApi.cookieCount()} cookies)" else "sin establecer"}")
        appendLine("Cuerpo de búsqueda: ${mx.sjf.tesis.data.remote.SjfApi.cuerpoActivo()}")
        appendLine("Texto completo: ${mx.sjf.tesis.data.remote.SjfApi.estadoDetalle()}")
        appendLine("Cachés: ${mx.sjf.tesis.data.remote.SjfApi.cacheStats()}")
        appendLine("────────────────────────────────────────────")
        if (_entries.value.isEmpty()) appendLine("(sin eventos registrados)")
        _entries.value.forEach { appendLine("[${it.time}] ${it.text}") }
    }
}
