package mx.sjf.tesis.data.util

import mx.sjf.tesis.data.model.CitationStyle
import mx.sjf.tesis.data.model.Tesis

/**
 * Constructor de citas de tesis del Semanario Judicial de la Federación.
 *
 * Todas las citas se arman con los datos de localización OFICIALES que
 * entrega la SCJN para cada registro:
 *
 *  - **Clave** de la tesis: «P./J. 191/2026 (12a.)», «XI.1o.A.T.8 K (10a.)».
 *  - **Fuente**: «Semanario Judicial de la Federación» (semanario electrónico,
 *    desde 2014) o «Semanario Judicial de la Federación y su Gaceta» (impreso).
 *  - En la Gaceta impresa: **libro/tomo, mes y página**.
 *  - En el semanario electrónico: **fecha y hora de publicación** (las tesis
 *    solo digitales no tienen página).
 *  - **Registro digital**.
 *
 * Referencias: la ficha de cada tesis en sjf2.scjn.gob.mx (Registro digital,
 * Tesis, Época, Tipo, Instancia, Materia(s), Fuente) y los criterios
 * editoriales del Centro de Estudios Constitucionales de la SCJN
 * (`Tesis «J.»: P./J. 18/91, Semanario Judicial de la Federación y su
 * Gaceta, Octava Época, tomo VII, junio de 1991, p. 52. Reg. digital 205798`).
 */
object CitationBuilder {

    private const val SCJN = "Suprema Corte de Justicia de la Nación"
    private const val SJF = "Semanario Judicial de la Federación"

    /** Construye la cita en el estilo solicitado. */
    fun build(tesis: Tesis, style: CitationStyle): String = when (style) {
        CitationStyle.LEGAL -> legalFormat(tesis)
        CitationStyle.SCJN -> scjnFormat(tesis)
        CitationStyle.ACADEMIC -> academicFormat(tesis)
        CitationStyle.PLAIN -> plainFormat(tesis)
        CitationStyle.HTML -> htmlFormat(tesis)
    }

    /**
     * Cita para escritos y demandas: frase de apoyo con clave, órgano emisor y
     * localización completa, seguida del rubro, el texto y los precedentes.
     *
     *   Lo anterior encuentra sustento en la jurisprudencia P./J. 191/2026
     *   (12a.), emitida por el Pleno de la Suprema Corte de Justicia de la
     *   Nación, publicada en el Semanario Judicial de la Federación, Duodécima
     *   Época, el viernes 2 de octubre de 2026 a las 10:12 horas, registro
     *   digital 2032690, de rubro y texto siguientes:
     */
    fun legalFormat(tesis: Tesis): String = buildString {
        append("Lo anterior encuentra sustento en la ")
        append(designacion(tesis))
        append(", emitida por ")
        append(emisor(tesis.instancia))
        append(", publicada en el ")
        append(localizacion(tesis, conFechaComoFrase = true))
        append(", registro digital ${tesis.registro}, de rubro y texto siguientes:")
        appendLine()
        appendLine()
        appendLine(rubro(tesis))
        if (tesis.texto.isNotBlank()) {
            appendLine()
            appendLine(textoPlano(tesis.texto))
        }
        if (tesis.precedente.isNotBlank()) {
            appendLine()
            append(textoPlano(tesis.precedente))
        }
    }.trimEnd()

    /**
     * Ficha oficial: los mismos campos, en el mismo orden, que la ficha de la
     * tesis en el Semanario Judicial de la Federación.
     */
    fun scjnFormat(tesis: Tesis): String = buildString {
        appendLine("Registro digital: ${tesis.registro}")
        if (tesis.clave.isNotBlank()) appendLine("Tesis: ${tesis.clave}")
        if (tesis.epoca.isNotBlank()) appendLine(epocaCompleta(tesis.epoca))
        tipoFicha(tesis)?.let { appendLine("Tipo: $it") }
        if (tesis.instancia.isNotBlank()) appendLine("Instancia: ${tesis.instancia}")
        if (tesis.materia.isNotBlank()) appendLine("Materia(s): ${tesis.materia}")
        append("Fuente: ${tesis.fuente.ifBlank { SJF }}")
        val datos = datosImpresos(tesis, academico = false)
        if (datos.isNotEmpty()) append(". ${datos.joinToString(", ")}")
        appendLine()
        appendLine()
        appendLine(rubro(tesis))
        if (tesis.texto.isNotBlank()) {
            appendLine()
            appendLine(textoPlano(tesis.texto))
        }
        if (tesis.precedente.isNotBlank()) {
            appendLine()
            appendLine(textoPlano(tesis.precedente))
        }
        if (tesis.publicacion.isNotBlank()) {
            appendLine()
            appendLine(normalizarEspacios(tesis.publicacion))
        }
        appendLine()
        append(tesis.urlDetalle)
    }.trimEnd()

    /**
     * Criterios editoriales de la SCJN (Centro de Estudios Constitucionales):
     *
     * ```
     * Tesis «J.»: P./J. 18/91, Semanario Judicial de la Federación y su
     * Gaceta, Octava Época, tomo VII, junio de 1991, p. 52. Reg. digital 205798.
     * ```
     *
     * Para Tribunales Colegiados se agrega «T.C.C.» después de la clave.
     */
    fun academicFormat(tesis: Tesis): String = buildString {
        append("Tesis [${if (tesis.esJurisprudencia) "J." else "A."}]: ")
        append(tesis.clave.ifBlank { "s. n." })
        abreviaturaOrgano(tesis.instancia)?.let { append(", $it") }
        append(", ${tesis.fuente.ifBlank { SJF }}")
        if (tesis.epoca.isNotBlank()) append(", ${epocaCompleta(tesis.epoca)}")
        val datos = datosImpresos(tesis, academico = true)
        when {
            datos.isNotEmpty() -> append(", ${datos.joinToString(", ")}")
            tesis.fecha.isNotBlank() -> append(", ${fechaParaCita(tesis.fecha)}")
        }
        append(if (tesis.pagina.isBlank()) ", s. p. " else ". ")
        append("Reg. digital ${tesis.registro}.")
    }

    /**
     * Cita breve en una sola línea, útil para notas al pie:
     *
     *   Jurisprudencia P./J. 191/2026 (12a.), de rubro: "ASISTENCIA CONSULAR…",
     *   Semanario Judicial de la Federación, Duodécima Época, publicada el
     *   viernes 2 de octubre de 2026 a las 10:12 horas, registro digital 2032690.
     */
    fun plainFormat(tesis: Tesis): String = buildString {
        append(designacion(tesis).replaceFirstChar { it.uppercase() })
        append(", de rubro: \"${rubro(tesis).trimEnd('.')}\", ")
        append(localizacion(tesis, conFechaComoFrase = false))
        append(", registro digital ${tesis.registro}.")
    }

    /** Referencia sin rubro (pie del PDF, donde el rubro ya aparece arriba). */
    fun referencia(tesis: Tesis): String =
        designacion(tesis).replaceFirstChar { it.uppercase() } + ", " +
            localizacion(tesis, conFechaComoFrase = false) + ", registro digital ${tesis.registro}."

    /** Fragmento HTML para incrustar en páginas web o blogs. */
    fun htmlFormat(tesis: Tesis): String = buildString {
        append("<p><strong>${escapeHtml(designacion(tesis).replaceFirstChar { it.uppercase() })}</strong>, ")
        append("de rubro: «<em>${escapeHtml(rubro(tesis).trimEnd('.'))}</em>», ")
        append(escapeHtml(localizacion(tesis, conFechaComoFrase = false)))
        append(", registro digital ")
        append("<a href=\"${tesis.urlDetalle}\" target=\"_blank\" rel=\"noopener\">${tesis.registro}</a>.</p>")
    }

    /** Construye una cita combinada para múltiples tesis. */
    fun buildBatch(tesis: List<Tesis>, style: CitationStyle): String =
        tesis.joinToString("\n\n———\n\n") { build(it, style) }

    // ── Piezas comunes ──

    /** «jurisprudencia P./J. 191/2026 (12a.)» o «tesis aislada I.9o.T.241 L». */
    fun designacion(tesis: Tesis): String {
        val tipo = when {
            tesis.esJurisprudencia -> "jurisprudencia"
            tesis.tipo.isNotBlank() -> "tesis aislada"
            else -> "tesis"
        }
        return if (tesis.clave.isNotBlank()) "$tipo ${tesis.clave}" else tipo
    }

    /**
     * Localización: fuente, época y —según la publicación— libro, tomo y
     * página (Gaceta impresa) o la fecha y hora de publicación (semanario
     * electrónico).
     *
     * @param conFechaComoFrase «…, Duodécima Época, el viernes 2 de octubre…»
     *   (prosa de un escrito) en lugar de «…, publicada el viernes …».
     */
    fun localizacion(tesis: Tesis, conFechaComoFrase: Boolean): String {
        val partes = mutableListOf(tesis.fuente.ifBlank { SJF })
        if (tesis.epoca.isNotBlank()) partes += epocaCompleta(tesis.epoca)
        val impresos = datosImpresos(tesis, academico = false)
        val publicada = fechaDePublicacion(tesis.publicacion)
        when {
            // Con página: se cita la publicación impresa (Gaceta).
            tesis.pagina.isNotBlank() -> partes += impresos
            // Solo digital: fecha y hora oficiales de publicación.
            publicada != null -> partes += if (conFechaComoFrase) "el $publicada" else "publicada el $publicada"
            impresos.isNotEmpty() -> partes += impresos
            tesis.fecha.isNotBlank() -> partes += fechaParaCita(tesis.fecha)
        }
        return partes.joinToString(", ")
    }

    /** Libro/tomo y mes (en minúscula, como se cita), tomo y página. */
    private fun datosImpresos(tesis: Tesis, academico: Boolean): List<String> {
        val partes = mutableListOf<String>()
        if (tesis.volumen.isNotBlank()) partes += mesesEnMinuscula(tesis.volumen).let { v ->
            if (academico) v.replaceFirstChar { it.lowercase() } else v
        }
        if (tesis.tomo.isNotBlank()) partes += if (academico) tesis.tomo.replaceFirstChar { it.lowercase() } else tesis.tomo
        if (tesis.pagina.isNotBlank()) partes += if (academico) "p. ${tesis.pagina}" else "página ${tesis.pagina}"
        return partes
    }

    /**
     * Extrae «viernes 2 de octubre de 2026 a las 10:12 horas» de la nota
     * oficial «Esta tesis se publicó el viernes 02 de octubre de 2026 a las
     * 10:12 horas en el Semanario Judicial de la Federación…».
     */
    fun fechaDePublicacion(nota: String): String? {
        val m = Regex("se public[óo] el (.+?) en el Semanario", RegexOption.IGNORE_CASE)
            .find(normalizarEspacios(nota)) ?: return null
        return quitarCeroInicial(m.groupValues[1].trim())
    }

    /**
     * Extrae la fecha desde la que la jurisprudencia es de aplicación
     * obligatoria («lunes 5 de octubre de 2026»), si la nota la menciona.
     */
    fun obligatoriaDesde(nota: String): String? {
        val m = Regex("aplicaci[óo]n obligatoria a partir del (.+?), para", RegexOption.IGNORE_CASE)
            .find(normalizarEspacios(nota)) ?: return null
        return quitarCeroInicial(m.groupValues[1].removePrefix("día hábil siguiente, ").trim())
    }

    /** Órgano emisor en prosa: «el Pleno de la Suprema Corte…», «un Tribunal Colegiado…». */
    fun emisor(instancia: String): String {
        val i = instancia.trim()
        return when {
            i.isBlank() -> "la $SCJN"
            i.equals("Pleno", true) -> "el Pleno de la $SCJN"
            i.endsWith("Sala", true) -> "la $i de la $SCJN"
            i.contains("Tribunales Colegiados", true) -> "un Tribunal Colegiado de Circuito"
            i.contains("Plenos de Circuito", true) -> "un Pleno de Circuito"
            i.contains("Plenos Regionales", true) -> "un Pleno Regional"
            i.startsWith("Pleno", true) || i.startsWith("Tribunal", true) -> "el $i"
            i.startsWith("Sala", true) -> "la $i"
            else -> i
        }
    }

    /** Abreviatura que los criterios editoriales agregan tras la clave. */
    private fun abreviaturaOrgano(instancia: String): String? = when {
        instancia.contains("Tribunales Colegiados", true) -> "T.C.C."
        instancia.contains("Plenos de Circuito", true) -> "P.C."
        instancia.contains("Plenos Regionales", true) -> "P.R."
        else -> null
    }

    /** Tipo tal como lo muestra la ficha oficial: «Jurisprudencia» o «Aislada». */
    private fun tipoFicha(tesis: Tesis): String? = when {
        tesis.esJurisprudencia -> "Jurisprudencia"
        tesis.tipo.isNotBlank() -> "Aislada"
        else -> null
    }

    private val EPOCAS = mapOf(
        "12a" to "Duodécima Época", "11a" to "Undécima Época", "10a" to "Décima Época",
        "9a" to "Novena Época", "8a" to "Octava Época", "7a" to "Séptima Época",
        "6a" to "Sexta Época", "5a" to "Quinta Época"
    )

    /** «10a. Época» → «Décima Época»; las formas completas se dejan igual. */
    fun epocaCompleta(epoca: String): String {
        val abreviada = Regex("^(\\d{1,2}a)\\.?\\s*Época$", RegexOption.IGNORE_CASE).find(epoca.trim())
        return abreviada?.let { EPOCAS[it.groupValues[1].lowercase()] } ?: epoca.trim()
    }

    private val MESES = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
        "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    /** «Libro XXIII, Agosto de 2013» → «Libro XXIII, agosto de 2013». */
    private fun mesesEnMinuscula(s: String): String =
        MESES.fold(s) { acc, mes -> acc.replace(Regex("\\b$mes\\b"), mes.lowercase()) }

    /**
     * Fecha en estilo de cita: «18 de mayo de 2018». La interfaz muestra «del
     * 2018», pero en las citas se sigue el uso de la SCJN («… de 2025»).
     */
    private fun fechaParaCita(fecha: String): String =
        formatDateLong(fecha).replace(Regex("\\bdel (\\d{4})\\b"), "de $1")

    /** «viernes 02 de octubre» → «viernes 2 de octubre». */
    private fun quitarCeroInicial(s: String): String =
        s.replace(Regex("\\b0(\\d) de "), "$1 de ")

    private fun normalizarEspacios(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    private fun rubro(tesis: Tesis): String = tesis.rubro.trim().ifBlank { "(Sin rubro)" }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
