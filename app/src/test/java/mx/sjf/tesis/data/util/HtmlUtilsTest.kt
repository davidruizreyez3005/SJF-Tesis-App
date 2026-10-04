package mx.sjf.tesis.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de limpieza de HTML y decodificación de entidades. */
class HtmlUtilsTest {

    @Test
    fun decodificarEntidades_entidadesConNombre() {
        assertEquals("áéíóúñ", decodificarEntidades("&aacute;&eacute;&iacute;&oacute;&uacute;&ntilde;"))
        assertEquals("«cita»", decodificarEntidades("&laquo;cita&raquo;"))
        assertEquals("a & b", decodificarEntidades("a &amp; b"))
    }

    @Test
    fun decodificarEntidades_numericasDecimalesYHex() {
        assertEquals("A", decodificarEntidades("&#65;"))
        assertEquals("é", decodificarEntidades("&#xe9;"))
        assertEquals("é", decodificarEntidades("&#xE9;"))
    }

    @Test
    fun limpiarHtml_eliminaEtiquetasYSaltaDeLinea() {
        assertEquals("Hola mundo", limpiarHtml("<p>Hola <b>mundo</b></p>"))
        assertEquals("línea uno\nlínea dos", limpiarHtml("línea uno<br/>línea dos"))
    }

    @Test
    fun textoPlano_conservaParrafosYSeparaConLineaVacia() {
        assertEquals("Uno\n\nDos", textoPlano("<p>Uno</p><p>Dos</p>"))
        assertEquals("Texto simple", textoPlano("Texto simple"))
        assertEquals("", textoPlano("   "))
    }

    @Test
    fun textoPlano_textoConEnlacesExtraeElTexto() {
        val html = "<p>Consulta la <a href=\"https://sjf2.scjn.gob.mx\">tesis original</a> en línea.</p>"
        // Los fragmentos alrededor del enlace conservan la separación de palabras.
        assertEquals("Consulta la tesis original en línea.", textoPlano(html))
    }

    @Test
    fun textoPlano_noAbreHuecoAntePuntuacionTrasEnlace() {
        val html = "<p>Véase la <a href=\"#\">jurisprudencia</a>.</p>"
        assertEquals("Véase la jurisprudencia.", textoPlano(html))
    }

    @Test
    fun parrafosDeTexto_devuelveFragmentosMarcandoEnlaces() {
        val html = "<p>Ver <a href=\"#\">jurisprudencia</a> vigente</p>"
        val parrafos = parrafosDeTexto(html)
        assertEquals(1, parrafos.size)
        val fragmentos = parrafos[0]
        assertTrue(fragmentos.any { it.esEnlace && it.texto == "jurisprudencia" })
        assertTrue(fragmentos.any { !it.esEnlace && it.texto.contains("Ver") })
        assertTrue(fragmentos.any { !it.esEnlace && it.texto.contains("vigente") })
    }
}
