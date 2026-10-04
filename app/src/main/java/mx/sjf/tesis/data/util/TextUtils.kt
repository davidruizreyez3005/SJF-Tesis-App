package mx.sjf.tesis.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Elimina acentos y diacríticos para búsquedas insensibles a mayúsculas. */
fun String.foldAccents(): String = buildString {
    for (c in this@foldAccents) append(when (c) {
        'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'; 'ñ' -> 'n'
        'Á' -> 'A'; 'É' -> 'E'; 'Í' -> 'I'; 'Ó' -> 'O'; 'Ú' -> 'U'; 'Ü' -> 'U'; 'Ñ' -> 'N'
        else -> c
    })
}

/** Cuenta cuántas veces aparece un término en una cadena. */
fun String.countOccurrences(term: String): Int {
    var count = 0
    var index = indexOf(term)
    while (index >= 0) { count++; index = indexOf(term, index + term.length) }
    return count
}

// ── Fechas ──

/** Nombres de mes en español, en minúsculas (convención del SJF). */
val NOMBRES_MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
    "septiembre", "octubre", "noviembre", "diciembre"
)

/** La Gaceta antigua escribe «setiembre»; se normaliza a septiembre. */
private val ALIAS_MESES = mapOf("setiembre" to "septiembre")

private fun numeroMes(nombre: String): Int? {
    val n = nombre.trim().lowercase()
    val canónico = ALIAS_MESES[n] ?: n
    return NOMBRES_MESES.indexOf(canónico).takeIf { it >= 0 }?.plus(1)
}

/** Alternación de meses para las expresiones de extracción. */
private const val PATRON_MES =
    "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre"

/** Fecha completa en español: «26 de enero del 2018» (acepta «de» y «del»). */
private val RX_FECHA_COMPLETA =
    Regex("""(\d{1,2})\s+de\s+($PATRON_MES)\s+del?\s+(\d{4})""", RegexOption.IGNORE_CASE)

/** Solo mes y año: «Febrero de 2015» — el formato de la Gaceta impresa. */
private val RX_FECHA_MES_ANIO =
    Regex("""($PATRON_MES)\s+del?\s+(\d{4})""", RegexOption.IGNORE_CASE)

/** ISO o yyyy/MM/dd: «2018-01-26», «2018-01-26T00:00:00Z», «2018/01/26». */
private val RX_FECHA_ISO = Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})""")

/** dd/MM/yyyy o dd-MM-yyyy: «26/01/2018». */
private val RX_FECHA_DDMMYYYY = Regex("""^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$""")

/** Solo mes y año numérico: «09/2013», «9-2013» — formato de la API de búsqueda. */
private val RX_FECHA_MMAAAA = Regex("""^(\d{1,2})[/-](\d{4})$""")

/** Epoch en dígitos: segundos (9-10) o milisegundos (12-13). */
private val RX_EPOCH = Regex("""^-?\d{9,14}$""")

/** Año suelto: «2018». */
private val RX_ANIO = Regex("""^((?:19|20)\d{2})$""")

/** Rango plausible para una fecha del SJF (Quinta Época en adelante). */
private const val ANIO_MIN = 1910
private const val ANIO_MAX = 2036

/**
 * Fecha estructurada. [dia] es null cuando solo se conoce mes y año («Febrero
 * de 2015», típico de la Gaceta impresa) y [mes] es null cuando solo hay año.
 */
data class FechaLegible(val dia: Int?, val mes: Int?, val anio: Int) {

    /** Marca de tiempo (día 1 del mes cuando el día se desconoce): para ordenar. */
    val time: Long
        get() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(anio, (mes ?: 1) - 1, dia ?: 1)
        }.timeInMillis

    /** Forma legible: «26 de enero del 2018» / «enero del 2018» / «2018». */
    fun legible(): String = when {
        dia != null && mes != null -> "$dia de ${NOMBRES_MESES[mes - 1]} del $anio"
        mes != null -> "${NOMBRES_MESES[mes - 1]} del $anio"
        else -> anio.toString()
    }

    /** Forma compacta para tarjetas: «26/01/2018» / «01/2018» / «2018». */
    fun compacta(): String = when {
        dia != null && mes != null -> "%02d/%02d/%04d".format(dia, mes, anio)
        mes != null -> "%02d/%04d".format(mes, anio)
        else -> anio.toString()
    }
}

/**
 * Normaliza un epoch (en segundos o milisegundos) a milisegundos si cae en el
 * rango plausible del SJF (1910–2036). Devuelve null para 0 y basura.
 *
 * Nota: el orden importa — un epoch en segundos (p. ej. 1_422_748_800) también
 * cae numéricamente dentro del rango en milisegundos, así que el rango corto
 * (segundos) se evalúa primero.
 */
fun epochPlausibleMs(v: Long): Long? = when (v) {
    0L -> null
    in -1_900_000_000L..2_100_000_000L -> v * 1000L        // segundos (1910–2036)
    in -1_900_000_000_000L..2_100_000_000_000L -> v        // milisegundos (1910–2036)
    else -> null
}

/** Convierte un epoch (segundos o milisegundos) a fecha estructurada. */
fun epochAFechaLegible(v: Long): FechaLegible? {
    val ms = epochPlausibleMs(v) ?: return null
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
    val anio = cal.get(Calendar.YEAR)
    return if (anio in ANIO_MIN..ANIO_MAX)
        FechaLegible(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, anio)
    else null
}

/** Convierte un epoch (segundos o milisegundos) a texto legible, o null. */
fun fechaLegibleDeEpoch(v: Long): String? = epochAFechaLegible(v)?.legible()

/**
 * Busca una fecha DENTRO de un texto libre (publicación, localización):
 * primero una fecha completa («26 de enero del 2018») y luego mes-año
 * («Febrero de 2015»). La Gaceta impresa y las épocas antiguas solo traen
 * mes y año en esos campos.
 */
fun extraerFecha(texto: String): FechaLegible? {
    if (texto.isBlank()) return null
    RX_FECHA_COMPLETA.find(texto)?.let { m ->
        val dia = m.groupValues[1].toIntOrNull()
        val mes = numeroMes(m.groupValues[2])
        val anio = m.groupValues[3].toIntOrNull()
        if (dia != null && mes != null && anio != null &&
            dia in 1..31 && anio in ANIO_MIN..ANIO_MAX
        ) return FechaLegible(dia, mes, anio)
    }
    RX_FECHA_MES_ANIO.find(texto)?.let { m ->
        val mes = numeroMes(m.groupValues[1])
        val anio = m.groupValues[2].toIntOrNull()
        if (mes != null && anio != null && anio in ANIO_MIN..ANIO_MAX)
            return FechaLegible(null, mes, anio)
    }
    return null
}

/**
 * Intenta parsear una fecha en cualquiera de los formatos que usa la API del
 * SJF: español humano («26 de enero del 2018» o «Febrero de 2015»), ISO
 * («2018-01-26»), numérico («26/01/2018» y «09/2013»), epoch (segundos o
 * milisegundos) o año suelto. Devuelve null si no logra.
 */
fun parseFechaLegible(fecha: String): FechaLegible? {
    val s = fecha.trim()
    if (s.isEmpty()) return null
    RX_EPOCH.find(s)?.let { m ->
        m.value.toLongOrNull()?.let { v -> return epochAFechaLegible(v) }
    }
    RX_FECHA_ISO.find(s)?.let { m ->
        val anio = m.groupValues[1].toIntOrNull()
        val mes = m.groupValues[2].toIntOrNull()
        val dia = m.groupValues[3].toIntOrNull()
        if (anio != null && mes != null && dia != null &&
            mes in 1..12 && dia in 1..31 && anio in ANIO_MIN..ANIO_MAX
        ) return FechaLegible(dia, mes, anio)
    }
    RX_FECHA_DDMMYYYY.find(s)?.let { m ->
        val dia = m.groupValues[1].toIntOrNull()
        val mes = m.groupValues[2].toIntOrNull()
        val anio = m.groupValues[3].toIntOrNull()
        if (dia != null && mes != null && anio != null &&
            mes in 1..12 && dia in 1..31 && anio in ANIO_MIN..ANIO_MAX
        ) return FechaLegible(dia, mes, anio)
    }
    // «09/2013»: la API de búsqueda entrega mes-año numérico en algunos
    // registros (sobre todo Gaceta); sin día conocido no se fabrica uno.
    RX_FECHA_MMAAAA.find(s)?.let { m ->
        val mes = m.groupValues[1].toIntOrNull()
        val anio = m.groupValues[2].toIntOrNull()
        if (mes != null && anio != null &&
            mes in 1..12 && anio in ANIO_MIN..ANIO_MAX
        ) return FechaLegible(null, mes, anio)
    }
    RX_ANIO.find(s)?.let { m ->
        m.value.toIntOrNull()?.let { anio -> return FechaLegible(null, null, anio) }
    }
    return extraerFecha(s)
}

/** Marca de tiempo de una fecha en texto (0L si no se puede parsear): para ordenar. */
fun parseFecha(fecha: String): Long = parseFechaLegible(fecha)?.time ?: 0L

/** Formato compacto para tarjetas de resultado: "26/01/2018". */
fun formatDateCompact(fecha: String): String {
    if (fecha.isBlank()) return "s/f"
    val ts = parseFechaLegible(fecha) ?: return fecha.take(20)  // mostrar tal cual si no se pudo parsear
    return ts.compacta()
}

/** Formato largo premium para la hoja de detalle: "26 de enero del 2018". */
fun formatDateLong(fecha: String): String {
    if (fecha.isBlank()) return "Sin fecha"
    val ts = parseFechaLegible(fecha) ?: return fecha.trim().take(60)
    return ts.legible()
}

/** Tiempo relativo legible en español. */
fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Hace un momento"
        diff < 3_600_000L -> "Hace ${diff / 60_000L} min"
        diff < 86_400_000L -> "Hace ${diff / 3_600_000L} h"
        diff < 7 * 86_400_000L -> "Hace ${diff / 86_400_000L} d"
        else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

/** Extrae el año (4 dígitos) de una fecha en texto. */
fun extractYear(fecha: String): String =
    Regex("\\b(19|20)\\d{2}\\b").find(fecha)?.value ?: ""

/** Convierte "Tesis Aislada"/"Jurisprudencia" al código corto J/T usado por la SCJN. */
fun tipoGrupo(tipo: String): String {
    val t = tipo.lowercase()
    return when {
        t.contains("jurisprudencia") -> "J"
        t.contains("aislada") || t.contains("tesis") -> "T"
        else -> ""
    }
}

/** Acorta un texto largo con elipsis respetando límites de palabra. */
fun truncateWords(text: String, max: Int): String {
    if (text.length <= max) return text
    val cut = text.take(max)
    val lastSpace = cut.lastIndexOf(' ').coerceAtLeast(0)
    return cut.substring(0, lastSpace).trim() + "…"
}

/** Limpia un nombre de archivo: sin caracteres prohibidos por el sistema de archivos. */
fun sanitizeFileName(name: String): String {
    val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    val truncated = if (cleaned.length > 60) cleaned.take(60) else cleaned
    return truncated.ifBlank { "tesis" }
}
