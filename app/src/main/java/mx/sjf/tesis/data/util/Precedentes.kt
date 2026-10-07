package mx.sjf.tesis.data.util

/**
 * Un asunto que integra la tesis: «Amparo directo en revisión 6627/2025» y
 * sus datos (fecha, votación, ponente, secretario).
 */
data class Precedente(val asunto: String, val detalle: String)

/** Precedentes separados en asuntos y notas (aprobación, notas, criterios contendientes…). */
data class PrecedentesDivididos(val asuntos: List<Precedente>, val notas: List<String>)

/**
 * La API entrega en un solo campo los asuntos que dieron origen a la tesis y,
 * mezclados con ellos, avisos de aprobación («El Tribunal Pleno… aprobó…»),
 * notas («Nota: Esta tesis interrumpe…») y criterios contendientes. Una
 * jurisprudencia por reiteración trae cinco asuntos. Esta función los separa
 * para mostrarlos como lista numerada seguida de las notas.
 *
 * Un párrafo es un asunto si empieza con el nombre de un tipo de asunto y su
 * número («Amparo directo 426/2022.», «Contradicción de tesis 5/2018.»,
 * «Amparo en revisión 3066/98.»). Todo lo demás se conserva como nota, sin
 * perder nada del texto oficial.
 */
fun dividirPrecedentes(texto: String): PrecedentesDivididos {
    val parrafos = textoPlano(texto)
        .split(Regex("\\n+"))
        .map { it.replace(Regex("[ \\t]+"), " ").trim() }
        .filter { it.isNotEmpty() }
    val asuntos = mutableListOf<Precedente>()
    val notas = mutableListOf<String>()
    for (p in parrafos) {
        val m = ASUNTO.find(p)
        if (m != null && NO_ASUNTO.none { p.startsWith(it, ignoreCase = true) }) {
            asuntos += Precedente(m.groupValues[1].trim(), m.groupValues[2].trim())
        } else {
            notas += p
        }
    }
    return PrecedentesDivididos(asuntos, notas)
}

// Tipo de asunto (palabras, sin punto ni dos puntos) + número «N/AAAA» (y lo
// que lo acompañe, p. ej. «y su acumulada 3/2020») hasta el primer punto.
private val ASUNTO = Regex("""^([A-ZÁÉÍÓÚÑ][^.:\n]{2,140}?\b\d+/\d{2,4}\b[^.\n]{0,120}?)\.\s*(.*)$""")

private val NO_ASUNTO = listOf("Nota", "Notas", "El ", "La ", "Las ", "Los ", "Esta ", "Este ", "Tesis", "Criterio")

/** Clases de voto que se publican en el Semanario. */
private val TIPOS_VOTO = listOf(
    "particular", "concurrente", "aclaratorio", "minoritario", "de minoría",
    "razonado", "en contra", "disidente", "paralelo"
)

/**
 * Título y clase de un voto a partir de su texto. El primer párrafo del texto
 * oficial lo describe: «Voto particular que formula la Ministra Lenia Batres
 * Guadarrama relativo al amparo directo en revisión 6627/2025.»
 */
fun tituloYTipoDeVoto(texto: String): Pair<String, String> {
    val primero = textoPlano(texto)
        .split(Regex("\\n+"))
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return "Voto" to ""
    val titulo = primero.trimEnd('.').let { if (it.length > 240) it.take(237).trimEnd() + "…" else it }
    val tipo = Regex("""\bvotos?\s+(${TIPOS_VOTO.joinToString("|")})""", RegexOption.IGNORE_CASE)
        .find(primero)?.groupValues?.get(1)?.lowercase() ?: ""
    return titulo to tipo
}

/**
 * Quién formula el voto, sin repetir el tipo que ya se muestra aparte:
 * «Voto paralelo del Ministro Sergio Salvador Aguirre Anguiano» → «Ministro
 * Sergio Salvador Aguirre Anguiano». Si el título no sigue ese patrón se
 * devuelve completo.
 */
fun autorDeVoto(titulo: String): String {
    val sinTipo = Regex(
        """^votos?\s+(?:${TIPOS_VOTO.joinToString("|")})\s+(?:que\s+formulan?\s+)?(?:(?:del|de\s+la|de\s+los|de\s+las|el|la|los|las)\s+)?""",
        RegexOption.IGNORE_CASE
    ).replaceFirst(titulo.trim(), "")
    return if (sinTipo.length < 3 || sinTipo == titulo.trim()) titulo.trim()
           else sinTipo.replaceFirstChar { it.uppercase() }
}
