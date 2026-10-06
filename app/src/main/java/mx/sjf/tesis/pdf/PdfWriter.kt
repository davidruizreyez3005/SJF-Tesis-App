package mx.sjf.tesis.pdf

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Deflater

/**
 * Escritor PDF 1.4 mínimo y en flujo: cada página se comprime y se escribe al
 * momento, así que la memoria no crece con el tamaño del documento.
 *
 * Usa las fuentes estándar Times (sin incrustar) con WinAnsiEncoding, y admite
 * marcadores (índice lateral del lector), vínculos internos y metadatos.
 *
 * Uso: [reservarPaginas] con el total, luego [escribirPagina] en orden y por
 * último [cerrar] con los marcadores.
 */
class PdfWriter(private val salida: OutputStream) {

    /** Destino dentro del documento: página (base 0) y altura desde abajo. */
    data class Destino(val pagina: Int, val y: Float)

    /** Rectángulo de una página que, al tocarlo, lleva a [destino]. */
    data class Vinculo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val destino: Destino)

    /** Entrada del índice lateral. [hijos] forman el segundo nivel. */
    data class Marcador(val titulo: String, val destino: Destino, val hijos: List<Marcador> = emptyList())

    private var posicion = 0L
    private val desplazamientos = mutableMapOf<Int, Long>()
    private var siguienteObjeto = 1

    private val catalogo = reservar()
    private val arbolPaginas = reservar()
    private val fuentes = PdfFont.entries.associateWith { reservar() }
    private var paginas: IntArray = IntArray(0)
    private var escritas = 0
    private var anchoPagina = 612f
    private var altoPagina = 792f

    init {
        escribir("%PDF-1.4\n")
        // Comentario binario: avisa a los programas de transferencia que el archivo es binario.
        escribirBytes(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
        for ((fuente, numero) in fuentes) {
            objeto(numero, "<< /Type /Font /Subtype /Type1 /BaseFont /${fuente.nombrePs} /Encoding /WinAnsiEncoding >>")
        }
    }

    /** Reserva los números de objeto de todas las páginas (los vínculos pueden apuntar hacia adelante). */
    fun reservarPaginas(total: Int, ancho: Float, alto: Float) {
        require(total > 0)
        paginas = IntArray(total) { reservar() }
        anchoPagina = ancho
        altoPagina = alto
    }

    /** Escribe la siguiente página: [contenido] son operadores PDF sin comprimir. */
    fun escribirPagina(contenido: ByteArray, vinculos: List<Vinculo> = emptyList()) {
        check(escritas < paginas.size) { "Se escribieron más páginas de las reservadas" }
        val comprimido = deflate(contenido)
        val flujo = reservar()
        iniciarObjeto(flujo)
        escribir("<< /Length ${comprimido.size} /Filter /FlateDecode >>\nstream\n")
        escribirBytes(comprimido)
        escribir("\nendstream\nendobj\n")

        val anotaciones = vinculos.map { v ->
            val n = reservar()
            objeto(
                n,
                "<< /Type /Annot /Subtype /Link /Border [0 0 0] /Rect [${num(v.x1)} ${num(v.y1)} ${num(v.x2)} ${num(v.y2)}] " +
                    "/Dest [${paginas[v.destino.pagina]} 0 R /XYZ 0 ${num(v.destino.y)} 0] >>"
            )
            n
        }
        val recursos = fuentes.entries.joinToString(" ") { (f, n) -> "/${f.recurso} $n 0 R" }
        val annots = if (anotaciones.isEmpty()) "" else " /Annots [${anotaciones.joinToString(" ") { "$it 0 R" }}]"
        objeto(
            paginas[escritas],
            "<< /Type /Page /Parent $arbolPaginas 0 R /MediaBox [0 0 ${num(anchoPagina)} ${num(altoPagina)}] " +
                "/Resources << /Font << $recursos >> >> /Contents $flujo 0 R$annots >>"
        )
        escritas++
    }

    /** Escribe árbol de páginas, marcadores, metadatos, catálogo y tabla xref. */
    fun cerrar(titulo: String, asunto: String, marcadores: List<Marcador>) {
        check(escritas == paginas.size) { "Faltan páginas: $escritas de ${paginas.size}" }
        objeto(
            arbolPaginas,
            "<< /Type /Pages /Count ${paginas.size} /Kids [${paginas.joinToString(" ") { "$it 0 R" }}] >>"
        )

        val raizMarcadores = if (marcadores.isEmpty()) null else escribirMarcadores(marcadores)

        val fecha = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        val info = reservar()
        objeto(
            info,
            "<< /Title ${textoPdf(titulo)} /Subject ${textoPdf(asunto)} /Author ${textoPdf("Semanario Judicial de la Federación")} " +
                "/Creator ${textoPdf("SJF Tesis")} /Producer ${textoPdf("SJF Tesis")} /CreationDate (D:${fecha}Z) >>"
        )
        val extra = if (raizMarcadores != null) " /Outlines $raizMarcadores 0 R /PageMode /UseOutlines" else ""
        objeto(catalogo, "<< /Type /Catalog /Pages $arbolPaginas 0 R$extra /Lang (es-MX) >>")

        val inicioXref = posicion
        val total = siguienteObjeto
        val xref = StringBuilder("xref\n0 $total\n0000000000 65535 f \n")
        for (n in 1 until total) {
            val d = desplazamientos[n] ?: error("Objeto $n reservado pero no escrito")
            xref.append(String.format(Locale.US, "%010d 00000 n \n", d))
        }
        escribir(xref.toString())
        escribir("trailer\n<< /Size $total /Root $catalogo 0 R /Info $info 0 R >>\nstartxref\n$inicioXref\n%%EOF\n")
        salida.flush()
    }

    // ── Marcadores (outline) ──

    private fun escribirMarcadores(marcadores: List<Marcador>): Int {
        val raiz = reservar()
        val numeros = escribirNivel(marcadores, raiz)
        val visibles = contar(marcadores)
        objeto(raiz, "<< /Type /Outlines /First ${numeros.first()} 0 R /Last ${numeros.last()} 0 R /Count $visibles >>")
        return raiz
    }

    private fun contar(m: List<Marcador>): Int = m.size + m.sumOf { contar(it.hijos) }

    /** Escribe un nivel de marcadores hermanos; devuelve sus números de objeto. */
    private fun escribirNivel(nivel: List<Marcador>, padre: Int): List<Int> {
        val numeros = nivel.map { reservar() }
        nivel.forEachIndexed { i, m ->
            val hijos = if (m.hijos.isEmpty()) emptyList() else escribirNivel(m.hijos, numeros[i])
            val sb = StringBuilder("<< /Title ${textoPdf(m.titulo)} /Parent $padre 0 R")
            if (i > 0) sb.append(" /Prev ${numeros[i - 1]} 0 R")
            if (i < nivel.size - 1) sb.append(" /Next ${numeros[i + 1]} 0 R")
            if (hijos.isNotEmpty()) {
                // Cuenta negativa: los hijos aparecen contraídos (índices largos de sentencias).
                sb.append(" /First ${hijos.first()} 0 R /Last ${hijos.last()} 0 R /Count -${hijos.size}")
            }
            sb.append(" /Dest [${paginas[m.destino.pagina]} 0 R /XYZ 0 ${num(m.destino.y)} 0] >>")
            objeto(numeros[i], sb.toString())
        }
        return numeros
    }

    // ── Bajo nivel ──

    private fun reservar(): Int = siguienteObjeto++

    private fun iniciarObjeto(n: Int) {
        desplazamientos[n] = posicion
        escribir("$n 0 obj\n")
    }

    private fun objeto(n: Int, cuerpo: String) {
        iniciarObjeto(n)
        escribir(cuerpo)
        escribir("\nendobj\n")
    }

    private fun escribir(s: String) = escribirBytes(s.toByteArray(Charsets.ISO_8859_1))

    private fun escribirBytes(b: ByteArray) {
        salida.write(b)
        posicion += b.size
    }

    private fun deflate(datos: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(datos)
        d.finish()
        val out = java.io.ByteArrayOutputStream(datos.size / 3 + 64)
        val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    companion object {
        /** Número con hasta 2 decimales, sin notación científica. */
        fun num(v: Float): String {
            val r = Math.round(v * 100f) / 100f
            return if (r == r.toLong().toFloat()) r.toLong().toString()
                   else String.format(Locale.US, "%.2f", r).trimEnd('0').trimEnd('.')
        }

        /**
         * Cadena de texto para metadatos y marcadores: UTF-16BE con BOM, en
         * hexadecimal (admite acentos y cualquier carácter).
         */
        fun textoPdf(s: String): String {
            val sb = StringBuilder("<FEFF")
            for (c in s) sb.append(String.format(Locale.US, "%04X", c.code))
            return sb.append('>').toString()
        }
    }
}
