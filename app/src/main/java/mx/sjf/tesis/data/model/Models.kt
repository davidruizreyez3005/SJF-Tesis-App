package mx.sjf.tesis.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modelo central de una tesis o jurisprudencia del Semanario Judicial de la Federación.
 */
data class Tesis(
    val registro: Long = 0L,
    val rubro: String = "",
    val texto: String = "",
    val epoca: String = "",
    val instancia: String = "",
    val tipo: String = "",
    val materia: String = "",
    val fecha: String = "",
    val precedente: String = "",
    /** Clave o número de identificación de la tesis, p. ej. «P./J. 191/2026 (12a.)». */
    val clave: String = "",
    /** Publicación oficial: «Semanario Judicial de la Federación» o «… y su Gaceta». */
    val fuente: String = "",
    /** Libro o tomo y mes de la publicación, p. ej. «Libro XXIII, Agosto de 2013». */
    val volumen: String = "",
    /** Tomo dentro del libro (Gaceta de la Décima Época), p. ej. «Tomo 3». */
    val tomo: String = "",
    /** Página impresa; vacía en publicaciones solo digitales. */
    val pagina: String = "",
    /** Nota oficial de publicación («Esta tesis se publicó el viernes …»). */
    val publicacion: String = ""
) {
    val esJurisprudencia: Boolean
        get() = tipo.contains("Jurisprudencia", ignoreCase = true)

    /** True si la tesis trae los datos de localización oficiales completos. */
    val tieneDatosOficiales: Boolean
        get() = clave.isNotBlank() && fuente.isNotBlank()

    val urlDetalle: String
        get() = "https://sjf2.scjn.gob.mx/detalle/tesis/$registro"

    /**
     * Rellena los campos vacíos con los metadatos de otra instancia de la
     * misma tesis (típicamente el resultado de búsqueda desde el que se abrió).
     *
     * Motivo: el endpoint de detalle NO devuelve siempre todos los metadatos —
     * según la edición del Semanario faltan «fecha», «época» o «instancia» — y
     * sin este relleno la hoja de detalle perdía datos que la búsqueda sí
     * traía (p. ej. registros 2004415/2004236 quedaban sin fecha).
     *
     * El texto y el registro NUNCA se heredan: pertenecerían a otro documento.
     */
    fun conMetadatosDe(base: Tesis): Tesis = copy(
        rubro = rubro.ifBlank { base.rubro },
        epoca = epoca.ifBlank { base.epoca },
        instancia = instancia.ifBlank { base.instancia },
        tipo = tipo.ifBlank { base.tipo },
        materia = materia.ifBlank { base.materia },
        fecha = fecha.ifBlank { base.fecha },
        precedente = precedente.ifBlank { base.precedente },
        clave = clave.ifBlank { base.clave },
        fuente = fuente.ifBlank { base.fuente },
        volumen = volumen.ifBlank { base.volumen },
        tomo = tomo.ifBlank { base.tomo },
        pagina = pagina.ifBlank { base.pagina },
        publicacion = publicacion.ifBlank { base.publicacion }
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("ius", registro)
        put("rubro", rubro)
        put("texto", texto)
        put("epoca", epoca)
        put("instancia", instancia)
        put("tipo", tipo)
        put("materia", materia)
        put("fecha", fecha)
        put("precedente", precedente)
        put("clave", clave)
        put("fuente", fuente)
        put("volumen", volumen)
        put("tomo", tomo)
        put("pagina", pagina)
        put("publicacion", publicacion)
    }

    companion object {
        fun fromJson(o: JSONObject) = Tesis(
            registro = if (o.optLong("ius", 0L) != 0L) o.optLong("ius") else o.optLong("registro", 0L),
            rubro = o.optString("rubro"),
            texto = o.optString("texto"),
            epoca = o.optString("epoca"),
            instancia = o.optString("instancia"),
            tipo = o.optString("tipo"),
            materia = o.optString("materia"),
            fecha = o.optString("fecha"),
            precedente = o.optString("precedente"),
            clave = o.optString("clave"),
            fuente = o.optString("fuente"),
            volumen = o.optString("volumen"),
            tomo = o.optString("tomo"),
            pagina = o.optString("pagina"),
            publicacion = o.optString("publicacion")
        )

        fun fromJsonArray(raw: String): List<Tesis> = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let(::fromJson)
            }
        }.getOrDefault(emptyList())
    }
}

/** Respuesta paginada de la API de búsqueda. */
data class SearchResponse(
    val documents: List<Tesis>,
    val totalElements: Int,
    val totalPages: Int,
    val page: Int
)

/** Tema de la interfaz: seguir al sistema o forzar claro/oscuro. */
enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro")
}

/** Configuración de usuario persistida. */
data class Settings(
    val apiDirect: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1.0f,
    val defaultCitationStyle: CitationStyle = CitationStyle.LEGAL
)

/** Estilo de cita juridica disponible para el usuario. */
enum class CitationStyle(val label: String, val description: String) {
    LEGAL("Cita jurídica", "Para escritos y demandas: clave, localización, rubro y texto completo"),
    SCJN("Ficha oficial", "Ficha del Semanario Judicial de la Federación, campo por campo"),
    ACADEMIC("Académica", "Criterios editoriales de la SCJN para publicaciones académicas"),
    PLAIN("Cita breve", "Una sola línea con clave, rubro y localización, para notas al pie"),
    HTML("HTML", "Fragmento HTML para incrustar en páginas web")
}

/** Entrada del historial de busquedas. */
data class HistoryEntry(
    val id: Long,
    val query: String,
    val timestamp: Long
)

/**
 * Estado de la operación de descarga por lotes.
 *
 * `Done` soporta dos formatos de salida:
 *  - Archivo único (PDF combinado o ZIP): [outputUri] apunta al archivo.
 *  - Varios archivos sueltos (descarga por lote): [uris] trae todos y
 *    [fileName] describe el conjunto ("N archivos").
 */
sealed class BatchState {
    object Idle : BatchState()
    data class Progress(
        val current: Int,
        val total: Int,
        val currentRubro: String,
        val stage: String = "Descargando"
    ) : BatchState()
    data class Done(
        val successes: Int,
        val failures: Int,
        val outputUri: android.net.Uri,
        val fileName: String,
        val mimeType: String,
        val uris: List<android.net.Uri> = emptyList()
    ) : BatchState()
    data class Error(val message: String) : BatchState()
}

/** Tipo de exportación a PDF seleccionado por el usuario. */
enum class PdfExportMode(val label: String, val description: String) {
    COMBINED("PDF combinado", "Todas las tesis en un solo documento PDF"),
    ZIP("ZIP de PDFs", "Cada tesis como PDF individual, comprimidas en un ZIP")
}
