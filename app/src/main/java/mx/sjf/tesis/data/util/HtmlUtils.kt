package mx.sjf.tesis.data.util

/** Decodifica entidades HTML comunes y numéricas. */
fun decodificarEntidades(s: String): String {
    var r = Regex("&#(\\d+);").replace(s) { m ->
        m.groupValues[1].toIntOrNull()?.let { if (it in 0..65535) it.toChar().toString() else m.value } ?: m.value
    }
    r = Regex("&#x([0-9a-fA-F]+);").replace(r) { m ->
        m.groupValues[1].toIntOrNull(16)?.let { if (it in 0..65535) it.toChar().toString() else m.value } ?: m.value
    }
    val mapa = listOf(
        "&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'",
        "&aacute;" to "á", "&eacute;" to "é", "&iacute;" to "í", "&oacute;" to "ó", "&uacute;" to "ú", "&uuml;" to "ü",
        "&Aacute;" to "Á", "&Eacute;" to "É", "&Iacute;" to "Í", "&Oacute;" to "Ó", "&Uacute;" to "Ú", "&Uuml;" to "Ü",
        "&ntilde;" to "ñ", "&Ntilde;" to "Ñ", "&iexcl;" to "¡", "&iquest;" to "¿",
        "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
        "&laquo;" to "«", "&raquo;" to "»"
    )
    (mapa + ("&amp;" to "&")).forEach { (k, v) -> r = r.replace(k, v) }
    return r
}

/** Elimina todas las etiquetas HTML y devuelve texto plano. */
fun limpiarHtml(s: String): String =
    decodificarEntidades(s.replace(Regex("""(?i)<br\s*/?>"""), "\n").replace(Regex("<[^>]+>"), ""))

/** Un fragmento de texto con marca de si era enlace. */
data class Fragmento(val texto: String, val esEnlace: Boolean)

/** Convierte texto con HTML en una lista de párrafos, cada uno con sus fragmentos. */
fun parrafosDeTexto(texto: String): List<List<Fragmento>> {
    if (texto.isBlank()) return emptyList()
    val normalizado = if (texto.contains('<'))
        texto.replace(Regex("""(?is)</p\s*>"""), "\n\n")
             .replace(Regex("""(?is)<br\s*/?>"""), "\n")
    else texto
    return normalizado.split(Regex("\\n\\n+")).map { parrafo ->
        val limpio = parrafo.trim()
        if (limpio.isEmpty()) emptyList() else fragmentar(limpio)
    }.filter { it.isNotEmpty() }
}

private fun fragmentar(parrafo: String): List<Fragmento> {
    if (!parrafo.contains('<'))
        return listOf(Fragmento(decodificarEntidades(parrafo).trim(), false))
    val frag = mutableListOf<Fragmento>()
    var resto = parrafo
    val reEnlace = Regex("""(?is)<a\b[^>]*>(.*?)</a>""")
    while (resto.isNotEmpty()) {
        val m = reEnlace.find(resto)
        if (m == null) { frag += Fragmento(limpiarHtml(resto).trim(), false); break }
        if (m.range.first > 0) frag += Fragmento(limpiarHtml(resto.substring(0, m.range.first)).trim(), false)
        frag += Fragmento(limpiarHtml(m.groupValues[1]).trim(), true)
        resto = resto.substring(m.range.last + 1)
    }
    // Separación alrededor de los enlaces: sin esto, los fragmentos recortados
    // se pegan ("la<enlace>tesis</enlace>sigue" → "latesissigue"). Solo se
    // añade espacio cuando el vecino empieza/termina en letra o dígito, para
    // no abrir hueco ante puntuación ("tesis</a>." → "tesis." y no "tesis .").
    val separados = mutableListOf<Fragmento>()
    frag.forEachIndexed { i, f ->
        var texto = f.texto
        val previo = frag.getOrNull(i - 1)
        val siguiente = frag.getOrNull(i + 1)
        if (!f.esEnlace && previo?.esEnlace == true && texto.isNotEmpty() && texto.first().isLetterOrDigit())
            texto = " $texto"
        if (!f.esEnlace && siguiente?.esEnlace == true && texto.isNotEmpty() && texto.last().isLetterOrDigit())
            texto = "$texto "
        separados += f.copy(texto = texto)
    }
    return separados.filter { it.texto.isNotBlank() }
}

/** Texto plano a partir de HTML o texto con párrafos. */
fun textoPlano(texto: String): String =
    parrafosDeTexto(texto).joinToString("\n\n") { p -> p.joinToString("") { it.texto } }.trim()
