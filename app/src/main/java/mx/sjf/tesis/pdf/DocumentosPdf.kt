package mx.sjf.tesis.pdf

import mx.sjf.tesis.data.model.Ejecutoria
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.model.Voto
import mx.sjf.tesis.data.util.CitationBuilder
import mx.sjf.tesis.data.util.dividirPrecedentes
import mx.sjf.tesis.data.util.formatDateLong
import mx.sjf.tesis.data.util.textoPlano
import java.io.OutputStream

/** Un documento listo para escribir: bloques, formato, metadatos y nombre de archivo. */
class DocumentoPdf(
    val titulo: String,
    val asunto: String,
    val nombreArchivo: String,
    private val formato: Formato,
    private val bloques: List<Bloque>
) {
    /** Escribe el PDF en [salida] (en flujo). Devuelve el número de páginas. */
    fun escribir(salida: OutputStream): Int = Maquetador(formato).escribir(salida, bloques, titulo, asunto)
}

/**
 * Documentos PDF de la app: tesis, ejecutoria, voto, expediente (tesis con sus
 * precedentes y votos) y compilación de varias tesis.
 *
 * Formato tipo documento jurídico: Times, texto justificado, ficha de
 * localización como la del Semanario, encabezado corrido y «Página X de Y».
 */
object DocumentosPdf {

    private const val PIE = "SJF Tesis · Generado a partir de sjf2.scjn.gob.mx · No es una publicación oficial de la SCJN"
    private const val SJF = "Semanario Judicial de la Federación"

    // ── Documentos ──

    fun tesis(t: Tesis, ejecutorias: List<Ejecutoria> = emptyList(), votos: List<Voto> = emptyList()): DocumentoPdf {
        val bloques = mutableListOf<Bloque>()
        bloques += cabecera("Tesis", t.registro)
        bloques += cuerpoTesis(t, ejecutorias, votos, ancla = null, conMarcadores = true)
        return DocumentoPdf(
            titulo = tituloTesis(t),
            asunto = t.rubro,
            nombreArchivo = "Tesis_${parteNombre(t)}.pdf",
            formato = formato("${designacion(t).replaceFirstChar { it.uppercase() }} · Registro digital ${t.registro}"),
            bloques = bloques
        )
    }

    fun ejecutoria(e: Ejecutoria, tesis: Tesis? = null): DocumentoPdf {
        val bloques = mutableListOf<Bloque>()
        bloques += cabecera("Ejecutoria", e.registro)
        bloques += cuerpoEjecutoria(e, tesis, ancla = null, marcadorRaiz = false)
        return DocumentoPdf(
            titulo = "Ejecutoria ${e.registro}" + if (e.asunto.isNotBlank()) " · ${e.asunto}" else "",
            asunto = e.rubro,
            nombreArchivo = "Ejecutoria_${e.registro}.pdf",
            formato = formato("Ejecutoria · Registro digital ${e.registro}"),
            bloques = bloques
        )
    }

    fun voto(v: Voto, tesis: Tesis? = null): DocumentoPdf {
        val bloques = mutableListOf<Bloque>()
        bloques += cabecera("Voto", v.registro)
        bloques += cuerpoVoto(v, tesis, ancla = null, marcador = false)
        return DocumentoPdf(
            titulo = v.titulo,
            asunto = tesis?.rubro ?: "",
            nombreArchivo = "Voto_${v.registro}.pdf",
            formato = formato("Voto · Registro digital ${v.registro}"),
            bloques = bloques
        )
    }

    /**
     * Expediente: la tesis con el texto completo de sus precedentes
     * (ejecutorias) y votos, con índice vinculado y marcadores.
     */
    fun expediente(t: Tesis, ejecutorias: List<Ejecutoria>, votos: List<Voto>): DocumentoPdf {
        val bloques = mutableListOf<Bloque>()
        bloques += parrafo("SEMANARIO JUDICIAL DE LA FEDERACIÓN", PdfFont.BOLD, 9f, Alineacion.CENTRADA, gris = 0.35f, despues = 2f)
        bloques += parrafo("Expediente de la tesis", PdfFont.ITALIC, 10f, Alineacion.CENTRADA, gris = 0.35f, despues = 14f)
        bloques += parrafo(designacion(t).replaceFirstChar { it.uppercase() }, PdfFont.BOLD, 16f, Alineacion.CENTRADA, despues = 6f)
        bloques += parrafo("Registro digital ${t.registro}", PdfFont.ROMAN, 10.5f, Alineacion.CENTRADA, gris = 0.35f, despues = 10f)
        bloques += parrafo(t.rubro, PdfFont.BOLD, 11f, Alineacion.CENTRADA, despues = 4f)
        bloques += Bloque.Regla(grosor = 1f, antes = 12f, despues = 14f)
        bloques += parrafo("CONTENIDO", PdfFont.BOLD, 9.5f, Alineacion.IZQUIERDA, gris = 0.35f, despues = 8f)
        bloques += Bloque.EntradaIndice("Tesis ${t.clave.ifBlank { t.registro.toString() }}", "tesis", fuente = PdfFont.BOLD)
        if (ejecutorias.isNotEmpty()) {
            bloques += Bloque.EntradaIndice(if (ejecutorias.size == 1) "Precedente de la tesis" else "Precedentes de la tesis", "ejecutoria-${ejecutorias.first().registro}", fuente = PdfFont.BOLD)
            ejecutorias.forEach { e ->
                bloques += Bloque.EntradaIndice("Ejecutoria ${e.registro}" + if (e.asunto.isNotBlank()) " · ${e.asunto}" else "", "ejecutoria-${e.registro}", nivel = 1)
            }
        }
        if (votos.isNotEmpty()) {
            bloques += Bloque.EntradaIndice("Votos", "voto-${votos.first().registro}", fuente = PdfFont.BOLD)
            votos.forEach { v -> bloques += Bloque.EntradaIndice(v.titulo, "voto-${v.registro}", nivel = 1) }
        }

        bloques += Bloque.SaltoDePagina
        bloques += cuerpoTesis(t, ejecutorias, votos, ancla = "tesis", conMarcadores = true, marcaRaiz = "Tesis ${t.clave.ifBlank { t.registro.toString() }}")
        ejecutorias.forEach { e ->
            bloques += Bloque.SaltoDePagina
            bloques += cuerpoEjecutoria(e, t, ancla = "ejecutoria-${e.registro}", marcadorRaiz = true)
        }
        votos.forEach { v ->
            bloques += Bloque.SaltoDePagina
            bloques += cuerpoVoto(v, t, ancla = "voto-${v.registro}", marcador = true)
        }
        return DocumentoPdf(
            titulo = "Expediente · ${tituloTesis(t)}",
            asunto = t.rubro,
            nombreArchivo = "Expediente_${parteNombre(t)}.pdf",
            formato = formato("Expediente · ${designacion(t).replaceFirstChar { it.uppercase() }}"),
            bloques = bloques
        )
    }

    /** Varias tesis en un solo archivo, con índice vinculado y marcadores. */
    fun compilacion(tesis: List<Tesis>, nombreArchivo: String): DocumentoPdf {
        val bloques = mutableListOf<Bloque>()
        bloques += parrafo("SEMANARIO JUDICIAL DE LA FEDERACIÓN", PdfFont.BOLD, 9f, Alineacion.CENTRADA, gris = 0.35f, despues = 2f)
        bloques += parrafo("Compilación de tesis", PdfFont.BOLD, 16f, Alineacion.CENTRADA, antes = 6f, despues = 4f)
        bloques += parrafo(if (tesis.size == 1) "1 tesis" else "${tesis.size} tesis", PdfFont.ROMAN, 10.5f, Alineacion.CENTRADA, gris = 0.35f, despues = 4f)
        bloques += Bloque.Regla(grosor = 1f, antes = 12f, despues = 14f)
        bloques += parrafo("CONTENIDO", PdfFont.BOLD, 9.5f, Alineacion.IZQUIERDA, gris = 0.35f, despues = 8f)
        tesis.forEachIndexed { i, t ->
            val etiqueta = "${i + 1}. " + (t.clave.ifBlank { "Registro ${t.registro}" }) + " — " + t.rubro
            bloques += Bloque.EntradaIndice(etiqueta, "tesis-${t.registro}")
        }
        tesis.forEach { t ->
            bloques += Bloque.SaltoDePagina
            bloques += cabecera("Tesis", t.registro)
            bloques += cuerpoTesis(t, emptyList(), emptyList(), ancla = "tesis-${t.registro}", conMarcadores = false,
                marcaRaiz = t.clave.ifBlank { "Registro ${t.registro}" })
        }
        return DocumentoPdf(
            titulo = "Compilación de tesis (${tesis.size})",
            asunto = SJF,
            nombreArchivo = nombreArchivo,
            formato = formato("Compilación de tesis"),
            bloques = bloques
        )
    }

    // ── Cuerpos ──

    private fun cuerpoTesis(
        t: Tesis,
        ejecutorias: List<Ejecutoria>,
        votos: List<Voto>,
        ancla: String?,
        conMarcadores: Boolean,
        marcaRaiz: String? = null
    ): List<Bloque> {
        val b = mutableListOf<Bloque>()
        val publicada = CitationBuilder.fechaDePublicacion(t.publicacion)
        val obligatoria = CitationBuilder.obligatoriaDesde(t.publicacion)

        // Tipo y clave, como en el encabezado del Semanario.
        b += Bloque.Parrafo(
            listOf(Tramo(designacion(t).replaceFirstChar { it.uppercase() }, PdfFont.BOLD)),
            tamano = 12f, alineacion = Alineacion.IZQUIERDA, despues = 8f,
            ancla = ancla, marcador = marcaRaiz?.let { Marca(it) }
        )
        // Ficha de localización.
        b += Bloque.Fila("Registro digital", t.registro.toString())
        if (t.clave.isNotBlank()) b += Bloque.Fila("Tesis", t.clave)
        if (t.epoca.isNotBlank()) b += Bloque.Fila("Época", CitationBuilder.epocaCompleta(t.epoca))
        if (t.instancia.isNotBlank()) b += Bloque.Fila("Instancia", t.instancia)
        if (t.tipo.isNotBlank()) b += Bloque.Fila("Tipo", t.tipo)
        if (t.materia.isNotBlank()) b += Bloque.Fila("Materia(s)", t.materia)
        b += Bloque.Fila("Fuente", t.fuente.ifBlank { SJF })
        listOf(t.volumen, t.tomo).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }
            ?.let { b += Bloque.Fila("Libro / Tomo", it) }
        if (t.pagina.isNotBlank()) b += Bloque.Fila("Página", t.pagina)
        if (publicada != null) b += Bloque.Fila("Publicación", publicada)
        else if (t.fecha.isNotBlank()) b += Bloque.Fila("Fecha", formatDateLong(t.fecha).replace(Regex("\\bdel (\\d{4})\\b"), "de $1"))
        if (obligatoria != null) b += Bloque.Fila("Obligatoria desde", obligatoria)
        b += Bloque.Regla(grosor = 0.5f, gris = 0.6f, antes = 8f, despues = 14f)

        // Rubro.
        b += Bloque.Parrafo(listOf(Tramo(t.rubro.ifBlank { "Tesis sin rubro" }, PdfFont.BOLD)), tamano = 11.5f, despues = 12f)

        // Texto con las etiquetas «Hechos:», «Criterio jurídico:», «Justificación:» en negritas.
        if (t.texto.isBlank()) {
            b += parrafo("El texto completo de esta tesis no está disponible en el servicio de consulta de la SCJN.", PdfFont.ITALIC, 10.5f, gris = 0.35f)
        } else {
            for (p in parrafos(t.texto)) {
                val etiqueta = ETIQUETAS.firstOrNull { p.startsWith(it, ignoreCase = true) }
                b += when {
                    etiqueta != null -> Bloque.Parrafo(
                        listOf(Tramo(p.take(etiqueta.length), PdfFont.BOLD), Tramo(p.drop(etiqueta.length))),
                        tamano = 11f, despues = 7f
                    )
                    esEncabezado(p) -> Bloque.Parrafo(listOf(Tramo(p, PdfFont.BOLD)), tamano = 11f, alineacion = Alineacion.CENTRADA, antes = 4f, despues = 8f)
                    else -> Bloque.Parrafo(listOf(Tramo(p)), tamano = 11f, despues = 7f)
                }
            }
        }

        // Precedentes de la tesis (ejecutorias).
        val registrosEjec = ejecutorias.map { it.registro }.ifEmpty { t.ejecutorias }
        if (registrosEjec.isNotEmpty()) {
            b += seccion(if (registrosEjec.size == 1) "Precedente de la tesis" else "Precedentes de la tesis", if (conMarcadores) 1 else null)
            if (ejecutorias.isNotEmpty()) {
                ejecutorias.forEachIndexed { i, e ->
                    b += Bloque.Parrafo(
                        listOf(Tramo("${i + 1}. Registro digital ${e.registro}. ", PdfFont.BOLD), Tramo(e.localizacion)),
                        tamano = 10.5f, alineacion = Alineacion.IZQUIERDA, despues = if (e.asunto.isNotBlank()) 1f else 6f
                    )
                    if (e.asunto.isNotBlank()) b += Bloque.Parrafo(listOf(Tramo(e.asunto, PdfFont.ITALIC)), tamano = 10f, sangria = 14f, gris = 0.3f, alineacion = Alineacion.IZQUIERDA, despues = 6f)
                }
            } else {
                registrosEjec.forEachIndexed { i, r -> b += parrafo("${i + 1}. Registro digital $r", PdfFont.ROMAN, 10.5f, Alineacion.IZQUIERDA, despues = 4f) }
            }
        }

        // Asuntos que integran la tesis.
        if (t.precedente.isNotBlank()) {
            val d = dividirPrecedentes(t.precedente)
            if (d.asuntos.isNotEmpty()) {
                b += seccion(if (d.asuntos.size == 1) "Asunto que integra la tesis" else "Asuntos que integran la tesis", if (conMarcadores) 1 else null)
                d.asuntos.forEachIndexed { i, a ->
                    val numero = if (d.asuntos.size > 1) "${i + 1}. " else ""
                    b += Bloque.Parrafo(listOf(Tramo("$numero${a.asunto}. ", PdfFont.BOLD), Tramo(a.detalle)), tamano = 10.5f, despues = 6f)
                }
            } else {
                b += seccion("Precedentes", if (conMarcadores) 1 else null)
            }
            d.notas.forEach { n -> b += Bloque.Parrafo(listOf(Tramo(n)), tamano = 9.5f, gris = 0.25f, despues = 5f) }
        }

        // Votos.
        val registrosVoto = votos.map { it.registro }.ifEmpty { t.votos }
        if (registrosVoto.isNotEmpty()) {
            b += seccion("Votos", if (conMarcadores) 1 else null)
            if (votos.isNotEmpty()) votos.forEach { v ->
                b += Bloque.Parrafo(listOf(Tramo(v.titulo + ". "), Tramo("Registro digital ${v.registro}.", PdfFont.ITALIC)), tamano = 10.5f, alineacion = Alineacion.IZQUIERDA, despues = 5f)
            } else registrosVoto.forEach { r -> b += parrafo("Registro digital $r", PdfFont.ROMAN, 10.5f, Alineacion.IZQUIERDA, despues = 4f) }
        }

        // Nota oficial de publicación y forma de citar.
        if (t.publicacion.isNotBlank()) {
            b += Bloque.Espacio(6f)
            b += Bloque.Parrafo(listOf(Tramo(t.publicacion, PdfFont.ITALIC)), tamano = 9.5f, gris = 0.25f, despues = 6f)
        }
        b += seccion("Forma de citar", null)
        b += Bloque.Parrafo(listOf(Tramo(CitationBuilder.referencia(t))), tamano = 10.5f, despues = 4f)
        b += Bloque.Parrafo(listOf(Tramo(t.urlDetalle, PdfFont.ITALIC)), tamano = 9f, gris = 0.35f, alineacion = Alineacion.IZQUIERDA)
        return b
    }

    private fun cuerpoEjecutoria(e: Ejecutoria, tesis: Tesis?, ancla: String?, marcadorRaiz: Boolean): List<Bloque> {
        val b = mutableListOf<Bloque>()
        b += Bloque.Parrafo(
            listOf(Tramo(e.asunto.ifBlank { "Ejecutoria" }, PdfFont.BOLD)),
            tamano = 13f, alineacion = Alineacion.IZQUIERDA, despues = 8f,
            ancla = ancla, marcador = if (marcadorRaiz) Marca("Ejecutoria ${e.registro}" + if (e.asunto.isNotBlank()) " · ${e.asunto}" else "") else null
        )
        b += Bloque.Fila("Registro digital", e.registro.toString())
        if (e.epoca.isNotBlank()) b += Bloque.Fila("Época", e.epoca)
        if (e.instancia.isNotBlank()) b += Bloque.Fila("Instancia", e.instancia)
        b += Bloque.Fila("Fuente", e.fuente.ifBlank { SJF })
        listOf(e.volumen, e.tomo).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }
            ?.let { b += Bloque.Fila("Libro / Tomo", it) }
        if (e.pagina.isNotBlank()) b += Bloque.Fila("Página", e.pagina)
        CitationBuilder.fechaDePublicacion(e.publicacion)?.let { b += Bloque.Fila("Publicación", it) }
        if (tesis != null) b += Bloque.Fila("Tesis relacionada", listOf(tesis.clave, "registro digital ${tesis.registro}").filter { it.isNotBlank() }.joinToString(", "))
        b += Bloque.Regla(grosor = 0.5f, gris = 0.6f, antes = 8f, despues = 12f)
        // Una ejecutoria puede originar varias tesis: un rubro por párrafo.
        val rubros = e.rubro.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        rubros.forEachIndexed { i, r ->
            b += Bloque.Parrafo(listOf(Tramo(r, PdfFont.BOLD_ITALIC)), tamano = 10.5f, despues = if (i == rubros.lastIndex) 12f else 6f)
        }
        b += cuerpoLargo(e.texto, marcadores = marcadorRaiz || ancla == null)
        if (e.publicacion.isNotBlank()) {
            b += Bloque.Espacio(6f)
            b += Bloque.Parrafo(listOf(Tramo(e.publicacion, PdfFont.ITALIC)), tamano = 9.5f, gris = 0.25f)
        }
        b += Bloque.Parrafo(listOf(Tramo(e.urlDetalle, PdfFont.ITALIC)), tamano = 9f, gris = 0.35f, alineacion = Alineacion.IZQUIERDA, antes = 8f)
        return b
    }

    private fun cuerpoVoto(v: Voto, tesis: Tesis?, ancla: String?, marcador: Boolean): List<Bloque> {
        val b = mutableListOf<Bloque>()
        val tipo = if (v.tipo.isNotBlank()) "Voto ${v.tipo}" else "Voto"
        b += Bloque.Parrafo(
            listOf(Tramo(tipo.replaceFirstChar { it.uppercase() }, PdfFont.BOLD)),
            tamano = 13f, alineacion = Alineacion.IZQUIERDA, despues = 8f,
            ancla = ancla, marcador = if (marcador) Marca(v.titulo) else null
        )
        b += Bloque.Fila("Registro digital", v.registro.toString())
        CitationBuilder.fechaDePublicacion(v.publicacion)?.let { b += Bloque.Fila("Publicación", it) }
        if (tesis != null) b += Bloque.Fila("Tesis relacionada", listOf(tesis.clave, "registro digital ${tesis.registro}").filter { it.isNotBlank() }.joinToString(", "))
        b += Bloque.Regla(grosor = 0.5f, gris = 0.6f, antes = 8f, despues = 12f)
        val cuerpo = parrafos(v.texto)
        // El primer párrafo del texto oficial es el título del voto: va como subtítulo.
        if (cuerpo.isNotEmpty()) b += Bloque.Parrafo(listOf(Tramo(cuerpo.first(), PdfFont.BOLD_ITALIC)), tamano = 11f, despues = 10f)
        cuerpo.drop(1).forEach { p -> b += parrafoLargo(p, marcadores = false) }
        b += Bloque.Parrafo(listOf(Tramo(v.urlDetalle, PdfFont.ITALIC)), tamano = 9f, gris = 0.35f, alineacion = Alineacion.IZQUIERDA, antes = 8f)
        return b
    }

    /** Texto largo (sentencias): los encabezados en mayúsculas pasan al índice lateral. */
    private fun cuerpoLargo(html: String, marcadores: Boolean): List<Bloque> {
        var cuantos = 0
        return parrafos(html).map { p ->
            val marcar = marcadores && esEncabezado(p) && cuantos++ < 80
            parrafoLargo(p, marcadores = marcar)
        }
    }

    private fun parrafoLargo(p: String, marcadores: Boolean): Bloque.Parrafo =
        if (esEncabezado(p)) Bloque.Parrafo(
            listOf(Tramo(p, PdfFont.BOLD)), tamano = 11f, alineacion = Alineacion.CENTRADA,
            antes = 6f, despues = 8f, conSiguiente = true,
            marcador = if (marcadores) Marca(p.trimEnd('.', ':').take(80), nivel = 1) else null
        )
        else Bloque.Parrafo(listOf(Tramo(p)), tamano = 11f, despues = 7f)

    // ── Piezas ──

    private val ETIQUETAS = listOf("Hechos:", "Criterio jurídico:", "Justificación:")

    private fun cabecera(tipo: String, registro: Long): List<Bloque> = listOf(
        parrafo("SEMANARIO JUDICIAL DE LA FEDERACIÓN", PdfFont.BOLD, 9f, Alineacion.CENTRADA, gris = 0.35f, despues = 2f),
        parrafo("$tipo · Registro digital $registro", PdfFont.ROMAN, 9f, Alineacion.CENTRADA, gris = 0.35f, despues = 6f),
        Bloque.Regla(grosor = 1f, antes = 2f, despues = 14f)
    )

    private fun seccion(titulo: String, nivelMarcador: Int?): Bloque.Parrafo = Bloque.Parrafo(
        listOf(Tramo(titulo.uppercase(), PdfFont.BOLD)), tamano = 9.5f, gris = 0.3f,
        alineacion = Alineacion.IZQUIERDA, antes = 12f, despues = 6f, conSiguiente = true,
        marcador = nivelMarcador?.let { Marca(titulo, it) }
    )

    private fun parrafo(
        texto: String, fuente: PdfFont = PdfFont.ROMAN, tamano: Float = 11f,
        alineacion: Alineacion = Alineacion.JUSTIFICADA, gris: Float = 0f, antes: Float = 0f, despues: Float = 6f
    ) = Bloque.Parrafo(listOf(Tramo(texto, fuente)), tamano = tamano, alineacion = alineacion, gris = gris, antes = antes, despues = despues)

    private fun formato(encabezado: String) = Formato(encabezadoIzq = SJF, encabezadoDer = encabezado, pie = PIE)

    /** Párrafos de un texto HTML o plano de la API. */
    internal fun parrafos(texto: String): List<String> =
        textoPlano(texto).split(Regex("\\n\\s*\\n")).map { it.replace(Regex("\\s*\\n\\s*"), "\n").trim() }.filter { it.isNotEmpty() }

    /**
     * Encabezado de una sentencia o tesis: renglón corto en mayúsculas
     * («CONSIDERANDO:», «R E S U L T A N D O», «PLENO.»).
     */
    internal fun esEncabezado(p: String): Boolean {
        if (p.length > 90 || p.contains('\n')) return false
        val letras = p.filter { it.isLetter() }
        return letras.length >= 3 && letras.all { it.isUpperCase() } && !p.trimEnd().endsWith(",")
    }

    private fun designacion(t: Tesis) = CitationBuilder.designacion(t)

    private fun tituloTesis(t: Tesis) =
        designacion(t).replaceFirstChar { it.uppercase() } + " · Registro digital ${t.registro}"

    /** «P./J. 191/2026 (12a.)» + 2032690 → «PJ-191-2026-12a_2032690». */
    internal fun parteNombre(t: Tesis): String {
        val clave = t.clave
            .replace(Regex("[/ ]+"), "-")
            .replace(Regex("[^A-Za-z0-9ÁÉÍÓÚáéíóúÑñ-]"), "")
            .replace(Regex("-+"), "-")
            .trim('-')
        return if (clave.isBlank()) t.registro.toString() else "${clave}_${t.registro}"
    }
}
