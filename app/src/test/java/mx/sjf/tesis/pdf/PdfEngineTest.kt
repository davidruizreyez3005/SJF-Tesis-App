package mx.sjf.tesis.pdf

import mx.sjf.tesis.data.model.Ejecutoria
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.model.Voto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Motor PDF propio: codificación, métricas, validez del archivo y paginación. */
class PdfEngineTest {

    // ── Codificación y métricas ──

    @Test
    fun winAnsi_cubreElEspanol() {
        val texto = "áéíóú ÁÉÍÓÚ ñÑ üÜ ¿¡ «» “” ‘’ – — … § °"
        val bytes = WinAnsi.codificar(texto)
        assertEquals(texto.length, bytes.size)            // un byte por carácter, sin sustituciones
        assertFalse(bytes.any { it == '?'.code.toByte() })
        assertEquals(0xE1, bytes[0].toInt() and 0xFF)       // á
        assertEquals(0x97, WinAnsi.codificar("—")[0].toInt() and 0xFF)
    }

    @Test
    fun winAnsi_sustitutosYGuionSuave() {
        assertArrayEquals("No. 5".toByteArray(Charsets.ISO_8859_1), WinAnsi.codificar("№ 5"))
        assertArrayEquals("Constitucion".toByteArray(), WinAnsi.codificar("Consti­tucion"))   // guion suave fuera
        assertArrayEquals("a-b".toByteArray(), WinAnsi.codificar("a−b"))
        assertArrayEquals("a".toByteArray(), WinAnsi.codificar("ā"))                               // sin diacrítico
    }

    @Test
    fun metricasTimes() {
        assertEquals(4.44f, PdfFont.ROMAN.ancho("a", 10f), 0.001f)
        assertEquals(5.0f, PdfFont.BOLD.ancho("a", 10f), 0.001f)
        assertEquals(2.5f, PdfFont.ROMAN.anchoEspacio(10f), 0.001f)
        assertEquals(PdfFont.ROMAN.ancho("a", 10f), PdfFont.ROMAN.ancho("á", 10f), 0.001f)
    }

    // ── Validez del archivo ──

    /** Comprueba encabezado, %%EOF y que cada entrada de la tabla xref apunte a su objeto. */
    private fun validar(pdf: ByteArray): Int {
        val s = String(pdf, Charsets.ISO_8859_1)
        assertTrue(s.startsWith("%PDF-1.4"))
        assertTrue(s.trimEnd().endsWith("%%EOF"))
        val inicioXref = Regex("startxref\\n(\\d+)").find(s)!!.groupValues[1].toInt()
        assertTrue(s.startsWith("xref", inicioXref))
        val cabecera = Regex("xref\\n0 (\\d+)\\n").find(s, inicioXref)!!
        val total = cabecera.groupValues[1].toInt()
        var pos = cabecera.range.last + 1 + 20                  // se salta la entrada 0
        for (n in 1 until total) {
            val offset = s.substring(pos, pos + 10).toInt()
            assertTrue("objeto $n en $offset", s.startsWith("$n 0 obj", offset))
            pos += 20
        }
        return Regex("/Type /Page /Parent").findAll(s).count()
    }

    private fun escribir(doc: DocumentoPdf): Pair<ByteArray, Int> {
        val out = ByteArrayOutputStream()
        val paginas = doc.escribir(out)
        return out.toByteArray() to paginas
    }

    // ── Documentos ──

    private val parrafoLargo = "Esta es una oración de prueba con acentos, eñes y palabras largas como constitucionalidad. "

    private val tesis = Tesis(
        registro = 175940, clave = "P./J. 2/2006", tipo = "Jurisprudencia", epoca = "Novena Época", instancia = "Pleno",
        materia = "Constitucional, Penal", fuente = "Semanario Judicial de la Federación y su Gaceta",
        volumen = "Tomo XXIII, Febrero de 2006", pagina = "5",
        rubro = "EXTRADICIÓN. LA PRISIÓN VITALICIA NO CONSTITUYE UNA PENA INUSITADA.",
        texto = "<p>Hechos: ${parrafoLargo.repeat(4)}</p><p>${parrafoLargo.repeat(6)}</p><p>PLENO.</p>",
        precedente = "Solicitud de modificación de jurisprudencia 2/2005-PL. 29 de noviembre de 2005. Mayoría de seis votos.",
        ejecutorias = listOf(19501), votos = listOf(20582)
    )

    private val ejecutoria = Ejecutoria(
        19501, "Solicitud de sustitución de jurisprudencia 2/2005-PL", "RUBRO UNO.\nRUBRO DOS.",
        "Novena Época", "Pleno", "Semanario Judicial de la Federación y su Gaceta", "Tomo XXIII, Mayo de 2006", "", "339", "",
        (1..400).joinToString("") { i -> if (i % 50 == 0) "<p>CONSIDERANDO $i:</p>" else "<p>${parrafoLargo.repeat(3)}</p>" }
    )

    private val voto = Voto(20582, "Voto paralelo del Ministro Sergio Salvador Aguirre Anguiano", "paralelo",
        "<p>Voto paralelo del Ministro Sergio Salvador Aguirre Anguiano.</p><p>${parrafoLargo.repeat(10)}</p>", "")

    @Test
    fun tesis_pdfValidoYCompacto() {
        val (pdf, paginas) = escribir(DocumentosPdf.tesis(tesis, listOf(ejecutoria), listOf(voto)))
        assertEquals(paginas, validar(pdf))
        assertTrue("una tesis cabe en pocas páginas: $paginas", paginas in 1..3)
        assertTrue("sin fuentes incrustadas el archivo es chico: ${pdf.size}", pdf.size < 15_000)
        assertEquals("Tesis_P-J-2-2006_175940.pdf", DocumentosPdf.tesis(tesis).nombreArchivo)
    }

    @Test
    fun ejecutoriaLarga_paginaYSeMantieneLigera() {
        val (pdf, paginas) = escribir(DocumentosPdf.ejecutoria(ejecutoria, tesis))
        assertEquals(paginas, validar(pdf))
        assertTrue("400 párrafos ocupan muchas páginas: $paginas", paginas > 30)
        // Tamaño por página muy por debajo de un PDF con fuentes incrustadas.
        assertTrue("${pdf.size / paginas} bytes por página", pdf.size / paginas < 4_000)
        // Los encabezados en mayúsculas forman el índice lateral (marcadores).
        assertTrue(String(pdf, Charsets.ISO_8859_1).contains("/Type /Outlines"))
    }

    @Test
    fun expediente_indiceConVinculosYMarcadores() {
        val (pdf, paginas) = escribir(DocumentosPdf.expediente(tesis, listOf(ejecutoria), listOf(voto)))
        assertEquals(paginas, validar(pdf))
        val s = String(pdf, Charsets.ISO_8859_1)
        assertEquals("5 renglones del índice con vínculo (tesis, 2 títulos de sección, ejecutoria, voto)", 5, Regex("/Subtype /Link").findAll(s).count())
        assertTrue(s.contains("/PageMode /UseOutlines"))
    }

    @Test
    fun compilacion_unaSeccionPorTesis() {
        val varias = (1..5).map { tesis.copy(registro = 175940L + it) }
        val (pdf, paginas) = escribir(DocumentosPdf.compilacion(varias, "x.pdf"))
        assertEquals(paginas, validar(pdf))
        assertTrue(paginas >= 6)   // índice + al menos una página por tesis
        assertEquals(5, Regex("/Subtype /Link").findAll(String(pdf, Charsets.ISO_8859_1)).count())
    }

    @Test
    fun encabezadosDeSentencia() {
        assertTrue(DocumentosPdf.esEncabezado("CONSIDERANDO:"))
        assertTrue(DocumentosPdf.esEncabezado("R E S U L T A N D O"))
        assertTrue(DocumentosPdf.esEncabezado("PLENO."))
        assertFalse(DocumentosPdf.esEncabezado("México, Distrito Federal."))
        assertFalse(DocumentosPdf.esEncabezado("A".repeat(120)))
    }

    @Test
    fun parrafosLargosSinPalabrasCortadas() {
        // Una «palabra» más ancha que la línea (p. ej. una URL) no rompe la maquetación.
        val url = "https://sjf2.scjn.gob.mx/" + "x".repeat(400)
        val (pdf, paginas) = escribir(DocumentosPdf.voto(voto.copy(texto = "<p>Título.</p><p>$url</p>")))
        assertEquals(paginas, validar(pdf))
    }
}
