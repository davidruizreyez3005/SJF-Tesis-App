package mx.sjf.tesis.pdf

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.IdentityHashMap

/** Un fragmento de texto con su fuente (p. ej. «Hechos:» en negritas + el resto). */
data class Tramo(val texto: String, val fuente: PdfFont = PdfFont.ROMAN)

enum class Alineacion { JUSTIFICADA, IZQUIERDA, CENTRADA, DERECHA }

/** Bloques de contenido que el [Maquetador] acomoda en páginas. */
sealed class Bloque {
    /**
     * Párrafo con uno o más [tramos]. Los saltos «\n» dentro del texto fuerzan
     * cambio de línea. [conSiguiente] evita que quede solo al pie de página
     * (títulos). [ancla] permite apuntarle desde el índice; [marcador] lo
     * agrega al índice lateral del lector de PDF.
     */
    data class Parrafo(
        val tramos: List<Tramo>,
        val tamano: Float = 11f,
        val interlineado: Float = tamano * 1.38f,
        val alineacion: Alineacion = Alineacion.JUSTIFICADA,
        val antes: Float = 0f,
        val despues: Float = 6f,
        val sangria: Float = 0f,
        val sangriaPrimera: Float = 0f,
        val gris: Float = 0f,
        val conSiguiente: Boolean = false,
        val ancla: String? = null,
        val marcador: Marca? = null
    ) : Bloque()

    /** Fila de ficha: etiqueta a la izquierda (columna fija) y valor que puede ocupar varias líneas. */
    data class Fila(
        val etiqueta: String,
        val valor: String,
        val anchoEtiqueta: Float = 118f,
        val tamano: Float = 10f,
        val despues: Float = 3f
    ) : Bloque()

    /** Línea horizontal. [proporcion] < 1 la centra con ese ancho relativo. */
    data class Regla(
        val grosor: Float = 0.6f,
        val gris: Float = 0f,
        val antes: Float = 4f,
        val despues: Float = 10f,
        val proporcion: Float = 1f
    ) : Bloque()

    data class Espacio(val alto: Float) : Bloque()

    data object SaltoDePagina : Bloque()

    /** Renglón del índice: texto, puntos guía y número de página, con vínculo a [ancla]. */
    data class EntradaIndice(
        val texto: String,
        val ancla: String,
        val nivel: Int = 0,
        val tamano: Float = 10.5f,
        val fuente: PdfFont = PdfFont.ROMAN
    ) : Bloque()
}

/** Entrada del índice lateral: [nivel] 0 = sección; 1 = subsección de la última sección. */
data class Marca(val titulo: String, val nivel: Int = 0)

/** Formato de página y textos fijos de encabezado y pie. */
data class Formato(
    val ancho: Float = 612f,           // carta: 8.5 × 11 in
    val alto: Float = 792f,
    val margenLateral: Float = 72f,
    val margenSuperior: Float = 72f,
    val margenInferior: Float = 68f,
    /** Encabezado corrido (a partir de la página 2): izquierda y derecha. */
    val encabezadoIzq: String = "",
    val encabezadoDer: String = "",
    /** Pie: texto fijo a la izquierda; a la derecha va «Página X de Y». */
    val pie: String = ""
) {
    val anchoUtil get() = ancho - 2 * margenLateral
}

/**
 * Motor de maquetación: corta párrafos en líneas con anchos exactos de las
 * fuentes, justifica con espaciado de palabras (un solo operador por línea),
 * pagina línea por línea evitando líneas huérfanas y viudas, mantiene los
 * títulos con su texto, y escribe el PDF en flujo con [PdfWriter].
 *
 * Todo se calcula antes de dibujar, por eso el índice y «Página X de Y» salen
 * exactos en una sola escritura.
 */
class Maquetador(private val formato: Formato) {

    // ── Resultado de la maquetación ──

    private class Segmento(val fuente: PdfFont, val bytes: ByteArray)

    private class Linea(val segmentos: List<Segmento>, val tamano: Float, val ancho: Float, val espacios: Int, val forzada: Boolean)

    private sealed class Pieza {
        class Texto(val segmentos: List<Segmento>, val tamano: Float, val x: Float, val base: Float, val tw: Float, val gris: Float) : Pieza()
        class Raya(val x1: Float, val x2: Float, val y: Float, val grosor: Float, val gris: Float) : Pieza()
        class Enlace(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val ancla: String) : Pieza()
    }

    private class Pagina { val piezas = mutableListOf<Pieza>() }

    private val cacheLineas = IdentityHashMap<Bloque, List<Linea>>()

    /**
     * Maqueta y escribe el documento completo en [salida].
     * @return número de páginas.
     */
    fun escribir(salida: OutputStream, bloques: List<Bloque>, titulo: String, asunto: String): Int {
        // 1ª pasada: ubicar anclas (el índice usa números provisionales del mismo ancho).
        val provisional = paginar(bloques) { null }
        val anclas = provisional.anclas
        // 2ª pasada: con los números reales. Las líneas ya están en caché, solo se recoloca.
        val final = paginar(bloques) { a -> anclas[a]?.first }
        check(final.paginas.size == provisional.paginas.size)

        val writer = PdfWriter(salida)
        writer.reservarPaginas(final.paginas.size, formato.ancho, formato.alto)
        val total = final.paginas.size
        final.paginas.forEachIndexed { i, pagina ->
            val vinculos = pagina.piezas.filterIsInstance<Pieza.Enlace>().mapNotNull { e ->
                val destino = final.anclas[e.ancla] ?: return@mapNotNull null
                PdfWriter.Vinculo(e.x1, e.y1, e.x2, e.y2, PdfWriter.Destino(destino.first, destino.second))
            }
            writer.escribirPagina(contenido(pagina, i, total), vinculos)
        }
        writer.cerrar(titulo, asunto, final.marcadores)
        return total
    }

    // ── Paginación ──

    private class Resultado(
        val paginas: List<Pagina>,
        val anclas: Map<String, Pair<Int, Float>>,
        val marcadores: List<PdfWriter.Marcador>
    )

    private fun paginar(bloques: List<Bloque>, numeroDe: (String) -> Int?): Resultado {
        val paginas = mutableListOf(Pagina())
        val anclas = HashMap<String, Pair<Int, Float>>()
        val marcadores = mutableListOf<PdfWriter.Marcador>()
        val hijosPendientes = mutableListOf<PdfWriter.Marcador>()
        var actual: PdfWriter.Marcador? = null

        val arriba = formato.alto - formato.margenSuperior
        val abajo = formato.margenInferior
        var y = arriba                       // borde superior de lo siguiente que se coloca
        val izq = formato.margenLateral

        fun pagina() = paginas.last()
        fun nuevaPagina() { paginas += Pagina(); y = arriba }
        fun alInicio() = y == arriba

        fun cerrarMarcador() {
            actual?.let { marcadores += it.copy(hijos = hijosPendientes.toList()) }
            hijosPendientes.clear()
            actual = null
        }

        fun registrar(b: Bloque.Parrafo) {
            val destino = Pair(paginas.size - 1, y + 6f)
            b.ancla?.let { anclas[it] = destino }
            b.marcador?.let { m ->
                val nuevo = PdfWriter.Marcador(m.titulo, PdfWriter.Destino(destino.first, destino.second))
                if (m.nivel == 0) { cerrarMarcador(); actual = nuevo }
                else if (actual != null) hijosPendientes += nuevo
                else marcadores += nuevo
            }
        }

        for ((indice, bloque) in bloques.withIndex()) {
            when (bloque) {
                is Bloque.Parrafo -> {
                    val lineas = lineasDe(bloque)
                    if (lineas.isEmpty()) continue
                    val alto = bloque.interlineado
                    if (!alInicio()) y -= bloque.antes
                    // Títulos: si no cabe el título más dos líneas de lo que sigue, a la siguiente página.
                    if (bloque.conSiguiente && !alInicio()) {
                        val siguiente = bloques.getOrNull(indice + 1)
                        val extra = when (siguiente) {
                            is Bloque.Parrafo -> siguiente.antes + siguiente.interlineado * minOf(2, lineasDe(siguiente).size)
                            is Bloque.Fila -> siguiente.tamano * 1.35f * 2
                            else -> 0f
                        }
                        if (y - lineas.size * alto - bloque.despues - extra < abajo) nuevaPagina()
                    }
                    if (y - alto < abajo) nuevaPagina()
                    registrar(bloque)
                    var i = 0
                    while (i < lineas.size) {
                        var caben = ((y - abajo) / alto).toInt()
                        if (caben <= 0 && alInicio()) caben = 1   // línea más alta que la página: se coloca igual
                        val restantes = lineas.size - i
                        if (caben <= 0 || (caben == 1 && restantes > 1 && !alInicio())) {
                            // Evita una línea huérfana al pie: el párrafo empieza en la siguiente página.
                            nuevaPagina(); continue
                        }
                        if (caben < restantes && restantes - caben == 1 && caben > 1) caben -= 1   // sin viudas
                        val n = minOf(caben, restantes)
                        repeat(n) { k ->
                            val linea = lineas[i + k]
                            colocarLinea(pagina(), bloque, linea, y, esUltima = i + k == lineas.lastIndex)
                            y -= alto
                        }
                        i += n
                        if (i < lineas.size) nuevaPagina()
                    }
                    y -= bloque.despues
                }

                is Bloque.Fila -> {
                    val lineas = lineasFila(bloque)
                    val alto = bloque.tamano * 1.35f
                    val total = lineas.size * alto
                    if (y - total < abajo) nuevaPagina()
                    val etiqueta = WinAnsi.codificar(bloque.etiqueta.uppercase())
                    pagina().piezas += Pieza.Texto(
                        listOf(Segmento(PdfFont.BOLD, etiqueta)), bloque.tamano * 0.82f,
                        izq, y - alto * 0.78f, 0f, 0.35f
                    )
                    lineas.forEach { l ->
                        pagina().piezas += Pieza.Texto(l.segmentos, l.tamano, izq + bloque.anchoEtiqueta, y - alto * 0.78f, 0f, 0f)
                        y -= alto
                    }
                    y -= bloque.despues
                }

                is Bloque.Regla -> {
                    if (!alInicio()) y -= bloque.antes
                    if (y - bloque.grosor < abajo) { nuevaPagina(); continue }
                    val ancho = formato.anchoUtil * bloque.proporcion
                    val x1 = izq + (formato.anchoUtil - ancho) / 2
                    pagina().piezas += Pieza.Raya(x1, x1 + ancho, y, bloque.grosor, bloque.gris)
                    y -= bloque.despues
                }

                is Bloque.Espacio -> if (!alInicio()) { y -= bloque.alto; if (y < abajo) nuevaPagina() }

                Bloque.SaltoDePagina -> if (!alInicio()) nuevaPagina()

                is Bloque.EntradaIndice -> {
                    val alto = bloque.tamano * 1.55f
                    if (y - alto < abajo) nuevaPagina()
                    colocarEntradaIndice(pagina(), bloque, numeroDe(bloque.ancla), y, alto)
                    y -= alto
                }
            }
        }
        cerrarMarcador()
        return Resultado(paginas, anclas, marcadores)
    }

    private fun colocarLinea(pagina: Pagina, b: Bloque.Parrafo, l: Linea, top: Float, esUltima: Boolean) {
        val primera = l === lineasDe(b).first()
        val sangria = b.sangria + if (primera) b.sangriaPrimera else 0f
        val disponible = formato.anchoUtil - sangria
        var x = formato.margenLateral + sangria
        var tw = 0f
        when (b.alineacion) {
            Alineacion.JUSTIFICADA -> if (!esUltima && !l.forzada && l.espacios > 0) {
                val propuesto = (disponible - l.ancho) / l.espacios
                // Con huecos exagerados (pocas palabras en la línea) se deja a la izquierda.
                if (propuesto in 0f..(l.tamano * 0.6f)) tw = propuesto
            }
            Alineacion.CENTRADA -> x += (disponible - l.ancho) / 2
            Alineacion.DERECHA -> x += disponible - l.ancho
            Alineacion.IZQUIERDA -> {}
        }
        val base = top - (b.interlineado - b.tamano) / 2 - b.tamano * 0.8f
        pagina.piezas += Pieza.Texto(l.segmentos, l.tamano, x, base, tw, b.gris)
    }

    private fun colocarEntradaIndice(pagina: Pagina, e: Bloque.EntradaIndice, numero: Int?, top: Float, alto: Float) {
        val izq = formato.margenLateral + e.nivel * 16f
        val der = formato.ancho - formato.margenLateral
        val base = top - alto * 0.72f
        val numeroTexto = WinAnsi.codificar(numero?.plus(1)?.toString() ?: "000")
        val anchoNumero = PdfFont.ROMAN.ancho(numeroTexto, e.tamano)
        val maxTexto = der - izq - anchoNumero - 24f
        var texto = WinAnsi.codificar(e.texto)
        if (e.fuente.ancho(texto, e.tamano) > maxTexto) {
            // Recorta con «…» para que el número de página siempre quede visible.
            var s = e.texto
            while (s.isNotEmpty() && e.fuente.ancho(WinAnsi.codificar("$s…"), e.tamano) > maxTexto) s = s.dropLast(1)
            texto = WinAnsi.codificar(s.trimEnd() + "…")
        }
        val anchoTexto = e.fuente.ancho(texto, e.tamano)
        pagina.piezas += Pieza.Texto(listOf(Segmento(e.fuente, texto)), e.tamano, izq, base, 0f, 0f)
        // Puntos guía entre el texto y el número.
        val punto = PdfFont.ROMAN.ancho(byteArrayOf('.'.code.toByte(), ' '.code.toByte()), e.tamano)
        val inicioPuntos = izq + anchoTexto + 6f
        val finPuntos = der - anchoNumero - 6f
        val cuantos = ((finPuntos - inicioPuntos) / punto).toInt()
        if (cuantos > 0) {
            val puntos = WinAnsi.codificar(". ".repeat(cuantos).trimEnd())
            pagina.piezas += Pieza.Texto(listOf(Segmento(PdfFont.ROMAN, puntos)), e.tamano, finPuntos - PdfFont.ROMAN.ancho(puntos, e.tamano), base, 0f, 0.55f)
        }
        pagina.piezas += Pieza.Texto(listOf(Segmento(PdfFont.ROMAN, numeroTexto)), e.tamano, der - anchoNumero, base, 0f, 0f)
        pagina.piezas += Pieza.Enlace(izq, top - alto, der, top, e.ancla)
    }

    // ── Corte de líneas ──

    private class Palabra(val fuente: PdfFont, val bytes: ByteArray, val ancho: Float, val saltoAntes: Boolean)

    private fun lineasDe(b: Bloque.Parrafo): List<Linea> = cacheLineas.getOrPut(b) {
        cortar(b.tramos, b.tamano, formato.anchoUtil - b.sangria, b.sangriaPrimera)
    }

    private fun lineasFila(b: Bloque.Fila): List<Linea> = cacheLineas.getOrPut(b) {
        cortar(listOf(Tramo(b.valor)), b.tamano, formato.anchoUtil - b.anchoEtiqueta, 0f)
    }

    /**
     * Corte voraz de líneas con anchos exactos. Las palabras más anchas que la
     * línea (URLs, cadenas largas) se parten por caracteres.
     */
    private fun cortar(tramos: List<Tramo>, tamano: Float, ancho: Float, sangriaPrimera: Float): List<Linea> {
        val palabras = mutableListOf<Palabra>()
        var saltoPendiente = false
        for (t in tramos) {
            val renglones = t.texto.split('\n')
            renglones.forEachIndexed { r, renglon ->
                if (r > 0) saltoPendiente = true
                for (p in renglon.split(' ')) {
                    if (p.isEmpty()) continue
                    val bytes = WinAnsi.codificar(p)
                    if (bytes.isEmpty()) continue
                    palabras += Palabra(t.fuente, bytes, t.fuente.ancho(bytes, tamano), saltoPendiente)
                    saltoPendiente = false
                }
            }
        }
        val lineas = mutableListOf<Linea>()
        var actual = mutableListOf<Palabra>()
        var anchoActual = 0f

        fun disponible() = if (lineas.isEmpty()) ancho - sangriaPrimera else ancho
        fun cerrar(forzada: Boolean) {
            if (actual.isEmpty()) return
            lineas += armar(actual, tamano, forzada)
            actual = mutableListOf(); anchoActual = 0f
        }

        for (p in palabras) {
            if (p.saltoAntes) cerrar(forzada = true)
            val espacio = if (actual.isEmpty()) 0f else actual.last().fuente.anchoEspacio(tamano)
            if (actual.isNotEmpty() && anchoActual + espacio + p.ancho > disponible()) cerrar(forzada = false)
            if (actual.isEmpty() && p.ancho > disponible()) {
                // Palabra más ancha que la línea: se parte en trozos que quepan.
                var resto = p.bytes
                while (resto.isNotEmpty()) {
                    var n = resto.size
                    while (n > 1 && p.fuente.ancho(resto.copyOf(n), tamano) > disponible()) n--
                    val trozo = resto.copyOf(n)
                    actual += Palabra(p.fuente, trozo, p.fuente.ancho(trozo, tamano), false)
                    resto = resto.copyOfRange(n, resto.size)
                    if (resto.isNotEmpty()) cerrar(forzada = true)
                    else anchoActual = actual.sumOf { it.ancho.toDouble() }.toFloat()
                }
                continue
            }
            anchoActual += (if (actual.isEmpty()) 0f else espacio) + p.ancho
            actual += p
        }
        cerrar(forzada = true)
        return lineas
    }

    /** Une palabras consecutivas de la misma fuente en un solo segmento (un Tj por fuente). */
    private fun armar(palabras: List<Palabra>, tamano: Float, forzada: Boolean): Linea {
        val segmentos = mutableListOf<Segmento>()
        val buf = ByteArrayOutputStream()
        var fuente = palabras.first().fuente
        var ancho = 0f
        var espacios = 0
        palabras.forEachIndexed { i, p ->
            if (i > 0) {
                ancho += palabras[i - 1].fuente.anchoEspacio(tamano)
                espacios++
                if (p.fuente != fuente) {
                    buf.write(' '.code)   // el espacio queda con la fuente anterior
                    segmentos += Segmento(fuente, buf.toByteArray()); buf.reset(); fuente = p.fuente
                } else buf.write(' '.code)
            }
            buf.write(p.bytes)
            ancho += p.ancho
        }
        segmentos += Segmento(fuente, buf.toByteArray())
        return Linea(segmentos, tamano, ancho, espacios, forzada)
    }

    // ── Dibujo (operadores PDF) ──

    private fun contenido(pagina: Pagina, indice: Int, total: Int): ByteArray {
        val out = ByteArrayOutputStream(4096)
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

        for (p in pagina.piezas) when (p) {
            is Pieza.Texto -> texto(out, p.segmentos, p.tamano, p.x, p.base, p.tw, p.gris)
            is Pieza.Raya -> w("${n(p.grosor)} w ${n(p.gris)} G ${n(p.x1)} ${n(p.y)} m ${n(p.x2)} ${n(p.y)} l S\n")
            is Pieza.Enlace -> {}
        }

        // Encabezado corrido (desde la 2.ª página) y pie con número de página.
        val izq = formato.margenLateral
        val der = formato.ancho - formato.margenLateral
        val gris = 0.4f
        if (indice > 0 && (formato.encabezadoIzq.isNotEmpty() || formato.encabezadoDer.isNotEmpty())) {
            val yEnc = formato.alto - formato.margenSuperior + 26f
            textoSimple(out, formato.encabezadoIzq, PdfFont.ITALIC, 8.5f, izq, yEnc, gris, Alineacion.IZQUIERDA)
            textoSimple(out, formato.encabezadoDer, PdfFont.ROMAN, 8.5f, der, yEnc, gris, Alineacion.DERECHA)
            w("0.4 w 0.7 G ${n(izq)} ${n(yEnc - 6f)} m ${n(der)} ${n(yEnc - 6f)} l S\n")
        }
        val yPie = formato.margenInferior - 30f
        w("0.4 w 0.7 G ${n(izq)} ${n(yPie + 12f)} m ${n(der)} ${n(yPie + 12f)} l S\n")
        if (formato.pie.isNotEmpty()) textoSimple(out, formato.pie, PdfFont.ROMAN, 7.5f, izq, yPie, gris, Alineacion.IZQUIERDA)
        textoSimple(out, "Página ${indice + 1} de $total", PdfFont.ROMAN, 8.5f, der, yPie, gris, Alineacion.DERECHA)
        return out.toByteArray()
    }

    private fun textoSimple(out: ByteArrayOutputStream, s: String, f: PdfFont, tamano: Float, x: Float, base: Float, gris: Float, al: Alineacion) {
        if (s.isEmpty()) return
        val bytes = WinAnsi.codificar(s)
        val xx = if (al == Alineacion.DERECHA) x - f.ancho(bytes, tamano) else x
        texto(out, listOf(Segmento(f, bytes)), tamano, xx, base, 0f, gris)
    }

    private fun texto(out: ByteArrayOutputStream, segmentos: List<Segmento>, tamano: Float, x: Float, base: Float, tw: Float, gris: Float) {
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        w("BT ${n(gris)} g ${n(tw)} Tw 1 0 0 1 ${n(x)} ${n(base)} Tm")
        for (s in segmentos) {
            w(" /${s.fuente.recurso} ${n(tamano)} Tf (")
            for (b in s.bytes) {
                when (b.toInt() and 0xFF) {
                    '('.code, ')'.code, '\\'.code -> { out.write('\\'.code); out.write(b.toInt()) }
                    else -> out.write(b.toInt())
                }
            }
            w(") Tj")
        }
        w(" ET\n")
    }

    private fun n(v: Float) = PdfWriter.num(v)
}
