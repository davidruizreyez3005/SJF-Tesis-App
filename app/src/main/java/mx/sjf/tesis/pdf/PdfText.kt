package mx.sjf.tesis.pdf

import java.text.Normalizer

/**
 * Fuentes estándar del PDF que usa el motor. No se incrustan: todo lector de
 * PDF las trae, por eso los archivos pesan una fracción de los que genera el
 * sitio de la SCJN.
 */
enum class PdfFont(val recurso: String, val nombrePs: String, private val anchos: IntArray) {
    ROMAN("F1", "Times-Roman", TimesMetrics.ROMAN),
    BOLD("F2", "Times-Bold", TimesMetrics.BOLD),
    ITALIC("F3", "Times-Italic", TimesMetrics.ITALIC),
    BOLD_ITALIC("F4", "Times-BoldItalic", TimesMetrics.BOLD_ITALIC);

    /** Ancho en puntos de un texto ya codificado en WinAnsi. */
    fun ancho(codificado: ByteArray, tamano: Float): Float {
        var milesimas = 0
        for (b in codificado) milesimas += anchos[b.toInt() and 0xFF]
        return milesimas * tamano / 1000f
    }

    fun ancho(texto: String, tamano: Float): Float = ancho(WinAnsi.codificar(texto), tamano)

    fun anchoEspacio(tamano: Float): Float = anchos[32] * tamano / 1000f
}

/**
 * Codificación WinAnsi (la de las fuentes estándar del PDF). Cubre todo el
 * español: vocales acentuadas, ñ, ü, ¿, ¡, «», comillas tipográficas, rayas.
 * Lo que no existe en WinAnsi se sustituye por su equivalente más cercano.
 */
object WinAnsi {

    // Bloque 0x80–0x9F de WinAnsi (los demás códigos coinciden con Latin-1).
    private val ESPECIALES = mapOf(
        '€' to 0x80, '‚' to 0x82, 'ƒ' to 0x83, '„' to 0x84, '…' to 0x85, '†' to 0x86, '‡' to 0x87,
        'ˆ' to 0x88, '‰' to 0x89, 'Š' to 0x8A, '‹' to 0x8B, 'Œ' to 0x8C, 'Ž' to 0x8E,
        '‘' to 0x91, '’' to 0x92, '“' to 0x93, '”' to 0x94, '•' to 0x95, '–' to 0x96, '—' to 0x97,
        '˜' to 0x98, '™' to 0x99, 'š' to 0x9A, '›' to 0x9B, 'œ' to 0x9C, 'ž' to 0x9E, 'Ÿ' to 0x9F
    )

    // Sustituciones para caracteres frecuentes en los textos de la SCJN que no
    // están en WinAnsi.
    private val SUSTITUTOS = mapOf(
        '№' to "No.", '−' to "-", '‐' to "-", '‑' to "-", '‒' to "–", '―' to "—",
        '\u00A0' to " ", '\u2002' to " ", '\u2003' to " ", '\u2007' to " ", '\u2009' to " ", '\u202F' to " ",
        '\u200B' to "", '\u200C' to "", '\u200D' to "", '\uFEFF' to "", '\u00AD' to "",
        '′' to "'", '″' to "\"", '→' to "->", '←' to "<-", '≤' to "<=", '≥' to ">=", '≠' to "!=",
        'ﬁ' to "fi", 'ﬂ' to "fl", '\t' to " ", '✓' to "", '▪' to "•", '◦' to "•", '●' to "•"
    )

    /** Código WinAnsi de un carácter, o null si no existe. */
    fun codigo(c: Char): Int? = when {
        c in ' '..'~' -> c.code
        c.code in 0xA0..0xFF -> c.code
        else -> ESPECIALES[c]
    }

    /**
     * Convierte texto Unicode a bytes WinAnsi. Lo que no tiene equivalente
     * directo se sustituye ([SUSTITUTOS]) o se le quitan los diacríticos
     * («ā» → «a»); en último caso queda «?».
     */
    fun codificar(texto: String): ByteArray {
        val salida = java.io.ByteArrayOutputStream(texto.length)
        for (c in texto) {
            // Primero las sustituciones: p. ej. el guion suave (U+00AD) está en
            // Latin-1 pero no debe imprimirse a mitad de palabra.
            val sustituto = SUSTITUTOS[c]
            if (sustituto != null) {
                for (s in sustituto) salida.write(codigo(s) ?: '?'.code)
                continue
            }
            val directo = codigo(c)
            if (directo != null) { salida.write(directo); continue }
            if (c == '\n' || c == '\r') { salida.write(' '.code); continue }
            val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                .firstOrNull { codigo(it) != null }
            salida.write(base?.let { codigo(it) } ?: '?'.code)
        }
        return salida.toByteArray()
    }
}
